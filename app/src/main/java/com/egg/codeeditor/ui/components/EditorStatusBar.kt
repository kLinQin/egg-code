package com.egg.codeeditor.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.model.EditorSettings
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.delay

private fun languageLabel(fileName: String): String {
    return when {
        fileName.endsWith(".kt") || fileName.endsWith(".kts") -> "Kotlin"
        fileName.endsWith(".java") -> "Java"
        fileName.endsWith(".js") -> "JavaScript"
        fileName.endsWith(".json") -> "JSON"
        fileName.endsWith(".html") || fileName.endsWith(".htm") -> "HTML"
        fileName.endsWith(".css") -> "CSS"
        fileName.endsWith(".py") -> "Python"
        fileName.endsWith(".md") -> "Markdown"
        fileName.endsWith(".xml") -> "XML"
        fileName.endsWith(".sh") -> "Shell"
        fileName.endsWith(".txt") -> "Text"
        else -> "Plain Text"
    }
}

/** Status bar bawah ala VS Code: posisi kursor, jumlah characters, tab, bahasa, encoding. */
@Composable
fun EditorStatusBar(
    editor: CodeEditor?,
    fileName: String,
    settings: EditorSettings
) {
    var line by remember { mutableIntStateOf(1) }
    var column by remember { mutableIntStateOf(1) }
    var chars by remember { mutableIntStateOf(0) }

    // Poll cursor // Pantau kursor & panjang teks secara berkala (Sora tidak punya listener sederhana) text length periodically (Sora has no simple listener)
    LaunchedEffect(editor) {
        while (true) {
            val e = editor
            if (e != null) {
                val c = e.cursor
                line = c.leftLine + 1
                column = c.leftColumn + 1
                chars = e.getText()?.length ?: 0
            }
            delay(200)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Ln $line, Col $column",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "$chars characters",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Spaces: ${settings.tabWidth}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                languageLabel(fileName),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "UTF-8",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}