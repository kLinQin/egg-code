package com.egg.plugin.typescript

import android.util.Log
import com.egg.extension.api.ExtensionAPI
import com.egg.extension.api.ExtensionContext

class Main(ctx: ExtensionContext) : ExtensionAPI(ctx) {
    override fun onLoad() {
        Log.d("TsGrammar", "onLoad")
    }
}
