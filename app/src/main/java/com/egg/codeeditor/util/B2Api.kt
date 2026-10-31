package com.egg.codeeditor.util

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class B2Account(
    val authToken: String,
    val apiUrl: String,
    val downloadUrl: String,
    val accountId: String,
    val allowedBucketId: String? = null,
    val allowedBucketName: String? = null
)

data class B2Bucket(
    val bucketId: String,
    val bucketName: String
)

data class B2FileInfo(
    val fileName: String,
    val contentLength: Long,
    val contentSha1: String,
    val uploadTimestamp: Long,
    val action: String
)

/** Entries shown in the Cloud Changes tab. */
sealed class CloudChange {
    data class New(val relPath: String) : CloudChange()
    data class Modified(val relPath: String) : CloudChange()
    data class RemoteOnly(val relPath: String) : CloudChange()
}

private const val B2_AUTHORIZE_URL = "https://api.backblazeb2.com/b2api/v2/b2_authorize_account"

private fun base64Basic(user: String, pass: String): String {
    return "Basic " + Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}

private fun httpJson(
    url: String,
    method: String,
    jsonBody: String? = null,
    authToken: String? = null,
    headers: Map<String, String> = emptyMap()
): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        authToken?.let { conn.setRequestProperty("Authorization", it) }
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (jsonBody != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw Exception("HTTP $code body=$body")
        }
        return JSONObject(body)
    } finally {
        conn.disconnect()
    }
}

suspend fun b2Authorize(keyId: String, appKey: String): B2Account = withContext(Dispatchers.IO) {
    val json = httpJson(B2_AUTHORIZE_URL, "GET", authToken = base64Basic(keyId, appKey))
    val apiUrl = json.optString("apiUrl")
    val authToken = json.optString("authorizationToken")
    val downloadUrl = json.optString("downloadUrl")
    val accountId = json.optString("accountId")
    if (apiUrl.isEmpty() || authToken.isEmpty()) {
        throw Exception("Authorize response incomplete: $json")
    }
    val allowed = json.optJSONObject("allowed")
    val bucketId = allowed?.optString("bucketId", "")?.takeIf { it.isNotEmpty() }
    val bucketName = allowed?.optString("bucketName", "")?.takeIf { it.isNotEmpty() }
    B2Account(
        authToken = authToken,
        apiUrl = apiUrl,
        downloadUrl = downloadUrl,
        accountId = accountId,
        allowedBucketId = bucketId,
        allowedBucketName = bucketName
    )
}

suspend fun b2FindBucket(acc: B2Account, bucketName: String): B2Bucket? = withContext(Dispatchers.IO) {
    val body = JSONObject().put("accountId", acc.accountId).toString()
    val json = httpJson(
        "${acc.apiUrl}/b2api/v2/b2_list_buckets",
        "POST",
        jsonBody = body,
        authToken = acc.authToken
    )
    val buckets = json.getJSONArray("buckets")
    for (i in 0 until buckets.length()) {
        val b = buckets.getJSONObject(i)
        if (b.getString("bucketName") == bucketName) {
            return@withContext B2Bucket(b.getString("bucketId"), b.getString("bucketName"))
        }
    }
    null
}

suspend fun b2ListFiles(acc: B2Account, bucketId: String): List<B2FileInfo> = withContext(Dispatchers.IO) {
    val body = JSONObject().put("bucketId", bucketId).put("maxFileCount", 10000).toString()
    val json = httpJson(
        "${acc.apiUrl}/b2api/v2/b2_list_file_names",
        "POST",
        jsonBody = body,
        authToken = acc.authToken
    )
    parseFiles(json.getJSONArray("files"))
}

suspend fun b2ListFileVersions(acc: B2Account, bucketId: String): List<B2FileInfo> = withContext(Dispatchers.IO) {
    val body = JSONObject().put("bucketId", bucketId).put("maxFileCount", 10000).toString()
    val json = httpJson(
        "${acc.apiUrl}/b2api/v2/b2_list_file_versions",
        "POST",
        jsonBody = body,
        authToken = acc.authToken
    )
    parseFiles(json.getJSONArray("files"))
}

