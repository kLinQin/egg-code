package com.egg.extension.api

import androidx.annotation.Keep
import java.io.File

@Keep
data class GrammarSpec(
    val name: String,
    val scopeName: String,
    val grammarFile: File,
    val languageConfigFile: File? = null,
    val extensions: List<String> = emptyList(),
    val extensionId: String = ""
)
