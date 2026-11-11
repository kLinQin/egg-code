package com.egg.codeeditor.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.model.EditorSettings
import com.egg.codeeditor.util.DiffLine
import com.egg.codeeditor.util.computeLineDiff

@Composable
fun DiffView(
    relPath: String,
    oldText: String,
    newText: String,
    settings: EditorSettings,
    modifier: Modifier = Modifier
) {
    val lines = remember(oldText, newText) { computeLineDiff(oldText, newText) }
    val wrap = settings.wordWrap
    val hScroll = rememberScrollState()
    val fontFamily = when (settings.fontFamily) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        else -> FontFamily.Monospace
    }
    val sameColor = MaterialTheme.colorScheme.onSurface
    val addColor = MaterialTheme.colorScheme.primary
    val removeColor = MaterialTheme.colorScheme.error
    val addBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val removeBg = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    val numColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxLineNo = lines.maxOfOrNull { it.newLineNo ?: 0 } ?: 0
    val digits = maxLineNo.toString().length.coerceAtLeast(3)
    val numWidth = (digits * (settings.fontSizeSp * 0.6f) + 8).dp

    LazyColumn(modifier.fillMaxSize()) {
        items(lines.size) { idx ->
            val l = lines[idx]
            val bg = when (l.kind) {
                DiffLine.Kind.SAME -> Color.Transparent
                DiffLine.Kind.ADD -> addBg
                DiffLine.Kind.REMOVE -> removeBg
            }
            val prefix = when (l.kind) {
                DiffLine.Kind.SAME -> "  "
                DiffLine.Kind.ADD -> "+ "
                DiffLine.Kind.REMOVE -> "- "
            }
            val textColor = when (l.kind) {
                DiffLine.Kind.SAME -> sameColor
                DiffLine.Kind.ADD -> addColor
                DiffLine.Kind.REMOVE -> removeColor
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(hScroll))
                    .padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (settings.showLineNumbers) {
                    Text(
                        text = l.newLineNo?.toString() ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = settings.fontSizeSp.sp,
                        color = numColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.width(numWidth)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = prefix + l.text,
                    fontFamily = fontFamily,
                    fontSize = settings.fontSizeSp.sp,
                    color = textColor,
                    softWrap = wrap
                )
            }
        }
    }
}
