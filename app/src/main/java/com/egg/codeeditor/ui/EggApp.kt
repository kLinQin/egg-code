package com.egg.codeeditor.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.egg.codeeditor.model.EditorTab
import com.egg.codeeditor.model.rememberCodeEditorState
import com.egg.codeeditor.util.isErrorMessage
import com.egg.codeeditor.ui.WelcomeScreen
import com.egg.codeeditor.ui.cloud.CloudPanel
import com.egg.codeeditor.ui.components.CloudActionBar
import com.egg.codeeditor.ui.components.EditorStatusBar
import com.egg.codeeditor.ui.components.EditorTabBar
import com.egg.codeeditor.ui.components.FileManagerActionBar
import com.egg.codeeditor.discord.DiscordRpcManager
import com.egg.codeeditor.ui.components.FindBar
import com.egg.codeeditor.ui.components.GitActionBar
import com.egg.codeeditor.ui.components.RailStatusBar
import com.egg.codeeditor.ui.components.SectionStatusBar
import com.egg.codeeditor.ui.components.SidebarButton
import com.egg.codeeditor.ui.components.SplashOverlay
import com.egg.codeeditor.ui.editor.SoraCodeEditor
import com.egg.codeeditor.ui.editor.applyColorScheme
import com.egg.codeeditor.ui.git.GitPanel
import com.egg.codeeditor.ui.terminal.ACTION_CLOSE_TERMINAL
import com.egg.codeeditor.ui.terminal.cancelTerminalNotification
import com.egg.codeeditor.ui.terminal.postTerminalNotification
import com.egg.codeeditor.ui.theme.EggSystemBarStyle
import com.egg.codeeditor.ui.theme.EggTheme
import com.egg.codeeditor.util.getFileName
import com.egg.codeeditor.util.readFileContent
import com.egg.codeeditor.util.writeFileContent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** True once the splash has been shown for this process (cold start only). */
private var splashShownForProcess = false

/** Tutup popup teks Sora (Cut/Copy/Paste) dengan aman.
 *  Runtuhkan seleksi ke satu titik kursor (lewat setSelection 2 argumen)
 *  sehingga popup aksi teks ikut hilang. */
