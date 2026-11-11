package com.egg.codeeditor.ui.editor

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.egg.codeeditor.model.CodeEditorState
import com.egg.codeeditor.model.EditorSettings
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.delay

private val languageCache = HashMap<String, TextMateLanguage?>()

// PLUGIN_GRAMMAR: load grammar from plugin registry if not yet in Sora
private fun ensureGrammarLoaded(scope: String) {
    try {
        if (io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry.getInstance().findGrammar(scope, false) != null) return
        val spec = com.egg.extension.api.GrammarRegistry.get(scope) ?: return
        val source = org.eclipse.tm4e.core.registry.IGrammarSource.fromFile(spec.grammarFile)
        val def = object : io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition {
            override fun getName() = spec.name
            override fun getLanguageConfiguration(): String? = null
            override fun getScopeName() = spec.scopeName
            override fun getGrammar(): org.eclipse.tm4e.core.registry.IGrammarSource = source
        }
        io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry.getInstance().loadGrammar(def)
        android.util.Log.d("EggGrammar", "Sora loaded: " + scope)
    } catch (e: Exception) {
        android.util.Log.e("EggGrammar", "Sora load failed: " + scope, e)
    }
}

internal fun getOrCreateLanguage(scope: String): TextMateLanguage? =
    languageCache.getOrPut(scope) {
        try {
            ensureGrammarLoaded(scope)
            TextMateLanguage.create(scope, true)
        } catch (e: Exception) {
            null
        }
    }

private fun scopeForName(fileName: String): String? {
    // PLUGIN_GRAMMAR: plugin scope takes priority
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext.isNotEmpty()) {
        com.egg.extension.api.GrammarRegistry.scopeForExtension(ext)?.let { return it }
    }
    return builtinScope(fileName)
}

private fun builtinScope(fileName: String): String? = when {
    fileName.endsWith(".kt") -> "source.kotlin"
    fileName.endsWith(".java") -> "source.java"
    fileName.endsWith(".js") -> "source.js"
    fileName.endsWith(".json") -> "source.json"
    fileName.endsWith(".html") || fileName.endsWith(".htm") -> "text.html.basic"
    fileName.endsWith(".css") -> "source.css"
    fileName.endsWith(".py") -> "source.python"
    fileName.endsWith(".md") -> "text.html.markdown"
    else -> null
}

internal fun applyColorScheme(editor: CodeEditor, themeName: String) {
    try {
        // PLUGIN_SYNTAX_THEME: plugin theme can ship its own TextMate theme
        val pluginSpec = com.egg.extension.api.ThemeRegistry.get(themeName)
        val pluginSyntax = pluginSpec?.syntaxPath?.let { java.io.File(it) }?.takeIf { it.exists() }
        if (pluginSyntax != null) {
            try {
                val soraRegistry = io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry.getInstance()
                val themeNameKey = "plugin_${pluginSpec.id}"
                // Load once
                if (runCatching { soraRegistry.setTheme(themeNameKey) }.isFailure) {
                    val model = io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel(
                        org.eclipse.tm4e.core.registry.IThemeSource.fromInputStream(
                            pluginSyntax.inputStream(), pluginSyntax.name, null
                        ),
                        themeNameKey
                    ).apply { isDark = pluginSpec.dark }
                    model.load(null)
                    soraRegistry.loadTheme(model)
                    soraRegistry.setTheme(themeNameKey)
                }
            } catch (e: Exception) {
                // fallback
                val fallback = if (pluginSpec.dark) "darcula" else "github-light"
                io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry.getInstance().setTheme(fallback)
            }
        } else {
            val soraTheme = when {
                themeName == "darcula" || themeName == "github-light" -> themeName
                pluginSpec != null -> if (pluginSpec.dark) "darcula" else "github-light"
                else -> "github-light"
            }
            io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry.getInstance().setTheme(soraTheme)
        }
        val scheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        if (themeName == "darcula") {
            scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB, android.graphics.Color.parseColor("#3D3D3A"))
            scheme.setColor(EditorColorScheme.SCROLL_BAR_TRACK, android.graphics.Color.TRANSPARENT)
        } else {
            scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB, android.graphics.Color.parseColor("#B8B8B8"))
            scheme.setColor(EditorColorScheme.SCROLL_BAR_TRACK, android.graphics.Color.TRANSPARENT)
        }
        editor.colorScheme = scheme
        // PLUGIN_BG_AFTER: apply plugin bg after editor takes ownership
        if (pluginSpec != null) {
            val cs = editor.colorScheme
            val bg = pluginSpec.colors["background"]?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
            val fg = pluginSpec.colors["onBackground"]?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
            if (bg != null) {
                cs.setColor(EditorColorScheme.WHOLE_BACKGROUND, bg)
                cs.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bg)
                cs.setColor(EditorColorScheme.CURRENT_LINE, blendArgb(bg, fg ?: 0xFFFFFFFF.toInt(), 0.08f))
            }
            if (fg != null) {
                cs.setColor(EditorColorScheme.TEXT_NORMAL, fg)
                cs.setColor(EditorColorScheme.LINE_NUMBER, blendArgb(fg, bg ?: 0xFF000000.toInt(), 0.5f))
            }
        }

        editor.setVerticalScrollBarEnabled(false)
        editor.setHorizontalScrollBarEnabled(false)

        editor.invalidate()
    } catch (_: Exception) {
    }
}

