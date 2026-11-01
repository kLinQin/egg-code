package com.egg.codeeditor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import com.egg.codeeditor.model.EditorSettings
import com.egg.codeeditor.model.EditorTab
import com.egg.codeeditor.model.FileNode
import com.egg.codeeditor.model.GitStatusInfo
import com.egg.codeeditor.util.B2Account
import com.egg.codeeditor.util.B2Bucket
import com.egg.codeeditor.util.B2FileInfo
import com.egg.codeeditor.util.CloudChange
import com.egg.codeeditor.util.GitCommitInfo
import com.egg.codeeditor.util.b2Authorize
import com.egg.codeeditor.util.b2DownloadBytes
import com.egg.codeeditor.util.b2FindBucket
import com.egg.codeeditor.util.b2ListFileVersions
import com.egg.codeeditor.util.b2ListFiles
import com.egg.codeeditor.util.b2UploadFile
import com.egg.codeeditor.util.computeCloudChanges
import com.egg.codeeditor.util.findOrCreateDocumentFile
import com.egg.codeeditor.util.getGitRemoteUrl
import com.egg.codeeditor.util.getPathFromTreeUri
import com.egg.codeeditor.util.loadSettings
import com.egg.codeeditor.util.readFileContent
import com.egg.codeeditor.util.readGitLog
import com.egg.codeeditor.util.readGitStatus
import com.egg.codeeditor.util.saveSettings
import com.egg.codeeditor.util.writeFileContent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * Memegang SEMUA state aplikasi + aksi bisnis yang sebelumnya ada di dalam EggApp().
 * Dibuat via remember { EggViewModel(context.applicationContext) } — tanpa dependency lifecycle tambahan.
 */
