package com.egg.example

import android.util.Log
import com.egg.extension.api.ExtensionAPI
import com.egg.extension.api.ExtensionContext

class Main(context: ExtensionContext) : ExtensionAPI(context) {
    override fun onLoad() {
        Log.d("EggExampleExt", "onLoad id=${context.extensionId}")
    }

    override fun onDispose() {
        Log.d("EggExampleExt", "onDispose")
    }
}