private fun parseFiles(arr: JSONArray): List<B2FileInfo> {
    val out = mutableListOf<B2FileInfo>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out.add(
            B2FileInfo(
                fileName = o.getString("fileName"),
                contentLength = o.getLong("contentLength"),
                contentSha1 = o.optString("contentSha1", ""),
                uploadTimestamp = o.optLong("uploadTimestamp", 0L),
                action = o.optString("action", "upload")
            )
        )
    }
    return out
}

suspend fun b2UploadFile(acc: B2Account, bucketId: String, objectName: String, file: File): Unit = withContext(Dispatchers.IO) {
    val urlInfo = httpJson(
        "${acc.apiUrl}/b2api/v2/b2_get_upload_url",
        "POST",
        jsonBody = JSONObject().put("bucketId", bucketId).toString(),
        authToken = acc.authToken
    )
    val uploadUrl = urlInfo.getString("uploadUrl")
    val uploadAuthToken = urlInfo.getString("authorizationToken")
    val bytes = file.readBytes()
    val sha1 = sha1Hex(bytes)
    val conn = URL(uploadUrl).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20000
        conn.readTimeout = 120000
        conn.setRequestProperty("Authorization", uploadAuthToken)
        conn.setRequestProperty("X-Bz-File-Name", encodeObjectName(objectName))
        conn.setRequestProperty("X-Bz-Content-Sha1", sha1)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty("Content-Length", bytes.size.toString())
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            throw Exception(err)
        }
    } finally {
        conn.disconnect()
    }
}

/** Download a file from B2 and return its raw bytes (no direct File writes — avoids EPERM). */
suspend fun b2DownloadBytes(acc: B2Account, bucketName: String, objectName: String): ByteArray = withContext(Dispatchers.IO) {
    val url = "${acc.downloadUrl}/file/${encodeObjectName(bucketName)}/${encodeObjectName(objectName)}"
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 20000
        conn.readTimeout = 120000
        conn.setRequestProperty("Authorization", acc.authToken)
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            throw Exception(err)
        }
        conn.inputStream.use { it.readBytes() }
    } finally {
        conn.disconnect()
    }
}

fun sha1Hex(data: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(data)
    return digest.joinToString("") { "%02x".format(it) }
}

/** B2 file names keep '/' as path separators; URL-encode everything else. */
private fun encodeObjectName(name: String): String {
    return buildString {
        for (ch in name) {
            when {
                ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '/' -> append(ch)
                ch == ' ' -> append("%20")
                else -> {
                    for (b in ch.toString().toByteArray(Charsets.UTF_8)) append("%").append("%02X".format(b))
                }
            }
        }
    }
}

/** Walk a directory tree, returning (relativePath, file). Skips .git. */
suspend fun listLocalFiles(root: File): List<Pair<String, File>> = withContext(Dispatchers.IO) {
    val out = mutableListOf<Pair<String, File>>()
    fun walk(dir: File, prefix: String) {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                if (f.name != ".git") walk(f, "$prefix${f.name}/")
            } else {
                out.add(prefix + f.name to f)
            }
        }
    }
    if (root.isDirectory) walk(root, "")
    out
}

/** Compare local files with the latest remote listing (from b2_list_file_names). */
suspend fun computeCloudChanges(root: File, remote: List<B2FileInfo>): List<CloudChange> = withContext(Dispatchers.IO) {
    val remoteByName = remote.associateBy { it.fileName }
    val localFiles = listLocalFiles(root)
    val changes = mutableListOf<CloudChange>()
    val seen = HashSet<String>()
    for ((relPath, f) in localFiles) {
        seen.add(relPath)
        val r = remoteByName[relPath]
        if (r == null) {
            changes.add(CloudChange.New(relPath))
        } else {
            val localSha = runCatching { sha1Hex(f.readBytes()) }.getOrNull()
            if (localSha == null || localSha != r.contentSha1) {
                changes.add(CloudChange.Modified(relPath))
            }
        }
    }
    for (r in remote) {
        if (r.fileName !in seen && r.action == "upload" && !r.fileName.endsWith("/")) {
            changes.add(CloudChange.RemoteOnly(r.fileName))
        }
    }
    changes
}