private fun blendArgb(a: Int, b: Int, t: Float): Int {
    val ar = (a shr 16 and 0xFF); val ag = (a shr 8 and 0xFF); val ab = (a and 0xFF)
    val br = (b shr 16 and 0xFF); val bg2 = (b shr 8 and 0xFF); val bb = (b and 0xFF)
    val r = (ar + (br - ar) * t).toInt().coerceIn(0, 255)
    val g = (ag + (bg2 - ag) * t).toInt().coerceIn(0, 255)
    val bl = (ab + (bb - ab) * t).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}

private fun resolveTypeface(settings: EditorSettings): Typeface = when (settings.fontFamily) {
    "sans" -> Typeface.SANS_SERIF
    "serif" -> Typeface.SERIF
    "custom" -> settings.customFontPath?.let { path ->
        runCatching { Typeface.createFromFile(path) }.getOrNull()
    } ?: Typeface.MONOSPACE
    else -> Typeface.MONOSPACE
}

private fun applyEditorSettings(editor: CodeEditor, settings: EditorSettings) {
    try {
        applyColorScheme(editor, settings.themeName)
        editor.setTextSize(settings.fontSizeSp.toFloat())
        editor.isLineNumberEnabled = settings.showLineNumbers
        editor.isWordwrap = settings.wordWrap
        editor.tabWidth = settings.tabWidth
        editor.setHighlightCurrentLine(settings.highlightActiveLine)
        editor.setHighlightBracketPair(settings.closeBracket)
        editor.setBlockLineEnabled(settings.indentGuide)
        editor.getProps().autoIndent = settings.autoIndent
        editor.getProps().symbolPairAutoCompletion = settings.closeBracket
        val completion = editor.getComponent(EditorAutoCompletion::class.java)
        completion.setEnabled(settings.autocomplete)
        if (!settings.autocomplete) {
            runCatching { completion.hide() }
        }
        editor.setTypefaceText(resolveTypeface(settings))
    } catch (_: Exception) {
    }
}

@Composable
fun SoraCodeEditor(
    modifier: Modifier = Modifier,
    state: CodeEditorState,
    fileName: String,
    content: String,
    settings: EditorSettings,
    onTextChange: () -> Unit
) {
    val context = LocalContext.current
    var dirtyNotified by remember { mutableStateOf(false) }
    var highlightingReady by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AndroidView(
            factory = {
                CodeEditor(context).also { editor ->
                    state.editor = editor
                    applyEditorSettings(editor, settings)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { (it as? CodeEditor)?.release() }
        )
        if (!highlightingReady) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }

    LaunchedEffect(fileName, content) {
        val editor = state.editor ?: return@LaunchedEffect
        highlightingReady = false
        val scopeName = scopeForName(fileName)
        if (scopeName != null) {
            val language = getOrCreateLanguage(scopeName)
            if (language != null) {
                try {
                    editor.setEditorLanguage(language)
                } catch (_: Exception) {
                }
            }
        }
        if (editor.getText().toString() != content) {
            editor.setText(content)
        }
        applyEditorSettings(editor, settings)
        try { editor.rerunAnalysis() } catch (_: Exception) {}
        editor.invalidate()
        var tries = 0
        while (tries < 40) {
            val styles = try { editor.styles } catch (_: Exception) { null }
            if (styles != null && styles.getSpans() != null) break
            delay(16)
            tries++
        }
        highlightingReady = true
        dirtyNotified = false
        while (true) {
            delay(300)
            if (editor.getText().toString() != content && !dirtyNotified) {
                dirtyNotified = true
                onTextChange()
            }
        }
    }

    LaunchedEffect(settings) {
        val editor = state.editor ?: return@LaunchedEffect
        applyEditorSettings(editor, settings)
    }
}