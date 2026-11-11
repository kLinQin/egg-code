package com.egg.codeeditor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import id.or.oo.pr.engine.PdnTerminalSession
import id.or.oo.pr.engine.PdnTerminal
import id.or.oo.pr.engine.PdnRuntime
import id.or.oo.pr.engine.PdnOperations
import id.or.oo.pr.engine.PdnConfiguration
import id.or.oo.pr.engine.PdnBind
import id.or.oo.pr.engine.PdnListener
import id.or.oo.pr.engine.PdnEvent
import id.or.oo.pr.engine.PdnTerminalListener
import id.or.oo.pr.engine.ProotHost
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.egg.codeeditor.model.EditorSettings
import com.egg.codeeditor.model.EditorTab
import com.egg.codeeditor.model.FileNode
import com.egg.codeeditor.model.GitStatusInfo
import com.egg.codeeditor.discord.DiscordRpcManager
import com.egg.codeeditor.util.B2Account
import com.egg.codeeditor.util.B2Bucket
import com.egg.codeeditor.util.B2FileInfo
import com.egg.codeeditor.util.CloudChange
import com.egg.codeeditor.util.CloudIndex
import com.egg.codeeditor.util.GitCommitInfo
import com.egg.codeeditor.util.b2Authorize
import com.egg.codeeditor.util.b2DeleteFileVersion
import com.egg.codeeditor.util.b2DownloadBytes
import com.egg.codeeditor.util.b2FindBucket
import com.egg.codeeditor.util.b2ListFileVersions
import com.egg.codeeditor.util.b2ListFiles
import com.egg.codeeditor.util.b2UploadFile
import com.egg.codeeditor.util.computeCloudChanges
import com.egg.codeeditor.util.findOrCreateDocumentFile
import com.egg.codeeditor.util.getFileName
import com.egg.codeeditor.util.getGitRemoteUrl
import com.egg.codeeditor.util.getPathFromTreeUri
import com.egg.codeeditor.util.loadSettings
import com.egg.codeeditor.util.readFileContent
import com.egg.codeeditor.util.readGitLog
import com.egg.codeeditor.util.saveLastProjectUri
import com.egg.codeeditor.util.saveLastPrivateRoot
import com.egg.codeeditor.util.loadLastPrivateRoot
import com.egg.codeeditor.util.saveRecentProjects
import com.egg.codeeditor.util.saveOpenTabs
import com.egg.codeeditor.util.loadOpenTabs
import com.egg.codeeditor.util.clearOpenTabs
import com.egg.codeeditor.util.saveB2Credentials
import com.egg.codeeditor.util.saveGitCredentials
import com.egg.codeeditor.util.loadGitCredentials
import com.egg.codeeditor.util.loadB2Credentials
import com.egg.codeeditor.util.loadRecentProjects
import com.egg.codeeditor.util.clearLastPrivateRoot
import com.egg.codeeditor.util.loadLastProjectUri
import com.egg.codeeditor.util.clearLastProjectUri
import com.egg.codeeditor.util.readGitStatus
import com.egg.codeeditor.util.saveSettings
import com.egg.codeeditor.util.writeFileContent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONArray
import org.json.JSONObject

