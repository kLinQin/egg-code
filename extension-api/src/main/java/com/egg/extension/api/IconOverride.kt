package com.egg.extension.api

import androidx.annotation.Keep

@Keep
data class IconOverride(
    val ext: String,
    val drawableFile: String?,
    val color: String?,
    val extensionId: String = ""
)
