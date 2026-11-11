package com.egg.extension.api

import androidx.annotation.Keep

/**
 * Base class for every Egg extension.
 * A plugin must have a class extending this, referenced from manifest.json "mainClass".
 */
@Keep
abstract class ExtensionAPI(val context: ExtensionContext) {
    open fun onLoad() {}
    open fun onDispose() {}
    open fun onInstalled() {}
    open fun onUninstalled() {}
}
