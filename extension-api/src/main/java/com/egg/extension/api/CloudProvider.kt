package com.egg.extension.api

import androidx.annotation.Keep
import java.io.File

@Keep
data class CloudFile(
    val path: String,
    val size: Long,
    val lastModified: Long
)

@Keep
data class CloudStatus(
    val connected: Boolean,
    val accountName: String? = null,
    val message: String? = null
)

@Keep
interface CloudProvider {
    val id: String
    val displayName: String
    val requiresConfig: Boolean get() = true

    suspend fun connect(config: Map<String, String>): Result<CloudStatus>
    suspend fun disconnect(): Result<Unit>
    fun status(): CloudStatus
    suspend fun list(prefix: String): Result<List<CloudFile>>
    suspend fun push(local: File, remotePath: String): Result<Unit>
    suspend fun pull(remotePath: String, local: File): Result<Unit>
    suspend fun delete(remotePath: String): Result<Unit>
}
