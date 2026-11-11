package com.egg.codeeditor.provider

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

class EggDocumentsProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "com.egg.codeeditor.documents"
        private const val ROOT_REPOS = "repos"
        private const val ROOT_B2 = "b2"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
        )
    }

    private lateinit var baseDir: File

    override fun onCreate(): Boolean {
        baseDir = File(context!!.filesDir, "")
        return true
    }

    private fun rootDir(rootId: String): File = File(baseDir, rootId).apply { mkdirs() }

    private fun fileForDocId(docId: String): File {
        val parts = docId.split("/", limit = 2)
        val rootId = parts[0]
        if (rootId != ROOT_REPOS && rootId != ROOT_B2) throw FileNotFoundException("Bad root: $docId")
        val rel = if (parts.size > 1) parts[1] else ""
        val root = rootDir(rootId).canonicalFile
        val f = if (rel.isEmpty()) root else File(root, rel).canonicalFile
        if (f != root && !f.path.startsWith(root.path + File.separator)) {
            throw FileNotFoundException("Outside root: $docId")
        }
        return f
    }

    private fun docIdForFile(rootId: String, f: File): String {
        val base = rootDir(rootId)
        val rel = f.absolutePath.removePrefix(base.absolutePath).trimStart('/')
        return if (rel.isEmpty()) rootId else "$rootId/$rel"
    }

    private fun rootIdOf(docId: String): String = docId.substringBefore('/')

    private fun safeName(name: String): String {
        val n = name.trim()
        if (n.isEmpty() || n == "." || n == ".." ||
            n.contains('/') || n.contains('\\') || n.contains('\u0000')
        ) throw FileNotFoundException("Bad name: $name")
        return n
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_ROOT_PROJECTION
        val c = MatrixCursor(cols)
        addRoot(c, ROOT_REPOS, "Egg — Repositories", "Cloned Git repositories")
        addRoot(c, ROOT_B2, "Egg — Backblaze B2", "Pulled B2 buckets")
        return c
    }

    private fun addRoot(c: MatrixCursor, rootId: String, title: String, summary: String) {
        val dir = rootDir(rootId)
        val row = c.newRow()
        for (col in c.columnNames) {
            when (col) {
                Root.COLUMN_ROOT_ID -> row.add(rootId)
                Root.COLUMN_DOCUMENT_ID -> row.add(rootId)
                Root.COLUMN_TITLE -> row.add(title)
                Root.COLUMN_SUMMARY -> row.add(summary)
                Root.COLUMN_ICON -> row.add(android.R.drawable.ic_menu_manage)
                Root.COLUMN_FLAGS -> row.add(
                    Root.FLAG_SUPPORTS_CREATE or
                    Root.FLAG_SUPPORTS_IS_CHILD or
                    Root.FLAG_LOCAL_ONLY
                )
                Root.COLUMN_MIME_TYPES -> row.add("${Document.MIME_TYPE_DIR}|*/*")
                Root.COLUMN_AVAILABLE_BYTES -> row.add(dir.usableSpace)
                else -> row.add(null)
            }
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(c, fileForDocId(documentId), documentId)
        return c
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val c = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = fileForDocId(parentDocumentId)
        val rootId = rootIdOf(parentDocumentId)
        parent.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { includeFile(c, it, docIdForFile(rootId, it)) }
        return c
    }

    private fun includeFile(c: MatrixCursor, f: File, docId: String) {
        val row = c.newRow()
        for (col in c.columnNames) {
            when (col) {
                Document.COLUMN_DOCUMENT_ID -> row.add(docId)
                Document.COLUMN_DISPLAY_NAME -> row.add(f.name)
                Document.COLUMN_MIME_TYPE ->
                    row.add(if (f.isDirectory) Document.MIME_TYPE_DIR else guessMime(f.name))
                Document.COLUMN_LAST_MODIFIED -> row.add(f.lastModified())
                Document.COLUMN_SIZE -> row.add(if (f.isDirectory) null else f.length())
                Document.COLUMN_FLAGS -> {
                    var flags = 0
                    if (f.isDirectory) {
                        flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
                    }
                    if (f.parentFile?.canWrite() == true) {
                        flags = flags or Document.FLAG_SUPPORTS_DELETE or
                                Document.FLAG_SUPPORTS_RENAME or
                                Document.FLAG_SUPPORTS_WRITE
                    }
                    row.add(flags)
                }
                else -> row.add(null)
            }
        }
    }

    private fun guessMime(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "kt","kts","java" -> "text/x-kotlin"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "txt","md","log" -> "text/plain"
            "html","htm" -> "text/html"
            "css" -> "text/css"
            "png" -> "image/png"
            "jpg","jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val f = fileForDocId(documentId)
        if (!f.exists()) throw FileNotFoundException("Missing: $documentId")
        val flags = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(f, flags)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        if (parentDocumentId.isEmpty() || parentDocumentId == documentId) return false
        return try {
            val parent = fileForDocId(parentDocumentId)
            val child = fileForDocId(documentId)
            child.canonicalPath.startsWith(parent.canonicalPath + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileForDocId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Not a dir: $parentDocumentId")
        val name = safeName(displayName)
        val target = File(parent, name)
        val ok = if (mimeType == Document.MIME_TYPE_DIR) target.mkdirs() else target.createNewFile()
        if (!ok && !target.exists()) throw FileNotFoundException("Cannot create $displayName")
        return docIdForFile(rootIdOf(parentDocumentId), target)
    }

    override fun deleteDocument(documentId: String) {
        val f = fileForDocId(documentId)
        if (!f.delete()) throw FileNotFoundException("Cannot delete $documentId")
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val f = fileForDocId(documentId)
        val name = safeName(displayName)
        val target = File(f.parentFile, name)
        if (!f.renameTo(target)) throw FileNotFoundException("Cannot rename $documentId")
        return docIdForFile(rootIdOf(documentId), target)
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?
    ): Cursor {
        val c = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (rootId != ROOT_REPOS && rootId != ROOT_B2) return c
        val root = rootDir(rootId)
        root.walkTopDown().filter { it.name.contains(query, ignoreCase = true) }.forEach {
            if (it != root) includeFile(c, it, docIdForFile(rootId, it))
        }
        return c
    }
}
