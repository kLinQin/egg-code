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
    GitStatusInfo(s.added.toList(), s.modified.toList(), s.untracked.toList(), s.removed.toList(), s.missing.toList())
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
        try { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: "" } catch (e: Exception) { "Error: ${e.message}" }
    }
}

suspend fun writeFileContent(context: Context, uri: Uri, content: String) {
    withContext(Dispatchers.IO) {
        try { context.contentResolver.openOutputStream(uri, "wt")?.use { it.bufferedWriter().write(content) } } catch (e: Exception) { e.printStackTrace() }
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

/** Create (or find) a file under a SAF tree URI, creating parent folders as needed. */
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