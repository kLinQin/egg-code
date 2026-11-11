package com.egg.extension.api

import androidx.annotation.Keep

@Keep
data class ExtensionManifest(
    val id: String,
    val name: String,
    val mainClass: String,
    val version: String = "1.0.0",
    val description: String = "",
    val author: Author = Author()
) {
    @Keep
    data class Author(val displayName: String = "Unknown")
}
