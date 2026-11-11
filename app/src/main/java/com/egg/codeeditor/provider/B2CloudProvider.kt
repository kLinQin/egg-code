package com.egg.codeeditor.provider

import android.content.Context
import com.egg.codeeditor.util.B2Account
import com.egg.codeeditor.util.B2Bucket
import com.egg.codeeditor.util.b2Authorize
import com.egg.codeeditor.util.b2DeleteFileVersion
import com.egg.codeeditor.util.b2DownloadBytes
import com.egg.codeeditor.util.b2FindBucket
import com.egg.codeeditor.util.b2ListFileVersions
import com.egg.codeeditor.util.b2ListFiles
import com.egg.codeeditor.util.b2UploadFile
import com.egg.codeeditor.util.loadB2Credentials
import com.egg.extension.api.CloudFile
import com.egg.extension.api.CloudProvider
import com.egg.extension.api.CloudStatus
import java.io.File

class B2CloudProvider(private val appContext: Context) : CloudProvider {
    override val id: String = "com.egg.b2"
    override val displayName: String = "Backblaze B2"
    override val requiresConfig: Boolean = true

    private var account: B2Account? = null
    private var bucket: B2Bucket? = null
    private var lastMessage: String? = null

    override fun status(): CloudStatus = CloudStatus(
        connected = account != null && bucket != null,
        accountName = bucket?.bucketName,
        message = lastMessage
    )

    override suspend fun connect(config: Map<String, String>): Result<CloudStatus> = runCatching {
        val keyId = config["keyId"] ?: error("keyId required")
        val appKey = config["appKey"] ?: error("appKey required")
        val bucketName = config["bucket"] ?: error("bucket required")
        val acc = b2Authorize(keyId, appKey)
        val b = when {
            acc.allowedBucketId != null && acc.allowedBucketName != null ->
                B2Bucket(acc.allowedBucketId!!, acc.allowedBucketName!!)
            else -> b2FindBucket(acc, bucketName) ?: error("Bucket '$bucketName' not found")
        }
        account = acc
        bucket = b
        lastMessage = "Connected to '$bucketName'"
        status()
    }

    override suspend fun disconnect(): Result<Unit> = runCatching {
        account = null
        bucket = null
        lastMessage = null
    }

    override suspend fun list(prefix: String): Result<List<CloudFile>> = runCatching {
        val acc = account ?: error("Not connected")
        val b = bucket ?: error("Not connected")
        b2ListFiles(acc, b.bucketId).map { CloudFile(path = it.fileName, size = it.contentLength.toLong(), lastModified = it.uploadTimestamp) }
    }

    override suspend fun push(local: File, remotePath: String): Result<Unit> = runCatching {
        val acc = account ?: error("Not connected")
        val b = bucket ?: error("Not connected")
        b2UploadFile(acc, b.bucketId, remotePath, local)
    }

    override suspend fun pull(remotePath: String, local: File): Result<Unit> = runCatching {
        val acc = account ?: error("Not connected")
        val b = bucket ?: error("Not connected")
        val data = b2DownloadBytes(acc, b.bucketName, remotePath)
        local.parentFile?.mkdirs()
        local.writeBytes(data)
    }

    override suspend fun delete(remotePath: String): Result<Unit> = runCatching {
        val acc = account ?: error("Not connected")
        val b = bucket ?: error("Not connected")
        val versions = b2ListFileVersions(acc, b.bucketId)
        val info = versions.firstOrNull { it.fileName == remotePath && it.fileId.isNotEmpty() }
            ?: error("File not found: $remotePath")
        b2DeleteFileVersion(acc, remotePath, info.fileId)
    }

    // Helper untuk ViewModel yang masih pakai B2Account/B2Bucket langsung
    fun currentAccount(): B2Account? = account
    fun currentBucket(): B2Bucket? = bucket
    fun setConnection(acc: B2Account, b: B2Bucket) { account = acc; bucket = b; lastMessage = "Connected to '${b.bucketName}'" }

    companion object {
        fun create(ctx: Context): B2CloudProvider = B2CloudProvider(ctx)
    }
}
