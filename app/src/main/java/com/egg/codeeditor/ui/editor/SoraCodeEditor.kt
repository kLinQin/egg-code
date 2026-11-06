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

/**
 * Per-scope grammar instance cache. Parsing TextMate (large JSON) is paid only
 * ONCE per language for the whole session; later files get instant highlighting.
 * Prewarming is invoked from MainActivity at startup.
 */
private val languageCache = HashMap<String, TextMateLanguage?>()

/** Get (or create) a grammar instance for a scope. Safe to call from any thread. */
internal fun getOrCreateLanguage(scope: String): TextMateLanguage? =
    languageCache.getOrPut(scope) {
        try {
            TextMateLanguage.create(scope, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

/** Map a file name to a TextMate scope. */
private fun scopeForName(fileName: String): String? = when {
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

/** Apply a color scheme by theme name. Used in the factory and LaunchedEffect. */
internal fun applyColorScheme(editor: CodeEditor, themeName: String) {
    try {
        ThemeRegistry.getInstance().setTheme(themeName)
        val scheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        if (themeName == "darcula") {
            scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB, android.graphics.Color.parseColor("#3D3D3A"))
            scheme.setColor(EditorColorScheme.SCROLL_BAR_TRACK, android.graphics.Color.TRANSPARENT)
        } else {
            scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB, android.graphics.Color.parseColor("#B8B8B8"))
            scheme.setColor(EditorColorScheme.SCROLL_BAR_TRACK, android.graphics.Color.TRANSPARENT)
        }
        editor.colorScheme = scheme

        // Disable the framework/stock Android scrollbars (white) — Sora draws its
        // own custom scrollbars with colors from the scheme above.
        editor.setVerticalScrollBarEnabled(false)
        editor.setHorizontalScrollBarEnabled(false)

        // Force a redraw so the change applies immediately
        editor.invalidate()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** Resolve the Typeface from font settings (preset or uploaded custom font). */
private fun resolveTypeface(settings: EditorSettings): Typeface = when (settings.fontFamily) {
    "sans" -> Typeface.SANS_SERIF
    "serif" -> Typeface.SERIF
    "custom" -> settings.customFontPath?.let { path ->
        runCatching { Typeface.createFromFile(path) }.getOrNull()
    } ?: Typeface.MONOSPACE
    else -> Typeface.MONOSPACE
}

/** Apply all editor settings (called on factory creation and when settings change). */
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
    } catch (e: Exception) {
        e.printStackTrace()
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
                    // Apply all settings BEFORE the editor is shown → no white flash
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

    // Language + content are combined: language is set FIRST, then text → highlight appears instantly.
    // After setEditorLanguage, Sora resets the color scheme to its default (white),
    // so applyColorScheme is called AGAIN to keep scrollbars and colors correct.
    LaunchedEffect(fileName, content) {
        val editor = state.editor ?: return@LaunchedEffect
        highlightingReady = false
        val scopeName = scopeForName(fileName)
        if (scopeName != null) {
            val language = getOrCreateLanguage(scopeName)
            if (language != null) {
                try {
                    editor.setEditorLanguage(language)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        if (editor.getText().toString() != content) {
            editor.setText(content)
        }
        // Re-apply the scheme after a language change
        applyEditorSettings(editor, settings)
        // Force analysis + redraw immediately (avoids "plain text until scroll")
        try { editor.rerunAnalysis() } catch (_: Exception) {}
        editor.invalidate()
        // Tunggu sampai analyzer TextMate selesai (styles siap) — tanpa timing.
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

    // Theme & appearance: applied when settings change (runtime, not startup)
    LaunchedEffect(settings) {
        val editor = state.editor ?: return@LaunchedEffect
        applyEditorSettings(editor, settings)
    }
}