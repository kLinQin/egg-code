package com.egg.codeeditor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import com.egg.codeeditor.model.EditorSettings
import com.egg.codeeditor.model.EditorTab
import com.egg.codeeditor.model.FileNode
import com.egg.codeeditor.model.GitStatusInfo
import com.egg.codeeditor.discord.DiscordRpcManager
import com.egg.codeeditor.util.GitCommitInfo
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
import com.egg.codeeditor.util.saveGitCredentials
import com.egg.codeeditor.util.loadGitCredentials
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

import com.egg.codeeditor.util.SupabaseClient
import com.egg.codeeditor.util.SupabaseFolder
import com.egg.codeeditor.util.SupabaseNote
import com.egg.codeeditor.util.SupabaseUser

class EggViewModel(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var gitRefreshJob: Job? = null

    init {
        SupabaseClient.init(appContext)
        scope.launch {
            if (SupabaseClient.restoreSession()) {
                supabaseUser = SupabaseClient.currentUser
                afterSupabaseAuth()
            }
        }
    }

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
                availableSections = listOf("Git", "File Manager")
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
        val list = mutableListOf("File Manager")
        if (hasGit) list.add("Git")
        return list
    }

    fun reloadAllTabsFromDisk() {
        scope.launch {
            val toReload = mutableListOf<Int>()
            for (i in tabs.indices) {
                val t = tabs[i]
                val uri = t.uri ?: continue
                if (uri.scheme == "supabase") continue
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



    var currentSection by mutableStateOf("File Manager")

    var rootFolderUri by mutableStateOf<Uri?>(null)
    var rootFolderPath by mutableStateOf<String?>(null)
    var rootIsPrivate by mutableStateOf(false)
    var availableSections by mutableStateOf(listOf("File Manager"))
    var visibleNodes by mutableStateOf<List<FileNode>>(emptyList())
    var expandedPaths by mutableStateOf<Set<String>>(emptySet())
    val folderCache = mutableStateMapOf<String, List<FileNode>>()

    var supabaseUser by mutableStateOf<SupabaseUser?>(null)
    var supabaseNotes by mutableStateOf<List<SupabaseNote>>(emptyList())
    var supabaseFolders by mutableStateOf<List<SupabaseFolder>>(emptyList())
    var showCreateNoteDialog by mutableStateOf(false)
    var showCreateFolderDialog by mutableStateOf(false)
    var createTargetFolder by mutableStateOf<String?>(null)
    var createTargetParent by mutableStateOf<String?>(null)
    var newNoteIsPublic by mutableStateOf(false)
    var newFolderIsPublic by mutableStateOf(false)
    var showLoginDialog by mutableStateOf(false)
    var supabaseLoading by mutableStateOf(false)
    private val supabaseExpandedPaths = mutableStateOf<Set<String>>(emptySet())
    val isSupabaseMode: Boolean get() = supabaseUser != null && supabaseProjectActive
    var supabaseProjectActive by mutableStateOf(false)

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

    var showAppStorageDialog by mutableStateOf(false)
    var appStorageItems by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var recentProjects by mutableStateOf<List<Pair<String, String>>>(emptyList())

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
                if (uri.scheme == "supabase") continue
                val ok = runCatching {
                    if (uri.scheme == "file") {
                        uri.path?.let { File(it).exists() } == true
                    } else {
                        DocumentFile.fromSingleUri(appContext, uri)?.exists() == true
                    }
                }.getOrDefault(false)
                if (!ok) continue
                val content = runCatching { readFileContent(appContext, uri) }.getOrNull() ?: continue
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

    private fun displayNoteName(title: String, id: Long): String {
        val base = title.ifBlank { "Untitled" }
        val lower = base.lowercase()
        if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) return base
        return "$base.md"
    }

    fun buildSupabaseFileNodes(): List<FileNode> {
        val result = mutableListOf<FileNode>()
        val allFolderPaths = LinkedHashSet<String>()
        supabaseFolders.forEach { allFolderPaths.add(it.name) }
        supabaseNotes.forEach { n ->
            val mc = n.mainCategory.ifBlank { "Unfiled" }
            if (mc.isNotBlank()) allFolderPaths.add(mc)
        }
        if (allFolderPaths.isEmpty() && supabaseNotes.isEmpty()) return emptyList()

        // Normalisasi: untuk path "A/B", pastikan "A" ada sebagai folder juga
        val expanded = LinkedHashSet<String>()
        allFolderPaths.forEach { p ->
            val parts = p.split("/")
            var acc = ""
            parts.forEachIndexed { i, part ->
                acc = if (acc.isEmpty()) part else "$acc/$part"
                expanded.add(acc)
            }
        }
        // map: parentPath -> list of child folder names
        val childrenByParent = mutableMapOf<String, MutableList<String>>()
        expanded.forEach { p ->
            val idx = p.lastIndexOf("/")
            val parent = if (idx > 0) p.substring(0, idx) else ""
            val name = if (idx >= 0) p.substring(idx + 1) else p
            childrenByParent.getOrPut(parent) { mutableListOf() }.add(name)
        }
        // map: folderPath -> notes with main_category == path
        val notesByFolder = supabaseNotes.groupBy { it.mainCategory.ifBlank { "Unfiled" } }

        fun walk(parent: String, depth: Int) {
            val children = childrenByParent[parent].orEmpty().sorted()
            for (name in children) {
                val fullPath = if (parent.isEmpty()) name else "$parent/$name"
                val folderUri = Uri.parse("supabase://folder/" + Uri.encode(fullPath))
                val folderNode = FileNode(name, true, folderUri, depth)
                result.add(folderNode)
                if (!supabaseExpandedPaths.value.contains(folderUri.toString())) continue

                // Sub-folders
                walk(fullPath, depth + 1)

                // Notes langsung di folder ini
                val folderNotes = notesByFolder[fullPath].orEmpty()
                val subs = folderNotes.groupBy { it.category.ifBlank { "" } }
                for ((cat, catNotes) in subs) {
                    if (cat.isBlank()) {
                        for (n in catNotes.sortedWith(compareByDescending<SupabaseNote> { it.isPinned }.thenByDescending { it.updatedAt })) {
                            result.add(FileNode(displayNoteName(n.title, n.id), false, Uri.parse("supabase://note/${n.id}"), depth + 1, n.isPublic))
                        }
                    } else {
                        val subUri = Uri.parse("supabase://folder/" + Uri.encode(fullPath) + "/" + Uri.encode(cat))
                        val subNode = FileNode(cat, true, subUri, depth + 1)
                        result.add(subNode)
                        if (!supabaseExpandedPaths.value.contains(subUri.toString())) continue
                        for (n in catNotes.sortedWith(compareByDescending<SupabaseNote> { it.isPinned }.thenByDescending { it.updatedAt })) {
                            result.add(FileNode(displayNoteName(n.title, n.id), false, Uri.parse("supabase://note/${n.id}"), depth + 2, n.isPublic))
                        }
                    }
                }
            }
        }
        walk("", 0)

        // Notes di folder "Unfiled" jika belum ada child folder "Unfiled" eksplisit
        if (!expanded.contains("Unfiled")) {
            val unfiled = notesByFolder["Unfiled"].orEmpty().filter { it.mainCategory.isBlank() }
            if (unfiled.isNotEmpty()) {
                val uf = FileNode("Unfiled", true, Uri.parse("supabase://folder/" + Uri.encode("Unfiled")), 0)
                result.add(uf)
                if (supabaseExpandedPaths.value.contains(uf.uri.toString())) {
                    for (n in unfiled.sortedWith(compareByDescending<SupabaseNote> { it.isPinned }.thenByDescending { it.updatedAt })) {
                        result.add(FileNode(displayNoteName(n.title, n.id), false, Uri.parse("supabase://note/${n.id}"), 1, n.isPublic))
                    }
                }
            }
        }
        return result
    }

    private suspend fun afterSupabaseAuth() {
        val r = SupabaseClient.listNotes()
        r.onSuccess { supabaseNotes = it }
            .onFailure { notify(it.message ?: "Failed to load notes") }
        SupabaseClient.listFolders()
            .onSuccess { supabaseFolders = it }
            .onFailure { /* optional */ }
        supabaseExpandedPaths.value = emptySet()
        supabaseProjectActive = true
        val sections = mutableListOf("File Manager")
        if (rootFolderPath != null && runCatching { org.eclipse.jgit.api.Git.open(File(rootFolderPath!!)).use { true } }.getOrDefault(false)) {
            sections.add("Git")
        }
        availableSections = sections
        currentSection = "File Manager"
        visibleNodes = buildSupabaseFileNodes()
    }

    fun supabaseLogin(email: String, password: String) {
        supabaseLoading = true
        scope.launch {
            val r = SupabaseClient.signInWithPassword(email.trim(), password)
            r.onSuccess {
                supabaseUser = it
                afterSupabaseAuth()
            }.onFailure { notify(it.message ?: "Login failed") }
            supabaseLoading = false
        }
    }

    fun supabaseSignUp(email: String, password: String, name: String) {
        supabaseLoading = true
        scope.launch {
            val r = SupabaseClient.signUp(email.trim(), password, name.trim())
            r.onSuccess {
                supabaseUser = it
                afterSupabaseAuth()
            }.onFailure { notify(it.message ?: "Sign up failed") }
            supabaseLoading = false
        }
    }

    fun enterSupabaseMode() {
        if (supabaseUser == null) return
        scope.launch { afterSupabaseAuth() }
    }

    fun clearGitCredentials() {
        gitUser = ""; gitToken = ""
        saveGitCredentials(appContext, "", "")
    }

    fun supabaseLogout() {
        scope.launch {
            SupabaseClient.signOut()
            supabaseUser = null
            supabaseNotes = emptyList()
            supabaseFolders = emptyList()
            supabaseExpandedPaths.value = emptySet()
            availableSections = buildAvailableSections(rootFolderPath)
            supabaseProjectActive = false
            rebuildVisibleNodes()
        }
    }

    fun openSupabaseNote(id: Long?) {
        if (id == null) return
        scope.launch {
            val content = SupabaseClient.getNoteContent(id)
            if (content == null) { notify("Failed to load note"); return@launch }
            val uri = Uri.parse("supabase://note/$id")
            val baseTitle = supabaseNotes.firstOrNull { it.id == id }?.title ?: "Note $id"
            val name = displayNoteName(baseTitle, id)
            val exists = tabs.indexOfFirst { it.uri == uri }
            if (exists >= 0) {
                activeTabId = tabs[exists].id
            } else {
                val t = EditorTab(nextTabId++, name, uri, content)
                tabs.add(t)
                activeTabId = t.id
            }
            currentSection = "File Manager"
        }
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
        if (uri.startsWith("supabase://")) {
            supabaseExpandedPaths.value = if (supabaseExpandedPaths.value.contains(uri)) supabaseExpandedPaths.value - uri else supabaseExpandedPaths.value + uri
            visibleNodes = buildSupabaseFileNodes()
            return
        }
        expandedPaths = if (expandedPaths.contains(uri)) expandedPaths - uri else expandedPaths + uri
        scope.launch { rebuildVisibleNodes() }
    }

    fun openSingleFile(uri: Uri) {
        scope.launch {
            supabaseProjectActive = false
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
                notify("Failed to open file: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun openTerminalFile(hostPath: String) {
        scope.launch {
            try {
                val f = File(hostPath)
                if (!f.exists()) { notify("File not found: $hostPath"); return@launch }
                if (f.isDirectory) { notify("This is a folder: ${f.name}"); return@launch }
                val name = f.name
                val content = runCatching { f.readText() }.getOrDefault("")
                availableSections = listOf("File Manager")
                currentSection = "File Manager"
                val uri = Uri.fromFile(f)
                val existing = tabs.indexOfFirst { it.uri == uri }
                if (existing >= 0) {
                    activeTabId = tabs[existing].id
                } else {
                    val t = EditorTab(nextTabId++, name, uri, content)
                    tabs.add(t)
                    activeTabId = t.id
                }
                persistTabs()
            } catch (_: Exception) {}
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
        supabaseProjectActive = false
        rootFolderUri = null
        rootFolderPath = path
        rootIsPrivate = true
        saveLastPrivateRoot(appContext, path)
        recordRecent(label, path)
        clearLastProjectUri(appContext)
        availableSections = listOf("Git", "File Manager")
        currentSection = "File Manager"
        expandedPaths = emptySet()
        folderCache.clear()
        tabs.clear()
        val t0 = EditorTab(nextTabId++, "Untitled 1", null, "")
        tabs.add(t0); activeTabId = t0.id
        gitRepo = runCatching { Git.open(dir) }.getOrNull()
        scope.launch { rebuildVisibleNodes() }
    }

    fun openFolder(uri: Uri) {
        scope.launch {
            supabaseProjectActive = false
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
        rootFolderUri = null; rootFolderPath = null; rootIsPrivate = false; visibleNodes = emptyList(); supabaseProjectActive = false
        availableSections = listOf("File Manager", "Git")
        expandedPaths = emptySet(); folderCache.clear()
        tabs.clear()
        val t = EditorTab(nextTabId++, "Untitled 1", null, "")
        tabs.add(t); activeTabId = t.id
        gitRepo = null; gitStatus = null; gitCommits = emptyList(); gitRemoteUrl = null
        gitUser = ""; gitToken = ""
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
                val tgt = targetParentNode
                if (pr != null && (tgt == null || tgt.uri.scheme == "file")) {
                    val dir = tgt?.uri?.path?.let { File(it) } ?: pr
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
                val tgt = targetParentNode
                if (pr != null && (tgt == null || tgt.uri.scheme == "file")) {
                    val dir = tgt?.uri?.path?.let { File(it) } ?: pr
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
                if (SupabaseClient.isSupabaseNote(t.uri)) {
                    val id = SupabaseClient.noteIdFromUri(t.uri)
                    if (id != null) SupabaseClient.updateNoteContent(id, t.content)
                } else {
                    writeFileContent(appContext, t.uri, t.content)
                    scheduleGitRefresh()
                }
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

    fun createSupabaseNote(title: String, category: String, mainCategory: String, isPublic: Boolean) {
        if (title.isBlank()) { notify("Title required"); return }
        scope.launch {
            supabaseLoading = true
            SupabaseClient.createNote(title.trim(), category.trim(), mainCategory.trim(), "", isPublic)
                .onSuccess { note ->
                    supabaseNotes = listOf(note) + supabaseNotes
                    if (isSupabaseMode) visibleNodes = buildSupabaseFileNodes()
                    notify("Note created")
                }
                .onFailure { notify(it.message ?: "Create note failed") }
            supabaseLoading = false
        }
    }

    fun createSupabaseFolder(name: String, isPublic: Boolean, parentPath: String? = null) {
        if (name.isBlank()) { notify("Name required"); return }
        val cleanName = name.trim().replace("/", "-")
        val fullPath = if (parentPath.isNullOrBlank()) cleanName else "$parentPath/$cleanName"
        scope.launch {
            supabaseLoading = true
            SupabaseClient.createFolder(fullPath, "supabase", isPublic)
                .onSuccess { folder ->
                    supabaseFolders = supabaseFolders + folder
                    if (isSupabaseMode) visibleNodes = buildSupabaseFileNodes()
                    notify("Folder created")
                }
                .onFailure { notify(it.message ?: "Create folder failed") }
            supabaseLoading = false
        }
    }

    fun renameSupabaseNote(id: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        scope.launch {
            supabaseLoading = true
            SupabaseClient.updateNote(id, newTitle.trim(), null, null)
                .let { ok ->
                    if (ok) {
                        supabaseNotes = supabaseNotes.map { if (it.id == id) it.copy(title = newTitle.trim()) else it }
                        tabs.replaceAll { t -> if (t.uri?.toString() == "supabase://note/$id") t.copy(name = displayNoteName(newTitle.trim(), id)) else t }
                        if (isSupabaseMode) visibleNodes = buildSupabaseFileNodes()
                        notify("Renamed")
                    } else notify("Rename failed")
                }
            supabaseLoading = false
        }
    }

    fun deleteSupabaseNote(id: Long) {
        scope.launch {
            supabaseLoading = true
            val ok = SupabaseClient.deleteNote(id)
            if (ok) {
                supabaseNotes = supabaseNotes.filterNot { it.id == id }
                val uri = "supabase://note/$id"
                val idx = tabs.indexOfFirst { it.uri?.toString() == uri }
                if (idx >= 0) {
                    val wasActive = tabs[idx].id == activeTabId
                    tabs.removeAt(idx)
                    if (wasActive) switchAfterRemoval(idx)
                    persistTabs()
                }
                if (isSupabaseMode) visibleNodes = buildSupabaseFileNodes()
                notify("Note deleted")
            } else notify("Delete failed")
            supabaseLoading = false
        }
    }

    fun renameSupabaseFolder(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed.contains("/")) { notify("Invalid name"); return }
        val oldFolder = supabaseFolders.firstOrNull { it.id == id } ?: return
        val oldPath = oldFolder.name
        val idx = oldPath.lastIndexOf("/")
        val parentPath = if (idx > 0) oldPath.substring(0, idx) else ""
        val newPath = if (parentPath.isEmpty()) trimmed else "$parentPath/$trimmed"
        if (newPath == oldPath) return
        scope.launch {
            supabaseLoading = true
            val ok = SupabaseClient.renameFolder(id, newPath)
            if (ok) {
                supabaseFolders = supabaseFolders.map {
                    when {
                        it.id == id -> it.copy(name = newPath)
                        it.name.startsWith(oldPath + "/") -> it.copy(name = newPath + it.name.substring(oldPath.length))
                        else -> it
                    }
                }
                // Update semua note dengan main_category berprefix oldPath
                val affected = supabaseNotes.filter { it.mainCategory == oldPath || it.mainCategory.startsWith(oldPath + "/") }
                val updates = mutableListOf<SupabaseNote>()
                for (n in affected) {
                    val newMc = if (n.mainCategory == oldPath) newPath else newPath + n.mainCategory.substring(oldPath.length)
                    val upOk = SupabaseClient.updateNote(n.id, null, null, null, newMc)
                    if (upOk) updates.add(n.copy(mainCategory = newMc))
                }
                if (updates.isNotEmpty()) {
                    val map = updates.associateBy { it.id }
                    supabaseNotes = supabaseNotes.map { map[it.id] ?: it }
                }
                if (isSupabaseMode) visibleNodes = buildSupabaseFileNodes()
                notify("Folder renamed")
            } else notify("Rename failed")
            supabaseLoading = false
        }
    }

    fun deleteSupabaseFolder(id: Long, name: String) {
        val oldFolder = supabaseFolders.firstOrNull { it.id == id }
        val oldPath = oldFolder?.name ?: name
        scope.launch {
            supabaseLoading = true
            // Hapus note dulu (yang prefix-nya match)
            val affectedNotes = supabaseNotes.filter { it.mainCategory == oldPath || it.mainCategory.startsWith(oldPath + "/") }
            val deletedIds = mutableListOf<Long>()
            for (n in affectedNotes) {
                if (SupabaseClient.deleteNote(n.id)) deletedIds.add(n.id)
            }
            if (deletedIds.isNotEmpty()) {
                supabaseNotes = supabaseNotes.filterNot { it.id in deletedIds }
                // Tutup tab-tab supabase note yang terhapus
                val toClose = tabs.filter { t ->
                    val s = t.uri?.toString() ?: return@filter false
                    s.startsWith("supabase://note/") && s.substringAfterLast("/").toLongOrNull() in deletedIds
                }
                if (toClose.isNotEmpty()) {
                    val activeRemoved = toClose.any { it.id == activeTabId }
                    val firstIdx = tabs.indexOfFirst { t -> toClose.any { it.id == t.id } }
                    tabs.removeAll { t -> toClose.any { it.id == t.id } }
                    if (activeRemoved) switchAfterRemoval(firstIdx.coerceAtLeast(0))
                    persistTabs()
                }
            }
            // Hapus child folders (yang prefix-nya match)
            val childFolderIds = supabaseFolders.filter { it.id != id && it.name.startsWith(oldPath + "/") }.map { it.id }
            for (cid in childFolderIds) SupabaseClient.deleteFolder(cid)
            val ok = SupabaseClient.deleteFolder(id)
            if (ok) {
                supabaseFolders = supabaseFolders.filterNot { it.id == id || it.id in childFolderIds }
                if (isSupabaseMode) visibleNodes = buildSupabaseFileNodes()
                notify("Folder deleted")
            } else notify("Delete folder failed")
            supabaseLoading = false
        }
    }

    fun deleteTabFromStorage(tab: EditorTab) {
        scope.launch {
            var deletedNoteId: Long? = null
            withContext(Dispatchers.IO) {
                tab.uri?.let { u ->
                    when (u.scheme) {
                        "file" -> u.path?.let { pth -> runCatching { File(pth).delete() } }
                        "supabase" -> {
                            val nid = SupabaseClient.noteIdFromUri(u)
                            if (nid != null) {
                                SupabaseClient.deleteNote(nid)
                                deletedNoteId = nid
                            }
                        }
                        else -> runCatching { DocumentFile.fromSingleUri(appContext, u)?.delete() }
                    }
                }
            }
            deletedNoteId?.let { nid -> supabaseNotes = supabaseNotes.filterNot { it.id == nid } }
            val idx = tabs.indexOfFirst { it.id == tab.id }
            tabs.removeAll { it.id == tab.id }
            if (activeTabId == tab.id) switchAfterRemoval(idx)
            persistTabs()
            folderCache.clear()
            if (isSupabaseMode) visibleNodes = buildSupabaseFileNodes() else rebuildVisibleNodes()
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
                notify("Restore succeeded")
            } catch (e: Exception) {
                notify("Restore failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun gitInit() {
        scope.launch {
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git features."); return@launch }
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
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git features."); return@launch }
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
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git features."); return@launch }
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
                supabaseProjectActive = false
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
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git features."); return@launch }
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
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git features."); return@launch }
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
            if (rootFolderPath == null) { notify("This project has no filesystem path; use Open from App Storage for Git features."); return@launch }
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
}