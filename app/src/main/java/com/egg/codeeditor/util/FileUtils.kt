package com.egg.codeeditor.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.egg.codeeditor.model.GitStatusInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git

data class GitCommitInfo(
    val id: String,
    val shortId: String,
    val message: String,
    val author: String,
    val email: String,
    val time: Long
)

suspend fun readGitStatus(git: Git): GitStatusInfo = withContext(Dispatchers.IO) {
    val s = git.status().call()
    val staged = runCatching {
        git.diff().setCached(true).call().map { it.newPath ?: it.oldPath }
    }.getOrDefault(emptyList())
    val unstaged = runCatching {
        git.diff().call().map { it.newPath ?: it.oldPath }
    }.getOrDefault(emptyList())
    GitStatusInfo(
        added = s.added.toList(),
        modified = s.modified.toList(),
        untracked = s.untracked.toList(),
        removed = s.removed.toList(),
        missing = s.missing.toList(),
        staged = staged,
        unstaged = unstaged
    )
}

suspend fun readGitLog(git: Git): List<GitCommitInfo> = withContext(Dispatchers.IO) {
    runCatching {
        git.log().call().map { rev ->
            GitCommitInfo(
                id = rev.name,
                shortId = rev.name.take(7),
                message = rev.shortMessage,
                author = rev.authorIdent?.name ?: "",
                email = rev.authorIdent?.emailAddress ?: "",
                time = rev.commitTime * 1000L
            )
        }
    }.getOrDefault(emptyList())
}

fun getGitRemoteUrl(git: Git): String? {
    return runCatching {
        git.repository.config.getString("remote", "origin", "url")
    }.getOrNull()
}

suspend fun readFileContent(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                uri.path?.let { java.io.File(it).readText() } ?: ""
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

suspend fun writeFileContent(context: Context, uri: Uri, content: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                val f = uri.path?.let { java.io.File(it) } ?: return@withContext false
                f.writeText(content)
                true
            } else {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val stream = context.contentResolver.openOutputStream(uri, "rwt")
                    ?: context.contentResolver.openOutputStream(uri, "wt")
                    ?: return@withContext false
                stream.use { it.write(bytes); it.flush() }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var name = "Unknown"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) name = cursor.getString(nameIndex)
        }
    }
    return name
}

fun getPathFromTreeUri(treeUri: Uri): String? {
    return try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        val split = docId.split(":")
        if (split.size >= 2 && split[0] == "primary") {
            val subPath = split[1]
            val base = android.os.Environment.getExternalStorageDirectory().absolutePath
            if (subPath.isEmpty()) base else "$base/$subPath"
        } else if (split.size >= 2) "/storage/${split[0]}/${split[1]}" else null
    } catch (e: Exception) { null }
}

fun findOrCreateDocumentFile(context: Context, treeUri: Uri, relPath: String): DocumentFile? {
    var current = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    val parts = relPath.split("/").filter { it.isNotBlank() }
    parts.forEachIndexed { index, part ->
        val isLast = index == parts.lastIndex
        current = if (isLast) {
            current.findFile(part) ?: current.createFile("application/octet-stream", part)
        } else {
            val dir = current.findFile(part)
            if (dir == null || !dir.isDirectory) current.createDirectory(part) else dir
        } ?: return null
    }
    return current
}

fun isErrorMessage(msg: String): Boolean {
    val m = msg.lowercase()
    return m.contains("failed") || m.contains("error") || m.contains("cannot") ||
           m.contains("not found") || m.contains("exception")
}
