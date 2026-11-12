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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import com.egg.codeeditor.ui.components.EditorStatusBar
import com.egg.codeeditor.ui.components.EditorTabBar
import com.egg.codeeditor.ui.components.FileManagerActionBar
import com.egg.codeeditor.discord.DiscordRpcManager
import com.egg.codeeditor.ui.components.FindBar
import com.egg.codeeditor.ui.components.fileIconSpec
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
import com.egg.codeeditor.util.SupabaseClient
import com.egg.codeeditor.util.getFileName
import com.egg.codeeditor.util.readFileContent
import com.egg.codeeditor.util.writeFileContent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private var splashShownForProcess = false

private fun dismissEditorActionMode(editor: CodeEditor?) {
    val e = editor ?: return
    try {
        e.clearFocus()
        val c = e.cursor
        e.setSelection(c.leftLine, c.leftColumn)
    } catch (_: Exception) {
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.reloadAllTabsFromDisk()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    val editorState = rememberCodeEditorState()

    var showSplash by remember { mutableStateOf(!splashShownForProcess) }
    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(2.seconds)
            splashShownForProcess = true
            showSplash = false
        }
    }

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

    LaunchedEffect(vm.settings.themeName, editorState.editor) {
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

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            dismissEditorActionMode(editorState.editor)
        }
    }

    val anyModalOpen = vm.showSettings || vm.showAboutDialog || vm.showExitConfirm ||
            vm.showGitRemoteDialog ||
            vm.showNewFileDialog || vm.showNewFolderDialog || vm.showRenameDialog || vm.showDeleteDialog ||
            vm.pendingCloseTab != null || vm.pendingDeleteTab != null || vm.terminalVisible ||
            vm.showAppStorageDialog || vm.pendingGitAction != null

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
    EggTheme(darkTheme = com.egg.extension.api.ThemeRegistry.get(vm.settings.themeName)?.dark ?: (vm.settings.themeName == "darcula"), dynamicColor = false, pluginThemeId = vm.settings.themeName, appFontFamily = appFontFamilyResolved, appFontScale = vm.settings.appFontScale) {
        val sidebarDividerColor = MaterialTheme.colorScheme.outlineVariant
        EggSystemBarStyle(
            darkTheme = com.egg.extension.api.ThemeRegistry.get(vm.settings.themeName)?.dark ?: (vm.settings.themeName == "darcula") || vm.terminalVisible,
            customBg = if (vm.terminalVisible) 0xFF000000.toInt() else null
        )

        Box(modifier = Modifier.fillMaxSize()) {
            val hasProject = vm.rootFolderPath != null || vm.rootFolderUri != null ||
                    vm.isSupabaseMode || vm.tabs.any { it.uri != null || it.content.isNotEmpty() }
            val showWelcome = !hasProject
            if (showWelcome) {
                WelcomeScreen(
                    onOpenFolder = { openFolderLauncher.launch(null) },
                    onOpenFile = { openFileLauncher.launch(arrayOf("*/*")) },
                    onOpenGit = { vm.gitRemoteDialogTitle = "Clone Repository"; vm.showGitRemoteDialog = true },
                    onOpenAppStorage = { vm.openAppStoragePicker() },
                    recentProjects = vm.recentProjects,
                    onOpenRecent = { label, path -> vm.openPrivateRoot(label, path) },
                    onRemoveRecent = { path -> vm.removeRecent(path) },
                    versionText = appVersionText,
                    supabaseUser = vm.supabaseUser,
                    onLogin = { email, password -> vm.supabaseLogin(email, password) },
                    onSignUp = { email, password, name -> vm.supabaseSignUp(email, password, name) },
                    onLogout = { vm.supabaseLogout() }
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
                                                        "Git" -> SidebarButton(Icons.Default.AccountTree, "Git", selected = vm.currentSection == "Git") { vm.currentSection = "Git" }
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.weight(1f))
                                            SidebarButton(Icons.Default.Info, "Info", selected = vm.currentSection == "Info") { vm.currentSection = "Info" }
                                        }
                                        RailStatusBar(versionText = appVersionText)
                                    }

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
                                                (vm.currentSection == "Git" && vm.rootFolderPath != null)
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
                                                    onCloseProject = { if (vm.isSupabaseMode) vm.supabaseLogout() else vm.closeProject() }
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
                                            }
                                        }

                                        Box(Modifier.weight(1f).fillMaxWidth()) {
                                            when (vm.currentSection) {
                                                "File Manager" -> {
                                                    if (vm.isSupabaseMode) {
    FileManagerPanel(
        vm = vm,
        guidesByUri = emptyMap(),
        onOpenFile = { node ->
            if (node.isDirectory) vm.toggleExpand(node.uri.toString())
            else if (node.uri.scheme == "supabase") {
                val id = SupabaseClient.noteIdFromUri(node.uri)
                if (id != null) vm.openSupabaseNote(id)
            }
            scope.launch { drawerState.close() }
        }
    )
} else if (vm.rootFolderUri == null && !vm.rootIsPrivate) {
                                                        if (vm.tabs.any { it.uri != null }) {
                                                            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                                                                items(vm.tabs.filter { it.uri != null }, key = { it.id }) { tab ->
                                                                    androidx.compose.foundation.layout.Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clickable { vm.activeTabId = tab.id; scope.launch { drawerState.close() } }
                                                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        run {
                                                                            val fSpec = fileIconSpec(tab.name)
                                                                            Icon(fSpec.icon, null, Modifier.size(18.dp), tint = fSpec.color)
                                                                        }
                                                                        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                                                        Text(
                                                                            tab.name,
                                                                            modifier = Modifier.weight(1f),
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            Text(
                                                                "No project or file opened. Tap the menu to open a folder or file.",
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(16.dp)
                                                            )
                                                        }
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
                                                "Info" -> {
                                                    val infoMd = remember {
                                                        """
# Egg Code Editor

Android code editor built on **Sora Editor** + **TextMate grammar**.

## Features

- Syntax highlight (Kotlin, Java, JS, JSON, HTML, CSS, Python, Markdown)
- Integrated terminal (pdn-engine)
- Git integration (JGit)
- Markdown preview with custom syntax
- Discord Rich Presence

## Shortcuts

- `Ctrl+S` — Save
- `Ctrl+F` — Find
- `Ctrl+Z` — Undo
- `Ctrl+Y` — Redo

## License

Apache-2.0. Third-party: Sora Editor (LGPL-2.1), JGit (EPL-2.0).
                                                """.trimIndent()
                                                    }
                                                    com.egg.codeeditor.ui.components.MarkdownPreview(
                                                        markdown = infoMd,
                                                        editorSettings = vm.settings,
                                                        editorRef = editorState.editor,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
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
                                            activeFileName = vm.activeTab?.name
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        Scaffold(
                            topBar = {
                                if (vm.headerVisible) TopAppBar(
                                    title = {},
                                    modifier = Modifier.height(40.dp),
                                    navigationIcon = {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(Icons.Default.Menu, "Open drawer")
                                        }
                                    },
                                    actions = {
                                        if (vm.activeTab?.name?.endsWith(".md") == true) {
                                            IconButton(onClick = {
                                    val i = vm.activeTabIndex()
                                    if (i >= 0 && vm.tabs[i].diffNew == null) {
                                        val text = editorState.editor?.text?.toString() ?: ""
                                        if (vm.tabs[i].content != text) vm.tabs[i] = vm.tabs[i].copy(content = text)
                                    }
                                    vm.showMarkdownPreview = !vm.showMarkdownPreview
                                }) {
                                                Icon(if (vm.showMarkdownPreview) Icons.Default.Edit else Icons.Default.Visibility, "Toggle Preview")
                                            }
                                        }
                                        IconButton(onClick = { if (vm.activeTabId != null) vm.showFindBar = true }) {
                                            Icon(Icons.Default.Search, "Search")
                                        }
                                        IconButton(
                                            onClick = { try { editorState.editor?.undo() } catch (_: Exception) {} },
                                            enabled = canUndo,
                                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                        ) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                                        IconButton(
                                            onClick = { try { editorState.editor?.redo() } catch (_: Exception) {} },
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
                                        settings = vm.settings,
                                        headerVisible = vm.headerVisible,
                                        onToggleHeader = { vm.headerVisible = !vm.headerVisible }
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
                                        editorSettings = vm.settings,
                                        editorRef = editorState.editor,
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

                    if (vm.terminalVisible) {
                        TerminalPanel(vm = vm, onRequestEditorFocus = { editorState.editor?.requestFocus() })
                    }

                }
            }

            EggDialogs(vm = vm, onExit = { (context as? Activity)?.finish() })

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

            if (showSplash) {
                SplashOverlay()
            }
        }
    }
}