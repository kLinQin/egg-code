package com.egg.codeeditor

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.egg.codeeditor.ui.EggApp
import com.egg.codeeditor.terminal.EggTerminalEnv
import com.egg.codeeditor.extension.ExtensionManager
import com.egg.codeeditor.ui.editor.getOrCreateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

private val ALL_SCOPES = listOf(
    "source.kotlin", "source.java", "source.js", "source.ts", "source.json",
    "text.html.basic", "source.css", "source.python", "text.html.markdown",
    "text.xml", "source.yaml"
)

private fun initTextMate(context: Context, darkTheme: Boolean) {
    try {
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
        val themeRegistry = ThemeRegistry.getInstance()
        themeRegistry.loadTheme(
            ThemeModel(
                IThemeSource.fromInputStream(
                    FileProviderRegistry.getInstance().tryGetInputStream("darcula.json"),
                    "darcula.json", null
                ),
                "darcula"
            ).apply { isDark = true }
        )
        themeRegistry.loadTheme(
            ThemeModel(
                IThemeSource.fromInputStream(
                    FileProviderRegistry.getInstance().tryGetInputStream("github-light.json"),
                    "github-light.json", null
                ),
                "github-light"
            ).apply { isDark = false }
        )
        themeRegistry.setTheme(if (darkTheme) "darcula" else "github-light")
        GrammarRegistry.getInstance().loadGrammars("language.json")

        ALL_SCOPES.forEach { scope ->
            try { getOrCreateLanguage(scope) } catch (_: Exception) {}
        }
    } catch (_: Exception) {
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        EggTerminalEnv.init(application)
        val prefs = getSharedPreferences("egg_settings", MODE_PRIVATE)
        val darkTheme = prefs.getString("theme_name", "github-light") == "darcula"
        setTheme(if (darkTheme) R.style.Theme_Egg_Dark else R.style.Theme_Egg_Light)

        initTextMate(this, darkTheme)
        Thread {
            ExtensionManager.loadAll(applicationContext)
        }.start()

        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setContent {
            EggApp()
        }
    }
}