package com.egg.plugin.neonicons

import android.util.Log
import com.egg.extension.api.ExtensionAPI
import com.egg.extension.api.ExtensionContext

class Main(ctx: ExtensionContext) : ExtensionAPI(ctx) {
    override fun onLoad() {
        Log.d("NeonIcons", "onLoad id=${context.extensionId}")
    }
}
