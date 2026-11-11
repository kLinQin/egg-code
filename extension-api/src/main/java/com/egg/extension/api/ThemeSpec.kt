package com.egg.extension.api

import androidx.annotation.Keep

@Keep
data class ThemeSpec(
    val id: String,
    val name: String,
    val base: String,
    val colors: Map<String, String>,
    val syntaxPath: String? = null,
    val dark: Boolean = true,
    val extensionId: String = ""
)