class EggViewModel(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ── Terminal ──
    var terminalVisible by mutableStateOf(false)

    // ── Navigasi panel ──
    var currentSection by mutableStateOf("File Manager")

    // ── File manager ──
    var rootFolderUri by mutableStateOf<Uri?>(null)
    var rootFolderPath by mutableStateOf<String?>(null)
    var visibleNodes by mutableStateOf<List<FileNode>>(emptyList())
    var expandedPaths by mutableStateOf<Set<String>>(emptySet())
    val folderCache = mutableStateMapOf<String, List<FileNode>>()

    var showNewFileDialog by mutableStateOf(false)
    var showNewFolderDialog by mutableStateOf(false)
    var showRenameDialog by mutableStateOf(false)
    var showDeleteDialog by mutableStateOf(false)
    var selectedNode by mutableStateOf<FileNode?>(null)
    var targetParentNode by mutableStateOf<FileNode?>(null)

    // ── Git ──
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

    // ── Cloud (B2) ──
    var b2Account by mutableStateOf<B2Account?>(null)
    var b2Bucket by mutableStateOf<B2Bucket?>(null)
    var cloudChanges by mutableStateOf<List<CloudChange>>(emptyList())
    var cloudHistory by mutableStateOf<List<B2FileInfo>>(emptyList())
    var cloudBusy by mutableStateOf(false)
    var cloudMessage by mutableStateOf<String?>(null)
    var showCloudConnectDialog by mutableStateOf(false)

    // ── Settings & dialog umum ──
    var settings by mutableStateOf(loadSettings(appContext))
    var showSettings by mutableStateOf(false)
    var showAboutDialog by mutableStateOf(false)
    var showExitConfirm by mutableStateOf(false)

    // ── Tabs editor ──
    val tabs = mutableStateListOf<EditorTab>()
    var activeTabId by mutableStateOf<Long?>(null)
    var nextTabId by mutableStateOf(1L)
    var pendingCloseTab by mutableStateOf<EditorTab?>(null)
    var pendingDeleteTab by mutableStateOf<EditorTab?>(null)
    var pendingCloseAfterSaveId by mutableStateOf<Long?>(null)
    var pendingSaveAsTabId by mutableStateOf<Long?>(null)

    var showFindBar by mutableStateOf(false)

    val activeTab: EditorTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    // ── Settings ──
    fun updateSettings(newSettings: EditorSettings) {
        settings = newSettings
        saveSettings(appContext, newSettings)
    }

    // ── File tree ──
    suspend fun getChildren(uri: Uri, depth: Int): List<FileNode> {
        val path = uri.toString()
        folderCache[path]?.let { return it }
        val nodes = withContext(Dispatchers.IO) {
            try {
                val doc = DocumentFile.fromTreeUri(appContext, uri)
                val files = doc?.listFiles() ?: emptyArray()
                files.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
                    .map { d -> FileNode(d.name ?: "Unknown", d.isDirectory, d.uri, depth) }
            } catch (_: Exception) { emptyList() }
        }
        folderCache[path] = nodes
        return nodes
    }

    suspend fun rebuildVisibleNodes() {
        val rootUri = rootFolderUri ?: return
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

    fun openFolder(uri: Uri) {
        scope.launch {
            rootFolderUri = uri
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            rootFolderPath = getPathFromTreeUri(uri)
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
        }
    }

    fun closeProject() {
        rootFolderUri = null; rootFolderPath = null; visibleNodes = emptyList()
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

    fun createFile(name: String) {
        scope.launch {
            parentDoc()?.createFile("text/plain", name)
            folderCache.clear()
            rebuildVisibleNodes()
        }
    }

    fun createFolder(name: String) {
        scope.launch {
            parentDoc()?.createDirectory(name)
            folderCache.clear()
            rebuildVisibleNodes()
        }
    }

    fun rename(newName: String) {
        scope.launch {
            selectedNode?.let { DocumentFile.fromSingleUri(appContext, it.uri)?.renameTo(newName) }
            folderCache.clear()
            rebuildVisibleNodes()
        }
    }

    fun deleteSelected() {
        scope.launch {
            selectedNode?.let { DocumentFile.fromSingleUri(appContext, it.uri)?.delete() }
            folderCache.clear()
            rebuildVisibleNodes()
        }
    }

    private fun parentDoc(): DocumentFile? =
        targetParentNode?.let { DocumentFile.fromTreeUri(appContext, it.uri) }
            ?: rootFolderUri?.let { DocumentFile.fromTreeUri(appContext, it) }

    // ── Tabs ──
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
    }

    fun requestCloseTab(tab: EditorTab) {
        if (tab.isDirty) pendingCloseTab = tab else reallyCloseTab(tab.id)
    }

    fun saveTabAndClose(tab: EditorTab) {
        val i = tabs.indexOfFirst { it.id == tab.id }
        if (i < 0) return
        val t = tabs[i]
        if (t.uri != null) {
            scope.launch {
                writeFileContent(appContext, t.uri, t.content)
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
    }

    fun newUntitledTab() {
        val count = tabs.count { it.uri == null } + 1
        val t = EditorTab(nextTabId++, "Untitled $count", null, "")
        tabs.add(t)
        activeTabId = t.id
    }

    fun deleteTabFromStorage(tab: EditorTab) {
        scope.launch {
            tab.uri?.let { runCatching { DocumentFile.fromSingleUri(appContext, it)?.delete() } }
            val idx = tabs.indexOfFirst { it.id == tab.id }
            tabs.removeAll { it.id == tab.id }
            if (activeTabId == tab.id) switchAfterRemoval(idx)
            folderCache.clear()
            rebuildVisibleNodes()
        }
    }

    // ── Backup / Restore ──
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
            } catch (e: Exception) {
                e.printStackTrace()
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Git actions ──
    fun gitInit() {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val newGit = withContext(Dispatchers.IO) { Git.init().setDirectory(File(rootFolderPath!!)).call() }
                gitRepo = newGit
                gitStatus = readGitStatus(newGit)
                gitCommits = readGitLog(newGit)
                gitRemoteUrl = getGitRemoteUrl(newGit)
                gitMessage = "Repository initialized successfully."
            } catch (e: Exception) { gitMessage = "Init failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    fun gitRefresh() {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: run {
                    withContext(Dispatchers.IO) { Git.open(File(rootFolderPath!!)) }.also { gitRepo = it }
                }
                gitStatus = readGitStatus(g)
                gitCommits = readGitLog(g)
                gitRemoteUrl = getGitRemoteUrl(g)
            } catch (e: Exception) { gitMessage = "Failed to read status: ${e.message}" } finally { gitBusy = false }
        }
    }

    fun gitCommit() {
        scope.launch {
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
                gitMessage = "Commit succeeded."
            } catch (e: Exception) { gitMessage = "Commit failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    fun gitDoClone(url: String, user: String, token: String) {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val dest = withContext(Dispatchers.IO) {
                    val repoName = url.trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "cloned-repo" }
                    File(rootFolderPath!!, repoName)
                }
                val cp = if (user.isNotBlank()) UsernamePasswordCredentialsProvider(user, token) else null
                val cloned = withContext(Dispatchers.IO) {
                    Git.cloneRepository()
                        .setURI(url)
                        .setDirectory(dest)
                        .apply { if (cp != null) setCredentialsProvider(cp) }
                        .call()
                }
                gitRepo = cloned
                gitStatus = readGitStatus(cloned)
                gitCommits = readGitLog(cloned)
                gitRemoteUrl = getGitRemoteUrl(cloned)
                gitUser = user; gitToken = token
                gitMessage = "Clone succeeded: ${dest.absolutePath}"
            } catch (e: Exception) { gitMessage = "Clone failed: ${e.message}" } finally { gitBusy = false }
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
            } catch (e: Exception) { gitMessage = "Add remote failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    fun gitFetch() {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                withContext(Dispatchers.IO) {
                    val cmd = g.fetch()
                    if (gitUser.isNotBlank() && gitToken.isNotBlank()) cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(gitUser, gitToken))
                    cmd.call()
                }
                gitMessage = "Fetch succeeded."
            } catch (e: Exception) { gitMessage = "Fetch failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    fun gitPull() {
        scope.launch {
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
            } catch (e: Exception) { gitMessage = "Pull failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    fun gitPush() {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                val result = withContext(Dispatchers.IO) {
                    val cmd = g.push()
                    if (gitUser.isNotBlank() && gitToken.isNotBlank()) cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(gitUser, gitToken))
                    cmd.call()
                }
                gitMessage = if (result.iterator().hasNext()) "Push succeeded." else "Push failed: nothing pushed."
            } catch (e: Exception) { gitMessage = "Push failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    // ── Cloud (B2) actions ──
    fun cloudConnect(keyId: String, appKey: String, bucket: String) {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Authorize(keyId, appKey)
                val bucketInfo = when {
                    acc.allowedBucketId != null && acc.allowedBucketName != null ->
                        B2Bucket(acc.allowedBucketId!!, acc.allowedBucketName!!)
                    else -> b2FindBucket(acc, bucket) ?: throw Exception("Bucket '$bucket' not found")
                }
                b2Account = acc; b2Bucket = bucketInfo
                val remote = b2ListFiles(acc, bucketInfo.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remote)
                cloudHistory = b2ListFileVersions(acc, bucketInfo.bucketId)
                cloudMessage = "Connected to bucket '${bucketInfo.bucketName}'."
            } catch (e: Exception) {
                Log.e("EggB2", "connect failed", e)
                cloudMessage = "Connect failed:\n${e.stackTraceToString().take(1500)}"
            } finally { cloudBusy = false }
        }
    }

    fun cloudFetch() {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
                cloudMessage = "Fetch succeeded."
            } catch (e: Exception) { cloudMessage = "Fetch failed: ${e.message}" } finally { cloudBusy = false }
        }
    }

    fun cloudRefresh() {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val remote = b2ListFiles(acc, bucket.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remote)
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
            } catch (e: Exception) { cloudMessage = "Failed to read status: ${e.message}" } finally { cloudBusy = false }
        }
    }

    fun cloudPush() {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val remote = b2ListFiles(acc, bucket.bucketId)
                val changes = computeCloudChanges(File(rootFolderPath!!), remote)
                var uploaded = 0
                for (c in changes) {
                    val relPath = when (c) {
                        is CloudChange.New -> c.relPath
                        is CloudChange.Modified -> c.relPath
                        is CloudChange.RemoteOnly -> null
                    } ?: continue
                    b2UploadFile(acc, bucket.bucketId, relPath, File(rootFolderPath!!, relPath))
                    uploaded++
                }
                val remoteAfter = b2ListFiles(acc, bucket.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remoteAfter)
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
                cloudMessage = if (uploaded > 0) "Push succeeded: $uploaded file(s) uploaded." else "Push succeeded: nothing to upload."
            } catch (e: Exception) { cloudMessage = "Push failed: ${e.message}" } finally { cloudBusy = false }
        }
    }

    fun cloudPull() {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val remote = b2ListFiles(acc, bucket.bucketId)
                val changes = computeCloudChanges(File(rootFolderPath!!), remote)
                var downloaded = 0
                for (c in changes) {
                    if (c !is CloudChange.RemoteOnly) continue
                    val data = b2DownloadBytes(acc, bucket.bucketName, c.relPath)
                    val target = findOrCreateDocumentFile(appContext, rootFolderUri!!, c.relPath)
                        ?: throw Exception("Cannot create local file: ${c.relPath}")
                    appContext.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(data) }
                        ?: throw Exception("Cannot open output stream: ${c.relPath}")
                    downloaded++
                }
                val remoteAfter = b2ListFiles(acc, bucket.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remoteAfter)
                cloudMessage = if (downloaded > 0) "Pull succeeded: $downloaded file(s) downloaded." else "Pull succeeded: nothing to pull."
            } catch (e: Exception) { cloudMessage = "Pull failed: ${e.message}" } finally { cloudBusy = false }
        }
    }
}