package com.egg.codeeditor.ui.diff

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.egg.codeeditor.model.EditorSettings
import com.egg.codeeditor.ui.editor.applyColorScheme
import com.egg.codeeditor.ui.editor.getOrCreateLanguage
import com.egg.codeeditor.util.DiffLine
import com.egg.codeeditor.util.computeLineDiff
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.lang.styling.color.ResolvableColor
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.delay

private fun scopeForNameDiff(fileName: String): String? = when {
    fileName.endsWith(".kt") -> "source.kotlin"
    fileName.endsWith(".java") -> "source.java"
    fileName.endsWith(".js") -> "source.javascript"
    fileName.endsWith(".json") -> "source.json"
    fileName.endsWith(".html") || fileName.endsWith(".htm") -> "text.html.basic"
    fileName.endsWith(".css") -> "source.css"
    fileName.endsWith(".py") -> "source.python"
    fileName.endsWith(".md") -> "text.html.markdown"
    else -> null
}

@Composable
fun SoraDiffView(
    relPath: String,
    oldText: String,
    newText: String,
    settings: EditorSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val diffLines = remember(oldText, newText) { computeLineDiff(oldText, newText) }
    val diffText = remember(diffLines) {
        val sb = StringBuilder()
        diffLines.forEach { d ->
            when (d.kind) {
                DiffLine.Kind.ADD -> sb.append("+ ").append(d.text).append('\n')
                DiffLine.Kind.REMOVE -> sb.append("- ").append(d.text).append('\n')
                DiffLine.Kind.SAME -> sb.append("  ").append(d.text).append('\n')
            }
        }
        sb.toString()
    }
    val addLines = remember(diffLines) {
        var idx = 0
        val out = mutableListOf<Int>()
        diffLines.forEach { d -> if (d.kind == DiffLine.Kind.ADD) out.add(idx); idx++ }
        out
    }
    val removeLines = remember(diffLines) {
        var idx = 0
        val out = mutableListOf<Int>()
        diffLines.forEach { d -> if (d.kind == DiffLine.Kind.REMOVE) out.add(idx); idx++ }
        out
    }

    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                applyColorScheme(this, settings.themeName)
                setTextSize(settings.fontSizeSp.toFloat())
                setTypefaceText(Typeface.MONOSPACE)
                isLineNumberEnabled = true
                setLineNumberMarginLeft(6f)
                isWordwrap = settings.wordWrap
                setEditable(false)
                setHighlightCurrentLine(false)
                setHighlightBracketPair(false)
                setBlockLineEnabled(false)
                scopeForNameDiff(relPath)?.let { s -> getOrCreateLanguage(s)?.let { setEditorLanguage(it) } }
                setText(diffText)
                editorRef = this
            }
        },
        onRelease = { (it as? CodeEditor)?.release() }
    )

    LaunchedEffect(diffText, editorRef) {
        val ed = editorRef ?: return@LaunchedEffect
        // Tunggu analyzer selesai + layout selesai
        delay(800)
        val hc = HighlightTextContainer()
        val addColor = android.graphics.Color.argb(80, 46, 125, 50)
        val removeColor = android.graphics.Color.argb(80, 198, 40, 40)
        val addR = ResolvableColor { addColor }
        val removeR = ResolvableColor { removeColor }
        addLines.forEach { ln ->
            if (ln < ed.lineCount) {
                val len = ed.text.getLine(ln).length
                if (len > 0) hc.add(HighlightTextContainer.HighlightText(ln, 0, ln, len, addR, addR))
            }
        }
        removeLines.forEach { ln ->
            if (ln < ed.lineCount) {
                val len = ed.text.getLine(ln).length
                if (len > 0) hc.add(HighlightTextContainer.HighlightText(ln, 0, ln, len, removeR, removeR))
            }
        }
        // Set default highlight bg (textmate scheme mungkin tidak set)
        try {
            ed.colorScheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_HIGHLIGHT_BACKGROUND, android.graphics.Color.argb(80, 100, 100, 100))
            ed.colorScheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_HIGHLIGHT_BORDER, android.graphics.Color.TRANSPARENT)
        } catch (_: Throwable) {}
        ed.setHighlightTexts(hc)
        ed.invalidate()
    }
}