private fun dismissEditorActionMode(editor: CodeEditor?) {
    val e = editor ?: return
    try {
        e.clearFocus()
        val c = e.cursor
        e.setSelection(c.leftLine, c.leftColumn)
    } catch (_: Exception) {
        // abaikan — hanya upaya menutup popup teks
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EggApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = remember { EggViewModel(context.applicationContext) }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }

    LaunchedEffect(scope) {
        vm.setDiscordScope(scope)
    }

    LaunchedEffect(vm.activeTabId, vm.activeTab?.name) {
        val name = vm.activeTab?.name
        if (name != null) {
            DiscordRpcManager.updatePresence("Editing ${name}", "Egg Code Editor")
        } else {
            DiscordRpcManager.updatePresence("Idle", "Egg Code Editor")
        }
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // ── Editor state (WAJIB sebelum launcher supaya tidak unresolved reference) ──
    val editorState = rememberCodeEditorState()

    // ── Splash (cold start) ──
    var showSplash by remember { mutableStateOf(!splashShownForProcess) }
    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(2.seconds)
            splashShownForProcess = true
            showSplash = false
        }
    }

    // ── Terminal notification: permission + receiver "Close Session" ──
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* permission result does not need processing */ }

    val closeTerminalReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_CLOSE_TERMINAL) {
                    vm.terminalVisible = false
                }
            }
        }
    }
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_CLOSE_TERMINAL)
        ContextCompat.registerReceiver(context, closeTerminalReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(closeTerminalReceiver) }
        }
    }

    LaunchedEffect(vm.terminalVisible, vm.rootFolderPath) {
        if (vm.terminalVisible) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            postTerminalNotification(context, vm.rootFolderPath)
        } else {
            cancelTerminalNotification(context)
        }
    }

    // ── Perhitungan garis indent per node ──
    val guidesByUri = remember(vm.visibleNodes) {
        val laterHasBelow = mutableMapOf<Int, Boolean>()
        val result = mutableMapOf<String, List<Boolean>>()
        for (i in vm.visibleNodes.indices.reversed()) {
            val node = vm.visibleNodes[i]
            result[node.uri.toString()] = (0 until node.depth).map { l -> laterHasBelow[l] == true }
            for (l in 0 until node.depth) laterHasBelow[l] = true
        }
        result
    }

    // ── Launchers (Activity Result API) ──
    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { vm.openFolder(it) }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.openSingleFile(it) }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val i = vm.activeTabIndex()
                if (i >= 0) {
                    val tab = vm.tabs[i]
                    val text = editorState.editor?.text?.toString() ?: ""
                    writeFileContent(context, it, text)
                    vm.scheduleGitRefresh()
                    vm.tabs[i] = tab.copy(uri = it, name = getFileName(context, it), content = text, isDirty = false)
                }
                vm.pendingCloseAfterSaveId?.let { closeId ->
                    if (vm.tabs.any { it.id == closeId }) vm.reallyCloseTab(closeId)
                    vm.pendingCloseAfterSaveId = null
                    vm.pendingSaveAsTabId = null
                }
            }
        }
    }

    LaunchedEffect(vm.pendingSaveAsTabId) {
        if (vm.pendingSaveAsTabId != null) {
            val t = vm.tabs.firstOrNull { it.id == vm.pendingSaveAsTabId }
            if (t != null) saveFileLauncher.launch("${t.name}.txt")
        }
    }

    // ── Undo/redo polling ──
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    LaunchedEffect(editorState.editor) {
        val editor = editorState.editor ?: return@LaunchedEffect
        while (true) {
            canUndo = try { editor.canUndo() } catch (_: Exception) { false }
            canRedo = try { editor.canRedo() } catch (_: Exception) { false }
            delay(150.milliseconds)
        }
    }

    LaunchedEffect(Unit) {
        editorState.editor?.let { applyColorScheme(it, vm.settings.themeName) }
        if (vm.tabs.isEmpty()) {
            val t = EditorTab(vm.nextTabId++, "Untitled 1", null, "")
            vm.tabs.add(t)
            vm.activeTabId = t.id
        }
    }

    fun captureActiveTabContent() {
        val i = vm.activeTabIndex()
        if (i >= 0 && vm.tabs[i].diffNew == null) {
            val text = editorState.editor?.text?.toString() ?: ""
            if (vm.tabs[i].content != text) vm.tabs[i] = vm.tabs[i].copy(content = text)
        }
    }

    // ── Back handling ──
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen) {
        when {
            vm.showSettings -> vm.showSettings = false
            vm.showAboutDialog -> vm.showAboutDialog = false
            vm.showExitConfirm -> vm.showExitConfirm = false
            vm.terminalVisible -> {
                vm.terminalVisible = false
                editorState.editor?.requestFocus()
            }
            vm.tabs.size > 1 -> {
                val activeId = vm.activeTabId
                if (activeId != null) vm.requestCloseTab(vm.tabs.first { it.id == activeId })
            }
            vm.currentSection != "File Manager" -> vm.currentSection = "File Manager"
            vm.settings.confirmExit -> vm.showExitConfirm = true
            else -> (context as? Activity)?.finish()
        }
    }

    // Tutup popup editor (copy/paste) saat drawer terbuka.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            dismissEditorActionMode(editorState.editor)
        }
    }

    // Tutup popup editor saat dialog/modal apa pun terbuka (Settings, About,
    // Git, Cloud, Rename, Delete, konfirmasi keluar, Terminal, dll.).
    val anyModalOpen = vm.showSettings || vm.showAboutDialog || vm.showExitConfirm ||
            vm.showGitRemoteDialog || vm.showCloudConnectDialog ||
            vm.showNewFileDialog || vm.showNewFolderDialog || vm.showRenameDialog || vm.showDeleteDialog ||
            vm.pendingCloseTab != null || vm.pendingDeleteTab != null || vm.terminalVisible ||
            vm.pendingCloudAction != null || vm.showAppStorageDialog || vm.pendingGitAction != null

    LaunchedEffect(anyModalOpen) {
        if (anyModalOpen) {
            dismissEditorActionMode(editorState.editor)
        }
    }

    val appVersionText = remember {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        "v" + (info?.versionName ?: "?")
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.uiMessages.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    // ── Theme global ──
    val appFontFamilyResolved: androidx.compose.ui.text.font.FontFamily? = remember(vm.settings.appFontFamily, vm.settings.appCustomFontPath) {
        when (vm.settings.appFontFamily) {
            "sans" -> androidx.compose.ui.text.font.FontFamily.SansSerif
            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "custom" -> {
                val path = vm.settings.appCustomFontPath
                if (path != null) runCatching {
                    androidx.compose.ui.text.font.FontFamily(
                        androidx.compose.ui.text.font.Font(java.io.File(path))
                    )
                }.getOrDefault(androidx.compose.ui.text.font.FontFamily.SansSerif)
                else androidx.compose.ui.text.font.FontFamily.SansSerif
            }
            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
        }
    }

    EggTheme(darkTheme = vm.settings.themeName == "darcula", dynamicColor = false, appFontFamily = appFontFamilyResolved, appFontScale = vm.settings.appFontScale) {
        val sidebarDividerColor = MaterialTheme.colorScheme.outlineVariant
        EggSystemBarStyle(
            darkTheme = vm.settings.themeName == "darcula" || vm.terminalVisible,
            customBg = if (vm.terminalVisible) 0xFF000000.toInt() else null
        )

        Box(modifier = Modifier.fillMaxSize()) {
        val hasProject = vm.rootFolderPath != null || vm.rootFolderUri != null ||
            vm.tabs.any { it.uri != null || it.content.isNotEmpty() }
        val showWelcome = !hasProject
        if (showWelcome) {
            WelcomeScreen(
                onOpenFolder = { openFolderLauncher.launch(null) },
                onOpenFile = { openFileLauncher.launch(arrayOf("*/*")) },
                onOpenGit = { vm.gitRemoteDialogTitle = "Clone Repository"; vm.showGitRemoteDialog = true },
                onOpenB2 = { vm.openB2FromWelcome() },
                onOpenAppStorage = { vm.openAppStoragePicker() },
                recentProjects = vm.recentProjects,
                onOpenRecent = { label, path -> vm.openPrivateRoot(label, path) },
                onRemoveRecent = { path -> vm.removeRecent(path) },
                versionText = appVersionText
            )
        } else {
        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.fillMaxWidth(),
                        drawerShape = RectangleShape,
                        drawerContainerColor = MaterialTheme.colorScheme.background
                    ) {
                        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                            // ── SIDEBAR RAIL (lebar 56dp) ──
                            Column(
                                modifier = Modifier.width(56.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "EGG",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider()
                                Column(
                                    modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.background)
                                        .drawBehind {
                                            val stroke = 1.dp.toPx()
                                            drawLine(
                                                color = sidebarDividerColor,
                                                start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                                strokeWidth = stroke
                                            )
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        vm.availableSections.forEach { sec ->
                                            when (sec) {
                                                "File Manager" -> SidebarButton(Icons.Default.Folder, "File", selected = vm.currentSection == "File Manager") { vm.currentSection = "File Manager" }
                                                "Cloud Storage" -> SidebarButton(Icons.Default.Cloud, "Cloud", selected = vm.currentSection == "Cloud Storage") { vm.currentSection = "Cloud Storage" }
                                                "Git" -> SidebarButton(Icons.Default.AccountTree, "Git", selected = vm.currentSection == "Git") { vm.currentSection = "Git" }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    SidebarButton(Icons.Default.Info, "Info", onClick = { vm.showAboutDialog = true })
                                }
                                RailStatusBar(versionText = appVersionText)
                            }

                            // ── KONTEN PANEL ──
                            Column(Modifier.fillMaxSize().weight(1f)) {
                                Surface(color = MaterialTheme.colorScheme.surface) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(40.dp).padding(start = 16.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            vm.currentSection,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                                            Icon(Icons.Default.Close, "Close Drawer")
                                        }
                                    }
                                }
                                HorizontalDivider()

                                val hasActionBar = vm.currentSection == "File Manager" ||
                                        (vm.currentSection == "Git" && vm.rootFolderPath != null) ||
                                        (vm.currentSection == "Cloud Storage" && vm.rootFolderPath != null)
                                if (hasActionBar) {
                                    when (vm.currentSection) {
                                        "File Manager" -> FileManagerActionBar(
                                            rootFolderUri = vm.rootFolderUri,
                                            rootFolderPath = vm.rootFolderPath,
                                            expandedPaths = vm.expandedPaths,
                                            onNewFile = { vm.targetParentNode = null; vm.showNewFileDialog = true },
                                            onNewFolder = { vm.targetParentNode = null; vm.showNewFolderDialog = true },
                                            onRefresh = { vm.refreshFolder() },
                                            onToggleCollapse = { vm.collapseAllOrExpandRoots() },
                                            onCloseProject = { vm.closeProject() }
                                        )
                                        "Git" -> GitActionBar(
                                            gitRepo = vm.gitRepo,
                                            gitBusy = vm.gitBusy,
                                            commitMessage = vm.commitMessage,
                                            remoteUrl = vm.gitRemoteUrl,
                                            onInit = { vm.gitInit() },
                                            onClone = { vm.showGitRemoteDialog = true; vm.gitRemoteDialogTitle = "Clone Repository" },
                                            onRefresh = { vm.gitRefresh() },
                                            onAddRemote = { vm.showGitRemoteDialog = true; vm.gitRemoteDialogTitle = "Add Remote" },
                                            onFetch = { vm.gitFetch() },
                                            onPull = { vm.gitPull() },
                                            onPush = { vm.gitPush() },
                                            onCommit = { vm.gitCommit() },
                                            changeCount = ((vm.gitStatus?.staged?.size ?: 0) + (vm.gitStatus?.unstaged?.size ?: 0))
                                        )
                                        "Cloud Storage" -> CloudActionBar(
                                            connected = vm.b2Account != null,
                                            cloudBusy = vm.cloudBusy,
                                            onConnect = { vm.showCloudConnectDialog = true },
                                            onRefresh = { vm.cloudRefresh() },
                                            onFetch = { vm.cloudFetch() },
                                            onPush = { vm.cloudPush() },
                                            onPull = { vm.cloudPull() }
                                        )
                                    }
                                }

                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    when (vm.currentSection) {
                                        "File Manager" -> {
                                            if (vm.rootFolderUri == null && !vm.rootIsPrivate) {
                                                Text(
                                                    "No folder opened. Choose 'Open Project Folder' above.",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            } else {
                                                FileManagerPanel(
                                                    vm = vm,
                                                    guidesByUri = guidesByUri,
                                                    onOpenFile = { node ->
                                                        scope.launch {
                                                            val existing = vm.tabs.indexOfFirst { it.uri == node.uri }
                                                            if (existing >= 0) {
                                                                captureActiveTabContent()
                                                                vm.activeTabId = vm.tabs[existing].id
                                                            } else {
                                                                val content = readFileContent(context, node.uri)
                                                                captureActiveTabContent()
                                                                val tab = EditorTab(vm.nextTabId++, node.name, node.uri, content)
                                                                vm.tabs.add(tab)
                                                                vm.activeTabId = tab.id
                                                                vm.persistTabsPublic()
                                                            }
                                                            drawerState.close()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        "Git" -> {
                                            if (vm.rootFolderPath == null) {
                                                Text("Open a project folder first in File Manager.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                GitPanel(
                                                    gitRepo = vm.gitRepo,
                                                    gitStatus = vm.gitStatus,
                                                    gitCommits = vm.gitCommits,
                                                    commitMessage = vm.commitMessage,
                                                    gitBusy = vm.gitBusy,
                                                    gitMessage = vm.gitMessage,
                                                    onCommitMessageChange = { vm.commitMessage = it },
                                                    onStage = { vm.gitStage(it) },
                                                    onUnstage = { vm.gitUnstage(it) },
                                                    onDiscard = { rel, untracked -> vm.gitRequestDiscard(rel, untracked) },
                                                    onDiscardAll = { vm.gitRequestDiscardAll() },
                                                    onOpenDiff = { rel -> vm.openGitDiff(rel); scope.launch { drawerState.close() } }
                                                )
                                            }
                                        }
                                        "Cloud Storage" -> {
                                            if (vm.rootFolderPath == null) {
                                                Text("Open a project folder first in File Manager.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                CloudPanel(
                                                    connected = vm.b2Account != null,
                                                    cloudChanges = vm.cloudChanges,
                                                    cloudHistory = vm.cloudHistory,
                                                    cloudBusy = vm.cloudBusy,
                                                    cloudMessage = vm.cloudMessage,
                                                    onPushFile = { relPath -> vm.cloudPushFile(relPath) },
                                                    onDiscardFile = { relPath, untracked -> vm.cloudRequestDiscard(relPath, untracked) },
                                                    onDeleteRemote = { relPath -> vm.cloudRequestDeleteRemote(relPath) },
                                                    onDiscardAll = { vm.cloudRequestDiscardAll() }
                                                )
                                            }
                                        }
                                        else -> Text("(${vm.currentSection} feature coming soon)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                SectionStatusBar(
                                    section = vm.currentSection,
                                    rootFolderPath = vm.rootFolderPath,
                                    visibleNodes = vm.visibleNodes,
                                    gitRepo = vm.gitRepo,
                                    gitStatus = vm.gitStatus,
                                    cloudBucketName = vm.b2Bucket?.bucketName
                                )
                            }
                        }
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {},
                            modifier = Modifier.height(40.dp),
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, "Open drawer")
                                }
                            },
                            actions = {
                                if (vm.activeTab?.name?.endsWith(".md") == true) {
                                    IconButton(onClick = { vm.showMarkdownPreview = !vm.showMarkdownPreview }) {
                                        Icon(if (vm.showMarkdownPreview) Icons.Default.Edit else Icons.Default.Visibility, "Toggle Preview")
                                    }
                                }
                                IconButton(onClick = { if (vm.activeTabId != null) vm.showFindBar = true }) {
                                    Icon(Icons.Default.Search, "Search")
                                }
                                IconButton(
                                    onClick = { try { editorState.editor?.undo() } catch (e: Exception) { e.printStackTrace() } },
                                    enabled = canUndo,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                                IconButton(
                                    onClick = { try { editorState.editor?.redo() } catch (e: Exception) { e.printStackTrace() } },
                                    enabled = canRedo,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val i = vm.activeTabIndex()
                                            if (i >= 0) {
                                                val tab = vm.tabs[i]
                                                if (tab.uri != null) {
                                                    val text = editorState.editor?.text?.toString() ?: ""
                                                    writeFileContent(context, tab.uri, text)
                                                    vm.scheduleGitRefresh()
                                                    vm.tabs[i] = vm.tabs[i].copy(content = text, isDirty = false)
                                                } else {
                                                    saveFileLauncher.launch("${tab.name}.txt")
                                                }
                                            }
                                        }
                                    },
                                    enabled = vm.activeTab?.isDirty == true,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) { Icon(Icons.Default.Save, "Save") }
                                IconButton(onClick = { vm.terminalVisible = true }) { Icon(Icons.Default.Terminal, "Terminal") }
                                IconButton(onClick = { vm.showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                            }
                        )
                    },
                    bottomBar = {
                        if (vm.settings.showBottomBar) {
                            EditorStatusBar(
                                editor = editorState.editor,
                                fileName = vm.activeTab?.name ?: "",
                                settings = vm.settings
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(Modifier.padding(innerPadding).fillMaxSize().imePadding()) {
                        EditorTabBar(
                            tabs = vm.tabs,
                            activeTabId = vm.activeTabId,
                            onSelect = { id -> captureActiveTabContent(); vm.activeTabId = id; vm.showMarkdownPreview = false },
                            onClose = { tab -> vm.requestCloseTab(tab) },
                            onSave = { tab ->
                                if (tab.diffNew == null) scope.launch {
                                    val i = vm.tabs.indexOfFirst { it.id == tab.id }
                                    if (i >= 0) {
                                        val t = vm.tabs[i]
                                        if (t.uri != null) {
                                            writeFileContent(context, t.uri, t.content)
                                            vm.scheduleGitRefresh()
                                            vm.tabs[i] = vm.tabs[i].copy(isDirty = false)
                                        } else {
                                            vm.activeTabId = t.id
                                            saveFileLauncher.launch("${t.name}.txt")
                                        }
                                    }
                                }
                            },
                            onCloseOthers = { id -> captureActiveTabContent(); vm.closeOthers(id) },
                            onDelete = { tab -> vm.pendingDeleteTab = tab },
                            onNewTab = { captureActiveTabContent(); vm.newUntitledTab() },
                            gitMarkerFor = { tab -> vm.gitMarkerForTab(tab.uri) }
                        )

                        if (vm.showFindBar) {
                            FindBar(editor = editorState.editor, onDismiss = { vm.showFindBar = false })
                        }

                        if (vm.activeTab?.diffNew != null) {
                            com.egg.codeeditor.ui.diff.SoraDiffView(
                                relPath = vm.activeTab!!.name,
                                oldText = vm.activeTab!!.diffOld ?: "",
                                newText = vm.activeTab!!.diffNew ?: "",
                                settings = vm.settings,
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp)
                            )
                        } else if (vm.showMarkdownPreview && vm.activeTab?.name?.endsWith(".md") == true) {
                            com.egg.codeeditor.ui.components.MarkdownPreview(
                                markdown = vm.activeTab?.content ?: "",
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp)
                            )
                        } else SoraCodeEditor(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                            state = editorState,
                            fileName = vm.activeTab?.name ?: "Untitled",
                            content = vm.activeTab?.content ?: "",
                            settings = vm.settings,
                            onTextChange = {
                                val i = vm.activeTabIndex()
                                if (i >= 0 && !vm.tabs[i].isDirty) {
                                    vm.tabs[i] = vm.tabs[i].copy(isDirty = true)
                                }
                            }
                        )
                    }
                }
            }

            // ── Terminal ──
            if (vm.terminalVisible) {
                TerminalPanel(vm = vm, onRequestEditorFocus = { editorState.editor?.requestFocus() })
            }

        }
        }

        // ── Dialog & konfirmasi (di luar cabang welcome/UI normal) ──
        EggDialogs(vm = vm, onExit = { (context as? Activity)?.finish() })

        // ── Snackbar ──
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { data ->
            val err = isErrorMessage(data.visuals.message)
            Snackbar(
                snackbarData = data,
                containerColor = if (err) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (err) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                actionColor = if (err) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
                shape = RectangleShape
            )
        }

        // ── Splash overlay (di atas welcome & UI normal) ──
        if (showSplash) {
            SplashOverlay()
        }
        }
    }
}
