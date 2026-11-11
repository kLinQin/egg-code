package com.egg.extension.api

import android.content.Context
import java.io.File

/**
 * Runtime context passed to every extension instance.
 */
class ExtensionContext(
    val appContext: Context,
    val extensionId: String,
    val extensionDir: File
) {
    fun registerTheme(spec: ThemeSpec) {
        ThemeRegistry.register(spec.copy(extensionId = extensionId))
    }
}