class EggViewModel(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var gitRefreshJob: Job? = null

    fun dispose() {
        runCatching { scope.cancel() }
        settingsSaveJob?.cancel()
        gitRefreshJob?.cancel()
    }

    private fun restoreLastProject() {
        val privateRoot = loadLastPrivateRoot(appContext)
        if (privateRoot != null) {
            val dir = File(privateRoot)
            if (dir.isDirectory) {
                rootFolderUri = null
                rootFolderPath = privateRoot
                rootIsPrivate = true
                gitRepo = runCatching { Git.open(dir) }.getOrNull()
                availableSections = if (privateRoot.contains("/b2/"))
                    listOf("Cloud Storage", "File Manager")
                else
                    listOf("Git", "File Manager")
                scope.launch { rebuildVisibleNodes() }
                if (gitRepo != null) scheduleGitRefresh()
                return
            } else {
                clearLastPrivateRoot(appContext)
            }
        }
        val saved = loadLastProjectUri(appContext) ?: return
        val uri = runCatching { Uri.parse(saved) }.getOrNull() ?: return
        val hasPerm = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPerm) {
            clearLastProjectUri(appContext)
            return
        }
        rootFolderUri = uri
        rootFolderPath = getPathFromTreeUri(uri)
        availableSections = buildAvailableSections(rootFolderPath)
        if (rootFolderPath != null && gitRepo == null) {
            gitRepo = runCatching { Git.open(File(rootFolderPath!!)) }.getOrNull()
        }
        scope.launch { rebuildVisibleNodes() }
        if (gitRepo != null) scheduleGitRefresh()
    }

    private fun buildAvailableSections(rootPath: String?): List<String> {
        if (rootPath == null) return listOf("File Manager")
        val hasGit = runCatching { org.eclipse.jgit.api.Git.open(File(rootPath)).use { true } }.getOrDefault(false)
        val hasCloud = loadB2Credentials(appContext) != null // sesuaikan fungsi kredensial yang ada
        val list = mutableListOf("File Manager")
        if (hasCloud) list.add("Cloud Storage")
        if (hasGit) list.add("Git")
        return list
    }

    fun reloadAllTabsFromDisk() {
        scope.launch {
            val toReload = mutableListOf<Int>()
            for (i in tabs.indices) {
                val t = tabs[i]
                val uri = t.uri ?: continue
                if (t.isDirty) continue
                val disk = runCatching { readFileContent(appContext, uri) }.getOrNull() ?: continue
                if (disk != t.content) toReload.add(i)
            }
            for (i in toReload) {
                val t = tabs[i]
                val disk = runCatching { readFileContent(appContext, t.uri!!) }.getOrNull() ?: continue
                tabs[i] = t.copy(content = disk)
            }
        }
    }

    var terminalVisible by mutableStateOf(false)

    var headerVisible by mutableStateOf(true)
    var terminalEmulator by mutableStateOf<TerminalEmulator?>(null)
        private set
    private var terminalSession: PdnTerminalSession? = null
    private var terminalReaderJob: kotlinx.coroutines.Job? = null
    @Volatile private var terminalStarting = false

    fun writeTerminal(data: ByteArray) {
        val s = terminalSession ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { s.write(data) }
        }
    }

    var terminalBg by mutableStateOf(androidx.compose.ui.graphics.Color(0xFF141413))
        private set
    var terminalFg by mutableStateOf(androidx.compose.ui.graphics.Color(0xFFE8E6DC))
        private set

    fun setTerminalColors(bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
        if (bg == terminalBg && fg == terminalFg) return
        terminalBg = bg
        terminalFg = fg
        val emu = terminalEmulator ?: return
        val surface = blend(bg, fg, 0.08f)
        val brightBlack = blend(bg, fg, 0.18f)
        val palette = intArrayOf(
            surface.toArgb(),
            0xFFD97066.toInt(),
            0xFF8BA173.toInt(),
            0xFFD9A05B.toInt(),
            0xFF6A9BCC.toInt(),
            0xFFB08AB0.toInt(),
            0xFF79B8B3.toInt(),
            fg.toArgb(),
            brightBlack.toArgb(),
            0xFFE08A7D.toInt(),
            0xFFAAB892.toInt(),
            0xFFE8BD82.toInt(),
            0xFF8CB4DF.toInt(),
            0xFFC9A6C9.toInt(),
            0xFF97CCC7.toInt(),
            fg.toArgb()
        )
        emu.applyColorScheme(palette, fg.toArgb(), bg.toArgb())
    }

    private fun blend(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color, t: Float) = androidx.compose.ui.graphics.Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f
    )

    fun resizeTerminal(rows: Int, cols: Int) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { terminalSession?.resize(rows, cols) }
        }
    }

    fun ensureTerminalStarted() {
        if (terminalSession != null || terminalStarting) return
        terminalStarting = true
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val ctx = appContext
                val host = object : ProotHost {
                    override val nativeLibDir: File get() = File(ctx.applicationInfo.nativeLibraryDir)
                    override val prefixDir: File get() = File(ctx.filesDir, "pdn")
                    override val homeDir: File get() = File(ctx.filesDir, "home")
                    override val packageName: String get() = ctx.packageName
                    override val cacheDir: File get() = ctx.cacheDir
                }
                val runtime = PdnRuntime(host)
                val ops = PdnOperations(runtime)
                val term = PdnTerminal(runtime)

                val emu = TerminalEmulatorFactory.create(
                    defaultBackground = terminalBg,
                    defaultForeground = terminalFg,
                    onKeyboardInput = { data -> writeTerminal(data) },
                    onResize = { dims -> resizeTerminal(dims.rows, dims.columns) }
                )
                terminalEmulator = emu

                fun printToScreen(msg: String) {
                    val b = msg.toByteArray(Charsets.UTF_8)
                    emu.writeInput(b, 0, b.size)
                }

                val alpineDir = File(ctx.filesDir, "pdn/var/lib/pdn/rootfs/alpine")
                if (!alpineDir.exists()) {
                    printToScreen("Installing Alpine (automatic)...\r\n")
                    val listener = object : PdnListener { override fun onEvent(event: PdnEvent) {} }
                    runCatching { ops.run(runtime.install("alpine"), listener) }
                        .onFailure { printToScreen("Install failed: ${it.message}\r\n") }
                }

                printToScreen("Starting Alpine shell...\r\n")
                val cfg = PdnConfiguration(
                    "root", "/workspace",
                    listOf(
                        PdnBind(File("/storage/emulated/0"), "/mnt/sdcard"),
                        PdnBind(ctx.filesDir, "/mnt/appdata")
                    ),
                    emptyMap()
                )
                val loginPb = runtime.login("alpine", cfg)
                val listener = object : PdnTerminalListener {}
                val session = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    term.start(loginPb, 80, 24, listener)
                }
                terminalSession = session
                runCatching { session.resize(emu.dimensions.rows, emu.dimensions.columns) }

                terminalReaderJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val buf = ByteArray(4096)
                    try {
                        while (!session.isClosed) {
                            val n = session.read(buf)
                            if (n > 0) emu.writeInput(buf, 0, n) else if (n < 0) break
                        }
                    } catch (e: Exception) {
                        val msg = "\r\n[Terminal closed: ${e.message}]\r\n".toByteArray(Charsets.UTF_8)
                        emu.writeInput(msg, 0, msg.size)
                    }
                }
            } catch (e: Exception) {
                val emu = terminalEmulator
                val msg = "Failed to start shell: ${e.message}\r\n".toByteArray(Charsets.UTF_8)
                emu?.writeInput(msg, 0, msg.size)
            } finally {
                terminalStarting = false
            }
        }
    }

    fun closeTerminal() {
        runCatching { terminalReaderJob?.cancel() }
        terminalReaderJob = null
        runCatching { terminalSession?.close() }
        terminalSession = null
        terminalEmulator = null
    }

    var currentSection by mutableStateOf("File Manager")

    var rootFolderUri by mutableStateOf<Uri?>(null)
    var rootFolderPath by mutableStateOf<String?>(null)
    var rootIsPrivate by mutableStateOf(false)
    var availableSections by mutableStateOf(listOf("File Manager"))
    var visibleNodes by mutableStateOf<List<FileNode>>(emptyList())
    var expandedPaths by mutableStateOf<Set<String>>(emptySet())
    val folderCache = mutableStateMapOf<String, List<FileNode>>()

    var showNewFileDialog by mutableStateOf(false)
    var showNewFolderDialog by mutableStateOf(false)
    var showRenameDialog by mutableStateOf(false)
    var showDeleteDialog by mutableStateOf(false)
    var selectedNode by mutableStateOf<FileNode?>(null)
    var targetParentNode by mutableStateOf<FileNode?>(null)

    var gitRepo by mutableStateOf<Git?>(null)
    var gitStatus by mutableStateOf<GitStatusInfo?>(null)
    var commitMessage by mutableStateOf("")
    var gitBusy by mutableStateOf(false)
    var gitMessage by mutableStateOf<String?>(null)
    var gitCommits by mutableStateOf<List<GitCommitInfo>>(emptyList())
    var gitRemoteUrl by mutableStateOf<String?>(null)
    var gitUser by mutableStateOf("")
    var gitToken by mutableStateOf("")
    var showGitRemoteDialog by mutableStateOf(false)
    var gitRemoteDialogTitle by mutableStateOf("Clone Repository")

    var b2Account by mutableStateOf<B2Account?>(null)
    var b2Bucket by mutableStateOf<B2Bucket?>(null)
    var cloudChanges by mutableStateOf<List<CloudChange>>(emptyList())
    var cloudHistory by mutableStateOf<List<B2FileInfo>>(emptyList())
    var cloudBusy by mutableStateOf(false)
    var cloudMessage by mutableStateOf<String?>(null)
    var showCloudConnectDialog by mutableStateOf(false)
    private var welcomeB2Mode by mutableStateOf(false)
    private var pullAfterConnect = false
    var showAppStorageDialog by mutableStateOf(false)
    var appStorageItems by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var recentProjects by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var cloudIndex: CloudIndex? = null
    private var cloudIndexRoot: String? = null

    private fun cloudIndexOf(): CloudIndex {
        val root = rootFolderPath ?: return CloudIndex(File("/")).also { it.load() }
        val existing = cloudIndex
        if (existing != null && cloudIndexRoot == root) return existing
        val dir = if (rootIsPrivate) File(appContext.filesDir, "index") else null
        val idx = CloudIndex(File(root), dir).also { it.load() }
        cloudIndex = idx; cloudIndexRoot = root
        return idx
    }

    var settings by mutableStateOf(loadSettings(appContext))
    var showSettings by mutableStateOf(false)
    var showAboutDialog by mutableStateOf(false)
    var showExitConfirm by mutableStateOf(false)

    val tabs = mutableStateListOf<EditorTab>()
    var activeTabId by mutableStateOf<Long?>(null)
    var nextTabId by mutableStateOf(1L)
    var pendingCloseTab by mutableStateOf<EditorTab?>(null)
    var pendingDeleteTab by mutableStateOf<EditorTab?>(null)
    var pendingCloseAfterSaveId by mutableStateOf<Long?>(null)
    var pendingSaveAsTabId by mutableStateOf<Long?>(null)

    var showFindBar by mutableStateOf(false)
    var showMarkdownPreview by mutableStateOf(false)
    var discordToken by mutableStateOf("")
    var discordEnabled by mutableStateOf(false)
    private var discordScope: kotlinx.coroutines.CoroutineScope? = null
    private var appScope: kotlinx.coroutines.CoroutineScope? = null

    fun setDiscordScope(scope: kotlinx.coroutines.CoroutineScope) {
        appScope = scope
        if (discordEnabled && discordToken.isNotBlank()) {
            DiscordRpcManager.start(discordToken, scope)
        }
    }

    fun updateDiscordToken(token: String) {
        discordToken = token
        appContext.getSharedPreferences("egg_discord", android.content.Context.MODE_PRIVATE)
            .edit().putString("token", token).apply()
        DiscordRpcManager.stop()
        if (discordEnabled && token.isNotBlank()) {
            appScope?.let { DiscordRpcManager.start(token, it) }
        }
    }

    fun updateDiscordEnabled(enabled: Boolean) {
        discordEnabled = enabled
        appContext.getSharedPreferences("egg_discord", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).apply()
        if (enabled && discordToken.isNotBlank()) {
            appScope?.let { DiscordRpcManager.start(discordToken, it) }
        } else {
            DiscordRpcManager.stop()
        }
    }

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()
    fun notify(msg: String) { _uiMessages.tryEmit(msg) }

    init {
        val prefs = appContext.getSharedPreferences("egg_discord", android.content.Context.MODE_PRIVATE)
        discordToken = prefs.getString("token", "") ?: ""
        discordEnabled = prefs.getBoolean("enabled", false)

        loadGitCredentials(appContext)?.let { (u, t) -> gitUser = u; gitToken = t }
        restoreLastProject()
        loadRecentFromPrefs()
        restoreTabs()
    }

    val activeTab: EditorTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    fun persistTabsPublic() { persistTabs() }

    private fun persistTabs() {
        val arr = org.json.JSONArray()
        tabs.forEach { t ->
            if (t.diffNew != null) return@forEach
            val u = t.uri ?: return@forEach
            arr.put(org.json.JSONObject().put("uri", u.toString()).put("name", t.name))
        }
        saveOpenTabs(appContext, arr.toString())
    }

    private fun restoreTabs() {
        val raw = loadOpenTabs(appContext) ?: return
        val arr = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return
        if (arr.length() == 0) return
        scope.launch {
            val restored = mutableListOf<EditorTab>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val uriStr = o.optString("uri", "")
                val name = o.optString("name", "")
                if (uriStr.isEmpty()) continue
                val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: continue
                val ok = runCatching {
                    if (uri.scheme == "file") {
                        uri.path?.let { File(it).exists() } == true
                    } else {
                        DocumentFile.fromSingleUri(appContext, uri)?.exists() == true
                    }
                }.getOrDefault(false)
                if (!ok) continue
                val content = runCatching { readFileContent(appContext, uri) }.getOrDefault("")
                restored.add(EditorTab(nextTabId++, name, uri, content))
            }
            if (restored.isNotEmpty()) {
                tabs.clear()
                tabs.addAll(restored)
                activeTabId = restored.first().id
            }
        }
    }

    private var settingsSaveJob: kotlinx.coroutines.Job? = null
    fun updateSettings(newSettings: EditorSettings) {
        settings = newSettings
        settingsSaveJob?.cancel()
        settingsSaveJob = scope.launch {
            kotlinx.coroutines.delay(300)
            saveSettings(appContext, settings)
        }
    }

    suspend fun getChildren(uri: Uri, depth: Int): List<FileNode> {
        val path = uri.toString()
        folderCache[path]?.let { return it }
        val nodes = withContext(Dispatchers.IO) {
            try {
                if (uri.scheme == "file") {
                    val f = uri.path?.let { File(it) }
                    val files = f?.listFiles() ?: emptyArray()
                    files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        .map { c -> FileNode(c.name, c.isDirectory, Uri.fromFile(c), depth) }
                } else {
                    val doc = DocumentFile.fromTreeUri(appContext, uri)
                    val files = doc?.listFiles() ?: emptyArray()
                    files.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
                        .map { d -> FileNode(d.name ?: "Unknown", d.isDirectory, d.uri, depth) }
                }
            } catch (_: Exception) { emptyList() }
        }
        folderCache[path] = nodes
        return nodes
    }

    suspend fun rebuildVisibleNodes() {
        val rootUri = when {
            rootIsPrivate -> rootFolderPath?.let { Uri.fromFile(File(it)) }
            else -> rootFolderUri
        } ?: return
        val result = mutableListOf<FileNode>()
        suspend fun walk(uri: Uri, depth: Int) {
            val children = getChildren(uri, depth)
            for (node in children) {
                result.add(node)
                if (node.isDirectory && expandedPaths.contains(node.uri.toString())) walk(node.uri, depth + 1)
            }
        }
        walk(rootUri, 0)
        visibleNodes = result
    }

    fun toggleExpand(uri: String) {
        expandedPaths = if (expandedPaths.contains(uri)) expandedPaths - uri else expandedPaths + uri
        scope.launch { rebuildVisibleNodes() }
    }

    fun openSingleFile(uri: Uri) {
        scope.launch {
            try {
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                val name = getFileName(appContext, uri)
                val content = readFileContent(appContext, uri)
                availableSections = listOf("File Manager")
                currentSection = "File Manager"
                val exists = tabs.indexOfFirst { it.uri == uri }
                if (exists >= 0) {
                    activeTabId = tabs[exists].id
                } else {
                    val t = EditorTab(nextTabId++, name, uri, content)
                    tabs.add(t)
                    activeTabId = t.id
                }
                persistTabs()
            } catch (e: Exception) {
            }
        }
    }

    private fun loadRecentFromPrefs() {
        val raw = loadRecentProjects(appContext) ?: run { recentProjects = emptyList(); return }
        val arr = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: run { recentProjects = emptyList(); return }
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val label = o.optString("label", "")
            val path = o.optString("path", "")
            if (label.isNotEmpty() && path.isNotEmpty()) out.add(label to path)
        }
        recentProjects = out
    }

    private fun recordRecent(label: String, path: String) {
        val existing = recentProjects.filter { it.second != path }
        val updated = (listOf(label to path) + existing).take(10)
        recentProjects = updated
        val arr = org.json.JSONArray()
        updated.forEach { (l, pth) ->
            arr.put(org.json.JSONObject().put("label", l).put("path", pth))
        }
        saveRecentProjects(appContext, arr.toString())
    }

    fun removeRecent(path: String) {
        val updated = recentProjects.filter { it.second != path }
        recentProjects = updated
        val arr = org.json.JSONArray()
        updated.forEach { (l, pth) ->
            arr.put(org.json.JSONObject().put("label", l).put("path", pth))
        }
        saveRecentProjects(appContext, arr.toString())
    }

    fun openAppStoragePicker() {
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                val out = mutableListOf<Pair<String, String>>()
                val repos = File(appContext.filesDir, "repos")
                if (repos.isDirectory) {
                    repos.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }?.forEach {
                        out.add("repos/${it.name}" to it.absolutePath)
                    }
                }
                val b2 = File(appContext.filesDir, "b2")
                if (b2.isDirectory) {
                    b2.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }?.forEach {
                        out.add("b2/${it.name}" to it.absolutePath)
                    }
                }
                out
            }
            appStorageItems = items
            showAppStorageDialog = true
        }
    }

    fun openPrivateRoot(label: String, path: String) {
        showAppStorageDialog = false
        val dir = File(path)
        if (!dir.isDirectory) { notify("Folder not found: $path"); return }
        rootFolderUri = null
        rootFolderPath = path
        rootIsPrivate = true
        saveLastPrivateRoot(appContext, path)
        recordRecent(label, path)
        clearLastProjectUri(appContext)
        val isB2 = label.startsWith("b2/")
        availableSections = if (isB2) listOf("Cloud Storage", "File Manager") else listOf("Git", "File Manager")
        currentSection = "File Manager"
        expandedPaths = emptySet()
        folderCache.clear()
        tabs.clear()
        val t0 = EditorTab(nextTabId++, "Untitled 1", null, "")
        tabs.add(t0); activeTabId = t0.id
        gitRepo = runCatching { Git.open(dir) }.getOrNull()
        scope.launch { rebuildVisibleNodes() }
        if (isB2) {
            val creds = loadB2Credentials(appContext)
            if (creds != null) {
                cloudConnect(creds.keyId, creds.appKey, creds.bucket)
            } else {
                notify("Reconnect to B2 needed for '$label'")
            }
        }
    }

    fun openFolder(uri: Uri) {
        scope.launch {
            rootFolderUri = uri
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            saveLastProjectUri(appContext, uri.toString())
            clearLastPrivateRoot(appContext)
            rootFolderPath = getPathFromTreeUri(uri)
            availableSections = buildAvailableSections(rootFolderPath)
            expandedPaths = emptySet()
            folderCache.clear()

            tabs.clear()
            val t = EditorTab(nextTabId++, "Untitled 1", null, "")
            tabs.add(t)
            activeTabId = t.id

            gitRepo = null; gitStatus = null; gitCommits = emptyList(); gitRemoteUrl = null
            gitUser = ""; gitToken = ""
            b2Account = null; b2Bucket = null
            cloudChanges = emptyList(); cloudHistory = emptyList(); cloudMessage = null
            rebuildVisibleNodes()
            val rp = rootFolderPath
            if (rp != null) {
                val repo = runCatching { withContext(Dispatchers.IO) { Git.open(File(rp)) } }.getOrNull()
                if (repo != null) {
                    gitRepo = repo
                    scheduleGitRefresh()
                }
            }
        }
    }

    fun closeProject() {
        clearLastProjectUri(appContext)
        clearOpenTabs(appContext)
        clearLastPrivateRoot(appContext)
        rootFolderUri = null; rootFolderPath = null; rootIsPrivate = false; visibleNodes = emptyList()
        availableSections = listOf("File Manager", "Cloud Storage", "Git")
        expandedPaths = emptySet(); folderCache.clear()
        tabs.clear()
        val t = EditorTab(nextTabId++, "Untitled 1", null, "")
        tabs.add(t); activeTabId = t.id
        gitRepo = null; gitStatus = null; gitCommits = emptyList(); gitRemoteUrl = null
        gitUser = ""; gitToken = ""
        b2Account = null; b2Bucket = null
        cloudChanges = emptyList(); cloudHistory = emptyList(); cloudMessage = null
    }

    fun refreshFolder() {
        scope.launch { folderCache.clear(); rebuildVisibleNodes() }
    }

    fun collapseAllOrExpandRoots() {
        scope.launch {
            if (expandedPaths.isNotEmpty()) {
                expandedPaths = emptySet()
            } else {
                val rootUri = rootFolderUri ?: return@launch
                val rootChildren = getChildren(rootUri, 0)
                expandedPaths = rootChildren.filter { it.isDirectory }.map { it.uri.toString() }.toSet()
            }
            rebuildVisibleNodes()
        }
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "md", "markdown" -> "text/markdown"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "kt", "kts", "java", "py", "sh", "c", "cpp", "h", "rs", "go", "ts" -> "text/plain"
            "yaml", "yml" -> "text/yaml"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    fun createFile(name: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val pr = privateRoot()
                if (pr != null) {
                    val dir = targetParentNode?.uri?.path?.let { File(it) } ?: pr
                    runCatching { File(dir, name).createNewFile() }
                } else parentDoc()?.createFile(mimeFor(name), name)
            }
            targetParentNode = null
            folderCache.clear()
            rebuildVisibleNodes()
        }
    }

    fun createFolder(name: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val pr = privateRoot()
                if (pr != null) {
                    val dir = targetParentNode?.uri?.path?.let { File(it) } ?: pr
                    runCatching { File(dir, name).mkdirs() }
                } else parentDoc()?.createDirectory(name)
            }
            targetParentNode = null
            folderCache.clear()
            rebuildVisibleNodes()
        }
    }

    fun rename(newName: String) {
        scope.launch {
            val node = selectedNode ?: return@launch
            val oldUriStr = node.uri.toString()
            val newNameClean = newName.trim()
            if (newNameClean.isEmpty() || newNameClean == node.name) return@launch

            val newUri: Uri? = withContext(Dispatchers.IO) {
                try {
                    if (node.uri.scheme == "file") {
                        val f = node.uri.path?.let { File(it) } ?: return@withContext null
                        val target = File(f.parentFile, newNameClean)
                        if (target.exists()) return@withContext null
                        val ok = f.renameTo(target)
                        if (ok) Uri.fromFile(target) else null
                    } else {
                        try {
                            val newDocUri = android.provider.DocumentsContract.renameDocument(
                                appContext.contentResolver, node.uri, newNameClean
                            )
                            newDocUri ?: node.uri
                        } catch (e: Exception) {
                            null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (newUri == null) {
                return@launch
            }

            val newUriStr = newUri.toString()
            val isDir = node.isDirectory
            for (i in tabs.indices) {
                val t = tabs[i]
                val tu = t.uri?.toString() ?: continue
                when {
                    tu == oldUriStr -> tabs[i] = t.copy(uri = newUri)
                    isDir && tu.startsWith(oldUriStr + "/") ->
                        tabs[i] = t.copy(uri = Uri.parse(newUriStr + tu.substring(oldUriStr.length)))
                }
            }
            persistTabs()

            if (isDir && expandedPaths.any { it == oldUriStr || it.startsWith(oldUriStr + "/") }) {
                expandedPaths = expandedPaths.map { ep ->
                    when {
                        ep == oldUriStr -> newUriStr
                        ep.startsWith(oldUriStr + "/") -> newUriStr + ep.substring(oldUriStr.length)
                        else -> ep
                    }
                }.toSet()
            }

            if (node.uri.scheme == "file" && rootIsPrivate) {
                val oldRootPath = rootFolderPath
                if (oldRootPath != null) {
                    val oldF = File(oldRootPath)
                    val newRootF = File(oldF.parentFile, newNameClean)
                    if (oldF.absolutePath == node.uri.path) {
                        rootFolderPath = newRootF.absolutePath
                        availableSections = buildAvailableSections(rootFolderPath)
                    }
                }
            } else if (node.uri.scheme != "file" && rootFolderUri == node.uri) {
                rootFolderPath = getPathFromTreeUri(newUri)
                availableSections = buildAvailableSections(rootFolderPath)
            }

            runCatching {
                val parentUri = DocumentFile.fromSingleUri(appContext, node.uri)?.parentFile?.uri
                if (parentUri != null) folderCache.remove(parentUri.toString())
            }
            folderCache.clear()
            selectedNode = visibleNodes.firstOrNull { it.uri == newUri } ?: node.copy(name = newNameClean, uri = newUri)
            rebuildVisibleNodes()
        }
    }

    fun deleteSelected() {
        scope.launch {
            val node = selectedNode
            withContext(Dispatchers.IO) {
                node?.let { n ->
                    if (n.uri.scheme == "file") {
                        n.uri.path?.let { runCatching { File(it).delete() } }
                    } else {
                        DocumentFile.fromSingleUri(appContext, n.uri)?.delete()
                    }
                }
            }
            if (node != null) {
                val base = node.uri.toString()
                val affected = tabs.filter { t ->
                    t.uri?.toString()?.let { it == base || it.startsWith(base + "/") } == true
                }
                if (affected.isNotEmpty()) {
                    val activeRemoved = affected.any { it.id == activeTabId }
                    val firstIdx = tabs.indexOfFirst { t -> affected.any { it.id == t.id } }
                    tabs.removeAll { t -> affected.any { it.id == t.id } }
                    if (activeRemoved) switchAfterRemoval(firstIdx.coerceAtLeast(0))
                    persistTabs()
                }
            }
            folderCache.clear()
            rebuildVisibleNodes()
        }
    }

    fun gitMarkerFor(node: FileNode): Char? {
        val st = gitStatus ?: return null
        if (!node.isDirectory) {
            val rel = relativeGitPath(node) ?: return null
            return when {
                st.modified.contains(rel) || st.unstaged.contains(rel) || st.staged.contains(rel) -> 'M'
                st.untracked.contains(rel) -> 'U'
                st.added.contains(rel) -> 'A'
                st.removed.contains(rel) || st.missing.contains(rel) -> 'D'
                else -> null
            }
        }
        val dirRel = relativeGitPath(node) ?: return null
        val prefix = if (dirRel.isEmpty()) "" else "$dirRel/"
        fun anyIn(list: List<String>): Boolean =
            list.any { it == dirRel || it.startsWith(prefix) }
        return when {
            anyIn(st.removed) || anyIn(st.missing) -> 'D'
            anyIn(st.modified) || anyIn(st.unstaged) || anyIn(st.staged) -> 'M'
            anyIn(st.untracked) -> 'U'
            anyIn(st.added) -> 'A'
            else -> null
        }
    }

    fun gitMarkerForTab(uri: android.net.Uri?): Char? {
        val st = gitStatus ?: return null
        if (uri == null) return null
        val root = rootFolderPath ?: return null
        val rel = runCatching {
            val rootFile = File(root).canonicalFile
            val nodeFile = File(uri.path ?: return null).canonicalFile
            nodeFile.relativeTo(rootFile).path.replace('\\', '/')
        }.getOrNull() ?: return null
        return when {
            st.modified.contains(rel) || st.unstaged.contains(rel) || st.staged.contains(rel) -> 'M'
            st.untracked.contains(rel) -> 'U'
            st.added.contains(rel) -> 'A'
            st.removed.contains(rel) || st.missing.contains(rel) -> 'D'
            else -> null
        }
    }

    private fun relativeGitPath(node: FileNode): String? {
        val root = rootFolderPath ?: return null
        val uriPath = node.uri.path ?: return null
        return runCatching {
            val rootFile = File(root).canonicalFile
            val nodeFile = File(uriPath).canonicalFile
            nodeFile.relativeTo(rootFile).path.replace('\\', '/')
        }.getOrNull()
    }

    private fun privateRoot(): File? = if (rootIsPrivate) rootFolderPath?.let { File(it) } else null

    private fun parentDoc(): DocumentFile? =
        targetParentNode?.let { DocumentFile.fromTreeUri(appContext, it.uri) }
            ?: rootFolderUri?.let { DocumentFile.fromTreeUri(appContext, it) }

    fun activeTabIndex(): Int = tabs.indexOfFirst { it.id == activeTabId }

    fun syncActiveTabContent(text: String) {
        val i = activeTabIndex()
        if (i >= 0 && tabs[i].content != text) tabs[i] = tabs[i].copy(content = text)
    }

    private fun switchAfterRemoval(removedIdx: Int) {
        if (tabs.isEmpty()) {
            val t = EditorTab(nextTabId++, "Untitled 1", null, "")
            tabs.add(t)
            activeTabId = t.id
        } else {
            activeTabId = tabs[minOf(removedIdx, tabs.lastIndex)].id
        }
    }

    fun reallyCloseTab(id: Long) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val wasActive = activeTabId == id
        tabs.removeAt(idx)
        if (wasActive) switchAfterRemoval(idx)
        persistTabs()
    }

    fun requestCloseTab(tab: EditorTab) {
        if (tab.isDirty) pendingCloseTab = tab else reallyCloseTab(tab.id)
    }

    fun saveTabAndClose(tab: EditorTab) {
        if (tab.diffNew != null) return
        val i = tabs.indexOfFirst { it.id == tab.id }
        if (i < 0) return
        val t = tabs[i]
        if (t.uri != null) {
            scope.launch {
                writeFileContent(appContext, t.uri, t.content)
                scheduleGitRefresh()
                reallyCloseTab(t.id)
            }
        } else {
            pendingCloseAfterSaveId = t.id
            pendingSaveAsTabId = t.id
            activeTabId = t.id
        }
    }

    fun closeOthers(id: Long) {
        tabs.removeAll { it.id != id }
        activeTabId = id
        persistTabs()
    }

    fun newUntitledTab() {
        val count = tabs.count { it.uri == null } + 1
        val t = EditorTab(nextTabId++, "Untitled $count", null, "")
        tabs.add(t)
        activeTabId = t.id
    }

    fun deleteTabFromStorage(tab: EditorTab) {
        scope.launch {
            withContext(Dispatchers.IO) {
                tab.uri?.let { u ->
                    if (u.scheme == "file") {
                        u.path?.let { pth -> runCatching { File(pth).delete() } }
                    } else {
                        runCatching { DocumentFile.fromSingleUri(appContext, u)?.delete() }
                    }
                }
            }
            val idx = tabs.indexOfFirst { it.id == tab.id }
            tabs.removeAll { it.id == tab.id }
            if (activeTabId == tab.id) switchAfterRemoval(idx)
            persistTabs()
            folderCache.clear()
            rebuildVisibleNodes()
        }
    }

    fun performBackup(uri: Uri) {
        scope.launch {
            try {
                val root = JSONObject()
                val s = JSONObject()
                s.put("themeName", settings.themeName)
                s.put("fontSizeSp", settings.fontSizeSp)
                s.put("showLineNumbers", settings.showLineNumbers)
                s.put("wordWrap", settings.wordWrap)
                s.put("tabWidth", settings.tabWidth)
                s.put("showBottomBar", settings.showBottomBar)
                s.put("fontFamily", settings.fontFamily)
                s.put("customFontPath", settings.customFontPath ?: "")
                s.put("confirmExit", settings.confirmExit)
                s.put("autoIndent", settings.autoIndent)
                s.put("autocomplete", settings.autocomplete)
                s.put("indentGuide", settings.indentGuide)
                s.put("highlightActiveLine", settings.highlightActiveLine)
                s.put("closeBracket", settings.closeBracket)
                root.put("settings", s)

                val arr = JSONArray()
                for (t in tabs) {
                    val o = JSONObject()
                    o.put("name", t.name)
                    t.uri?.toString()?.let { o.put("uri", it) }
                    o.put("content", t.content)
                    arr.put(o)
                }
                root.put("tabs", arr)
                writeFileContent(appContext, uri, root.toString())
            } catch (_: Exception) {
            }
        }
    }

    fun performRestore(uri: Uri) {
        scope.launch {
            try {
                val content = readFileContent(appContext, uri)
                val root = JSONObject(content)

                root.optJSONObject("settings")?.let { s ->
                    settings = EditorSettings(
                        themeName = s.optString("themeName", settings.themeName),
                        fontSizeSp = s.optInt("fontSizeSp", settings.fontSizeSp),
                        showLineNumbers = s.optBoolean("showLineNumbers", settings.showLineNumbers),
                        wordWrap = s.optBoolean("wordWrap", settings.wordWrap),
                        tabWidth = s.optInt("tabWidth", settings.tabWidth),
                        showBottomBar = s.optBoolean("showBottomBar", settings.showBottomBar),
                        fontFamily = s.optString("fontFamily", settings.fontFamily),
                        customFontPath = s.optString("customFontPath", "").ifEmpty { null },
                        confirmExit = s.optBoolean("confirmExit", settings.confirmExit),
                        autoIndent = s.optBoolean("autoIndent", settings.autoIndent),
                        autocomplete = s.optBoolean("autocomplete", settings.autocomplete),
                        indentGuide = s.optBoolean("indentGuide", settings.indentGuide),
                        highlightActiveLine = s.optBoolean("highlightActiveLine", settings.highlightActiveLine),
                        closeBracket = s.optBoolean("closeBracket", settings.closeBracket)
                    )
                    saveSettings(appContext, settings)
                }

                val arr = root.optJSONArray("tabs")
                tabs.clear()
                if (arr != null && arr.length() > 0) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        var tabUri: Uri? = null
                        val uriStr = o.optString("uri", "")
                        if (uriStr.isNotEmpty()) {
                            tabUri = runCatching { Uri.parse(uriStr) }.getOrNull()
                        }
                        val tab = EditorTab(
                            id = nextTabId++,
                            name = o.optString("name", "Untitled"),
                            uri = tabUri,
                            content = o.optString("content", "")
                        )
                        tabs.add(tab)
                    }
                } else {
                    val t = EditorTab(nextTabId++, "Untitled 1", null, "")
                    tabs.add(t)
                }
                activeTabId = tabs.first().id
            } catch (_: Exception) {
            }
        }
    }

    fun gitInit() {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            gitBusy = true; gitMessage = null
            try {
                val newGit = withContext(Dispatchers.IO) { Git.init().setDirectory(File(rootFolderPath!!)).call() }
                gitRepo = newGit
                gitStatus = readGitStatus(newGit)
                gitCommits = readGitLog(newGit)
                gitRemoteUrl = getGitRemoteUrl(newGit)
                gitMessage = "Repository initialized successfully."
                notify(gitMessage!!)
            } catch (e: Exception) { gitMessage = "Init failed: ${e.message}"; notify(gitMessage!!) } finally { gitBusy = false }
        }
    }

    fun scheduleGitRefresh() {
        if (gitRepo == null) return
        gitRefreshJob?.cancel()
        gitRefreshJob = scope.launch {
            delay(400)
            gitRefresh()
        }
    }

    fun gitRefresh() {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: run {
                    withContext(Dispatchers.IO) { Git.open(File(rootFolderPath!!)) }.also { gitRepo = it }
                }
                gitStatus = readGitStatus(g)
                gitCommits = readGitLog(g)
                gitRemoteUrl = getGitRemoteUrl(g)
            } catch (e: Exception) { gitMessage = "Failed to read status: ${e.message}"; notify(gitMessage!!) } finally { gitBusy = false }
        }
    }

    fun gitCommit() {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                withContext(Dispatchers.IO) {
                    g.add().addFilepattern(".").call()
                    g.commit().setMessage(commitMessage).setAuthor("Egg User", "egg@local").call()
                }
                commitMessage = ""
                gitStatus = readGitStatus(g)
                gitCommits = readGitLog(g)
                gitRemoteUrl = getGitRemoteUrl(g)
                gitMessage = "Commit succeeded. Pushing..."
                notify(gitMessage!!)
                gitPush()
            } catch (e: Exception) { gitMessage = "Commit failed: ${e.message}"; notify(gitMessage!!) } finally { gitBusy = false }
        }
    }

    fun gitDoClone(url: String, user: String, token: String) {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val repoName = url.trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "cloned-repo" }
                val dest = withContext(Dispatchers.IO) {
                    val base = File(appContext.filesDir, "repos")
                    base.mkdirs()
                    File(base, repoName)
                }
                val existing = withContext(Dispatchers.IO) {
                    if (!dest.exists() || dest.listFiles().isNullOrEmpty()) null
                    else runCatching { Git.open(dest) }.getOrNull()
                }
                if (dest.exists() && existing == null && !dest.listFiles().isNullOrEmpty()) {
                    throw IllegalStateException("Folder 'repos/$repoName' already exists and is not a Git repository. Delete it or use a different repo.")
                }
                val cloned = existing ?: run {
                    val cp = if (user.isNotBlank()) UsernamePasswordCredentialsProvider(user, token) else null
                    withContext(Dispatchers.IO) {
                        Git.cloneRepository()
                            .setURI(url)
                            .setDirectory(dest)
                            .apply { if (cp != null) setCredentialsProvider(cp) }
                            .call()
                    }
                }
                gitRepo = cloned
                gitStatus = readGitStatus(cloned)
                gitCommits = readGitLog(cloned)
                gitRemoteUrl = getGitRemoteUrl(cloned)
                gitUser = user; gitToken = token
                saveGitCredentials(appContext, user, token)
                rootFolderUri = null
                rootFolderPath = dest.absolutePath
                rootIsPrivate = true
                saveLastPrivateRoot(appContext, dest.absolutePath)
                recordRecent("repos/$repoName", dest.absolutePath)
                availableSections = listOf("Git", "File Manager")
                currentSection = "File Manager"
                clearLastProjectUri(appContext)
                expandedPaths = emptySet()
                folderCache.clear()
                tabs.clear()
                val t0 = EditorTab(nextTabId++, "Untitled 1", null, "")
                tabs.add(t0); activeTabId = t0.id
                rebuildVisibleNodes()
                gitMessage = "Clone succeeded: ${dest.absolutePath}"
                notify(gitMessage!!)
            } catch (e: Exception) { gitMessage = "Clone failed: ${e.message}"; notify(gitMessage!!) } finally { gitBusy = false }
        }
    }

    enum class GitActionKind { DISCARD_FILE, DISCARD_ALL }
    data class PendingGitAction(val kind: GitActionKind, val relPath: String = "", val untracked: Boolean = false)
    var pendingGitAction by mutableStateOf<PendingGitAction?>(null)

    private fun withGit(block: suspend (org.eclipse.jgit.api.Git) -> Unit) {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git features."); return@launch }
            val g = gitRepo ?: runCatching { withContext(Dispatchers.IO) { org.eclipse.jgit.api.Git.open(java.io.File(rootFolderPath!!)) } }.getOrNull()
            if (g == null) { gitMessage = "Git repository not available."; notify(gitMessage!!); return@launch }
            gitRepo = g
            gitBusy = true
            try { block(g) } finally { gitBusy = false }
        }
    }

    fun openGitDiff(relPath: String) {
        withGit { g ->
            if (relPath.startsWith("/") || relPath.contains("..")) return@withGit
            val old = runCatching {
                withContext(Dispatchers.IO) { readBlobAtHead(g, relPath) }
            }.getOrDefault("")
            val new = runCatching {
                withContext(Dispatchers.IO) { java.io.File(rootFolderPath!!, relPath).readText() }
            }.getOrDefault("")
            val name = "Diff: $relPath"
            val existing = tabs.indexOfFirst { it.diffNew != null && it.name == name }
            if (existing >= 0) {
                activeTabId = tabs[existing].id
            } else {
                val tab = EditorTab(nextTabId++, name, null, "", false, old, new)
                tabs.add(tab)
                activeTabId = tab.id
            }
        }
    }

    private fun readBlobAtHead(g: org.eclipse.jgit.api.Git, relPath: String): String {
        return try {
            val objId = g.repository.resolve("HEAD:$relPath")
                ?: return ""
            val loader = g.repository.open(objId)
            val bytes = loader.bytes
            if (bytes.any { it == 0.toByte() }) return "<binary>"
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) { "" }
    }

    fun gitStage(relPath: String) {
        withGit { g ->
            runCatching { withContext(Dispatchers.IO) { g.add().addFilepattern(relPath).call() } }
                .onFailure { gitMessage = "Stage failed: ${it.message}"; notify(gitMessage!!) }
            gitStatus = readGitStatus(g); gitCommits = readGitLog(g)
        }
    }

    fun gitUnstage(relPath: String) {
        withGit { g ->
            runCatching { withContext(Dispatchers.IO) { g.reset().addPath(relPath).call() } }
                .onFailure { gitMessage = "Unstage failed: ${it.message}"; notify(gitMessage!!) }
            gitStatus = readGitStatus(g); gitCommits = readGitLog(g)
        }
    }

    fun gitRequestDiscard(relPath: String, untracked: Boolean) {
        pendingGitAction = PendingGitAction(GitActionKind.DISCARD_FILE, relPath, untracked)
    }

    fun gitRequestDiscardAll() {
        pendingGitAction = PendingGitAction(GitActionKind.DISCARD_ALL)
    }

    fun gitCancelPending() { pendingGitAction = null }

    fun gitConfirmPending() {
        val act = pendingGitAction ?: return
        pendingGitAction = null
        when (act.kind) {
            GitActionKind.DISCARD_FILE -> gitDiscardOne(act.relPath, act.untracked)
            GitActionKind.DISCARD_ALL -> gitDiscardAll()
        }
    }

    private suspend fun reloadTabsForPaths(paths: Collection<String>) {
        val root = rootFolderPath ?: return
        for (rel in paths) {
            val idx = tabs.indexOfFirst { t ->
                val u = t.uri ?: return@indexOfFirst false
                when (u.scheme) {
                    "file" -> u.path?.let { File(it).canonicalPath == File(root, rel).canonicalPath } ?: false
                    else -> t.name == rel.substringAfterLast('/')
                }
            }
            if (idx >= 0) {
                val t = tabs[idx]
                val newContent = t.uri?.let { readFileContent(appContext, it) } ?: continue
                tabs[idx] = t.copy(content = newContent, isDirty = false)
            }
        }
    }

    private fun gitDiscardOne(relPath: String, untracked: Boolean) {
        withGit { g ->
            try {
                if (untracked) {
                    withContext(Dispatchers.IO) { java.io.File(rootFolderPath!!, relPath).delete() }
                } else {
                    withContext(Dispatchers.IO) { g.checkout().setStartPoint("HEAD").addPath(relPath).call() }
                }
                gitMessage = "Discarded: $relPath"
                notify(gitMessage!!)
                if (!untracked) reloadTabsForPaths(listOf(relPath))
            } catch (e: Exception) {
                gitMessage = "Discard failed: ${e.message}"
                notify(gitMessage!!)
            }
            gitStatus = readGitStatus(g); gitCommits = readGitLog(g)
        }
    }

    private fun gitDiscardAll() {
        withGit { g ->
            val status = readGitStatus(g)
            val unstaged = status.unstaged.toSet()
            val untracked = status.untracked.toSet()
            var done = 0
            var failed = 0
            val reloaded = mutableListOf<String>()
            untracked.forEach { rel ->
                if (runCatching { withContext(Dispatchers.IO) { java.io.File(rootFolderPath!!, rel).delete() } }.getOrDefault(false)) done++ else failed++
            }
            unstaged.forEach { rel ->
                if (runCatching { withContext(Dispatchers.IO) { g.checkout().setStartPoint("HEAD").addPath(rel).call() } }.isSuccess) { done++; reloaded.add(rel) } else failed++
            }
            if (reloaded.isNotEmpty()) reloadTabsForPaths(reloaded)
            gitMessage = if (failed > 0) "Discard All: $done done, $failed failed." else "Discard All: $done file(s) discarded."
            notify(gitMessage!!)
            gitStatus = readGitStatus(g); gitCommits = readGitLog(g)
        }
    }

    fun gitAddRemote(url: String, user: String, token: String) {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                withContext(Dispatchers.IO) {
                    g.remoteAdd().setName("origin").setUri(URIish(url)).call()
                }
                gitUser = user; gitToken = token; gitRemoteUrl = url
                gitMessage = "Remote 'origin' added."
                notify(gitMessage!!)
            } catch (e: Exception) { gitMessage = "Add remote failed: ${e.message}"; notify(gitMessage!!) } finally { gitBusy = false }
        }
    }

    fun gitFetch() {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                withContext(Dispatchers.IO) {
                    val cmd = g.fetch()
                    if (gitUser.isNotBlank() && gitToken.isNotBlank()) cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(gitUser, gitToken))
                    cmd.call()
                }
                gitMessage = "Fetch succeeded."
                notify(gitMessage!!)
            } catch (e: Exception) { gitMessage = "Fetch failed: ${e.message}"; notify(gitMessage!!) } finally { gitBusy = false }
        }
    }

    fun gitPull() {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                val result = withContext(Dispatchers.IO) {
                    val cmd = g.pull()
                    if (gitUser.isNotBlank() && gitToken.isNotBlank()) cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(gitUser, gitToken))
                    cmd.call()
                }
                gitStatus = readGitStatus(g)
                gitCommits = readGitLog(g)
                gitMessage = if (result.isSuccessful) "Pull succeeded." else "Pull failed: $result"
                notify(gitMessage!!)
            } catch (e: Exception) { gitMessage = "Pull failed: ${e.message}"; notify(gitMessage!!) } finally { gitBusy = false }
        }
    }

    fun gitPush() {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                val result = withContext(Dispatchers.IO) {
                    val cmd = g.push()
                    if (gitUser.isNotBlank() && gitToken.isNotBlank()) cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(gitUser, gitToken))
                    cmd.call()
                }
                gitMessage = if (result.iterator().hasNext()) "Push succeeded." else "Push failed: nothing pushed."
                notify(gitMessage!!)
            } catch (e: Exception) { gitMessage = "Push failed: ${e.message}"; notify(gitMessage!!) } finally { gitBusy = false }
        }
    }

    fun openB2FromWelcome() {
        welcomeB2Mode = true
        showCloudConnectDialog = true
    }

    fun cloudConnect(keyId: String, appKey: String, bucket: String) {
       scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Authorize(keyId, appKey)
                val bucketInfo = when {
                    acc.allowedBucketId != null && acc.allowedBucketName != null ->
                        B2Bucket(acc.allowedBucketId!!, acc.allowedBucketName!!)
                    else -> {
                        b2FindBucket(acc, bucket) ?: throw Exception("Bucket '$bucket' not found")
                    }
                }
                if (welcomeB2Mode) {
                    welcomeB2Mode = false
                    pullAfterConnect = true
                    val dir = File(appContext.filesDir, "b2/${bucketInfo.bucketName}")
                    dir.mkdirs()
                    rootFolderUri = null
                    rootFolderPath = dir.absolutePath
                    rootIsPrivate = true
                    saveLastPrivateRoot(appContext, dir.absolutePath)
                    clearLastProjectUri(appContext)
                    recordRecent("b2/${bucketInfo.bucketName}", dir.absolutePath)
                    availableSections = listOf("Cloud Storage", "File Manager")
                    currentSection = "File Manager"
                    expandedPaths = emptySet()
                    folderCache.clear()
                    tabs.clear()
                    val t0 = EditorTab(nextTabId++, "Untitled 1", null, "")
                    tabs.add(t0); activeTabId = t0.id
                    gitRepo = null
                    cloudIndex = null; cloudIndexRoot = null
                    scope.launch { rebuildVisibleNodes() }
                }
                b2Account = acc; b2Bucket = bucketInfo
                saveB2Credentials(appContext, keyId, appKey, bucketInfo.bucketName)
                val remote = b2ListFiles(acc, bucketInfo.bucketId)
                val path = rootFolderPath ?: throw IllegalStateException("This project has no filesystem path")
                cloudChanges = computeCloudChanges(File(path), remote, cloudIndexOf())
                cloudHistory = b2ListFileVersions(acc, bucketInfo.bucketId)
                cloudMessage = "Connected to bucket '${bucketInfo.bucketName}'."
           } catch (e: Exception) {
                cloudMessage = "Connect failed:\n${e.stackTraceToString().take(1500)}"
           } finally {
                cloudBusy = false; cloudMessage?.let { notify(it) }
                if (pullAfterConnect) { pullAfterConnect = false; scope.launch { cloudPull() } }
           }
        }
    }

    fun cloudFetch() {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
                cloudMessage = "Fetch succeeded."
            } catch (e: Exception) { cloudMessage = "Fetch failed: ${e.message}" } finally { cloudBusy = false; cloudMessage?.let { notify(it) } }
        }
    }

    fun cloudRefresh() {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val remote = b2ListFiles(acc, bucket.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remote, cloudIndexOf())
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
            } catch (e: Exception) { cloudMessage = "Failed to read status: ${e.message}" } finally { cloudBusy = false }
        }
    }

    fun cloudPush() {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val remote = b2ListFiles(acc, bucket.bucketId)
                val changes = computeCloudChanges(File(rootFolderPath!!), remote, cloudIndexOf())
                val toUpload = changes.mapNotNull {
                    when (it) {
                        is CloudChange.New -> it.relPath
                        is CloudChange.Modified -> it.relPath
                        is CloudChange.RemoteOnly -> null
                    }
                }
                val sem = Semaphore(4)
                val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
                var uploaded = 0
                coroutineScope {
                    toUpload.map { relPath ->
                        async {
                            sem.withPermit {
                                runCatching {
                                    b2UploadFile(acc, bucket.bucketId, relPath, File(rootFolderPath!!, relPath))
                                }.onSuccess { synchronized(this@EggViewModel) { uploaded++ } }
                                    .onFailure { errors.add("$relPath: ${it.message}") }
                            }
                        }
                    }.awaitAll()
                }
                val remoteAfter = b2ListFiles(acc, bucket.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remoteAfter, cloudIndexOf())
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
                cloudMessage = when {
                    errors.isNotEmpty() -> "Push: $uploaded uploaded, ${errors.size} failed. First: ${errors.first()}"
                    uploaded > 0 -> "Push succeeded: $uploaded file(s) uploaded."
                    else -> "Push succeeded: nothing to upload."
                }
            } catch (e: Exception) { cloudMessage = "Push failed: ${e.message}" } finally { cloudBusy = false; cloudMessage?.let { notify(it) } }
        }
    }

    enum class CloudActionKind { DISCARD_MODIFIED, DISCARD_UNTRACKED, DELETE_REMOTE, DISCARD_ALL }
    data class PendingCloudAction(val kind: CloudActionKind, val relPath: String = "")
    var pendingCloudAction by mutableStateOf<PendingCloudAction?>(null)

    fun cloudRequestDiscard(relPath: String, isUntracked: Boolean) {
        pendingCloudAction = PendingCloudAction(
            kind = if (isUntracked) CloudActionKind.DISCARD_UNTRACKED else CloudActionKind.DISCARD_MODIFIED,
            relPath = relPath
        )
    }

    fun cloudRequestDeleteRemote(relPath: String) {
        pendingCloudAction = PendingCloudAction(CloudActionKind.DELETE_REMOTE, relPath)
    }

    fun cloudRequestDiscardAll() {
        pendingCloudAction = PendingCloudAction(CloudActionKind.DISCARD_ALL)
    }

    fun cloudCancelPending() { pendingCloudAction = null }

    fun cloudConfirmPending() {
        val act = pendingCloudAction ?: return
        pendingCloudAction = null
        when (act.kind) {
            CloudActionKind.DISCARD_MODIFIED -> discardOne(act.relPath, untracked = false)
            CloudActionKind.DISCARD_UNTRACKED -> discardOne(act.relPath, untracked = true)
            CloudActionKind.DELETE_REMOTE -> deleteRemoteOne(act.relPath)
            CloudActionKind.DISCARD_ALL -> discardAll()
        }
    }

    fun cloudPushFile(relPath: String) {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                b2UploadFile(acc, bucket.bucketId, relPath, File(rootFolderPath!!, relPath))
                cloudMessage = "Pushed: $relPath"
                cloudRefresh()
            } catch (e: Exception) { cloudMessage = "Push failed: $relPath: ${e.message}" } finally { cloudBusy = false; cloudMessage?.let { notify(it) } }
        }
    }

    private fun discardOne(relPath: String, untracked: Boolean) {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                if (rootIsPrivate) {
                    val root = File(rootFolderPath!!)
                    val f = File(root, relPath)
                    if (untracked) {
                        runCatching { f.delete() }
                    } else {
                        val data = b2DownloadBytes(acc, bucket.bucketName, relPath)
                        f.parentFile?.mkdirs()
                        f.writeBytes(data)
                    }
                } else {
                    if (untracked) {
                        val target = findOrCreateDocumentFile(appContext, rootFolderUri!!, relPath)
                        target?.delete()
                    } else {
                        val data = b2DownloadBytes(acc, bucket.bucketName, relPath)
                        val target = findOrCreateDocumentFile(appContext, rootFolderUri!!, relPath)
                            ?: throw Exception("Cannot create local file: $relPath")
                        appContext.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(data) }
                            ?: throw Exception("Cannot open output stream: $relPath")
                    }
                }
                cloudIndexOf()
                cloudMessage = "Discarded: $relPath"
                cloudRefresh()
            } catch (e: Exception) { cloudMessage = "Discard failed: ${e.message}" } finally { cloudBusy = false; cloudMessage?.let { notify(it) } }
        }
    }

    private fun deleteRemoteOne(relPath: String) {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val info = cloudHistory.firstOrNull { it.fileName == relPath && it.fileId.isNotEmpty() }
                    ?: throw Exception("No fileId for $relPath (refresh History first)")
                b2DeleteFileVersion(acc, relPath, info.fileId)
                cloudMessage = "Deleted remote: $relPath"
                cloudRefresh()
            } catch (e: Exception) { cloudMessage = "Delete failed: ${e.message}" } finally { cloudBusy = false; cloudMessage?.let { notify(it) } }
        }
    }

    private fun discardAll() {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val mods = cloudChanges.filterIsInstance<CloudChange.Modified>().map { it.relPath }
                val news = cloudChanges.filterIsInstance<CloudChange.New>().map { it.relPath }
                val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
                var done = 0
                val sem = Semaphore(4)
                coroutineScope {
                    (mods.map { it to false } + news.map { it to true }).map { (rp, untracked) ->
                        async {
                            sem.withPermit {
                                runCatching {
                                    if (rootIsPrivate) {
                                        val root = File(rootFolderPath!!)
                                        val f = File(root, rp)
                                        if (untracked) {
                                            f.delete()
                                        } else {
                                            val data = b2DownloadBytes(acc, bucket.bucketName, rp)
                                            f.parentFile?.mkdirs()
                                            f.writeBytes(data)
                                        }
                                    } else {
                                        if (untracked) {
                                            findOrCreateDocumentFile(appContext, rootFolderUri!!, rp)?.delete()
                                        } else {
                                            val data = b2DownloadBytes(acc, bucket.bucketName, rp)
                                            val t = findOrCreateDocumentFile(appContext, rootFolderUri!!, rp)
                                                ?: throw Exception("cannot create $rp")
                                            appContext.contentResolver.openOutputStream(t.uri, "wt")?.use { it.write(data) }
                                                ?: throw Exception("cannot open $rp")
                                        }
                                    }
                                }.onSuccess { synchronized(this@EggViewModel) { done++ } }
                                    .onFailure { errors.add("$rp: ${it.message}") }
                            }
                        }
                    }.awaitAll()
                }
                cloudMessage = when {
                    errors.isNotEmpty() -> "Discard All: $done done, ${errors.size} failed. First: ${errors.first()}"
                    else -> "Discard All: $done file(s) discarded."
                }
                cloudRefresh()
            } catch (e: Exception) { cloudMessage = "Discard All failed: ${e.message}" } finally { cloudBusy = false; cloudMessage?.let { notify(it) } }
        }
    }

    fun cloudPull() {
       scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git/B2 features."); return@launch }
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val remote = b2ListFiles(acc, bucket.bucketId)
                val changes = computeCloudChanges(File(rootFolderPath!!), remote, cloudIndexOf())
                val toPull = changes.mapNotNull { (it as? CloudChange.RemoteOnly)?.relPath }
                val sem = Semaphore(4)
                val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
                var downloaded = 0
                coroutineScope {
                    toPull.map { relPath ->
                        async {
                            sem.withPermit {
                                runCatching {
                                    val data = b2DownloadBytes(acc, bucket.bucketName, relPath)
                                    if (rootIsPrivate) {
                                        val root = File(rootFolderPath!!)
                                        val target = File(root, relPath)
                                        target.parentFile?.mkdirs()
                                        target.writeBytes(data)
                                    } else {
                                        val target = findOrCreateDocumentFile(appContext, rootFolderUri!!, relPath)
                                            ?: throw Exception("Cannot create local file: $relPath")
                                        appContext.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(data) }
                                            ?: throw Exception("Cannot open output stream: $relPath")
                                    }
                                }.onSuccess { synchronized(this@EggViewModel) { downloaded++ } }
                                    .onFailure { errors.add("$relPath: ${it.message}") }
                            }
                        }
                    }.awaitAll()
                }
                val remoteAfter = b2ListFiles(acc, bucket.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remoteAfter, cloudIndexOf())
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
                cloudMessage = when {
                    errors.isNotEmpty() -> "Pull: $downloaded downloaded, ${errors.size} failed. First: ${errors.first()}"
                    downloaded > 0 -> "Pull succeeded: $downloaded file(s) downloaded."
                   else -> "Pull succeeded: nothing to pull."
                }
            } catch (e: Exception) {
                cloudMessage = "Pull failed: ${e.message}"
           } finally {
                cloudBusy = false; cloudMessage?.let { notify(it) }
            }
        }
    }
}