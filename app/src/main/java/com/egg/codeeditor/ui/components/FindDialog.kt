package com.egg.codeeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rosemoe.sora.widget.CodeEditor

/** Convert a raw index into (line, column) zero-based coordinates. */
private fun indexToLineCol(text: String, index: Int): Pair<Int, Int> {
    var line = 0
    var col = 0
    for (i in 0 until index.coerceAtMost(text.length)) {
        if (text[i] == '\n') {
            line++
            col = 0
        } else {
            col++
        }
    }
    return line to col
}

/** Compute all (line, column) positions where the query appears in the text. */
private fun computeMatches(
    text: String,
    query: String,
    caseSensitive: Boolean = false,
    wholeWord: Boolean = false
): List<Pair<Int, Int>> {
    val matches = mutableListOf<Pair<Int, Int>>()
    if (query.isEmpty()) return matches
    val needle = if (caseSensitive) query else query.lowercase()
    val hay = if (caseSensitive) text else text.lowercase()
    var idx = 0
    while (true) {
        val found = hay.indexOf(needle, idx)
        if (found < 0) break
        val ok = if (wholeWord) {
            val before = if (found == 0) ' ' else hay[found - 1]
            val afterIdx = found + needle.length
            val after = if (afterIdx >= hay.length) ' ' else hay[afterIdx]
            !before.isLetterOrDigit() && before != '_' &&
                !after.isLetterOrDigit() && after != '_'
        } else true
        if (ok) matches.add(indexToLineCol(text, found))
        idx = found + needle.length
    }
    return matches
}

/** Compact input box (height 38dp) with explicit colors.
 *  BasicTextField avoids Material3 clipping (min-height ±56dp) which
 *  would make text invisible when forced to 38dp height. */
@Composable
private fun CompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RectangleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .padding(horizontal = 12.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField: @Composable () -> Unit ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            }
        )
    }
}

/** Inline Find bar à la VS Code, shown above the editor. */
@Composable
fun FindBar(
    editor: CodeEditor?,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var replaceWith by remember { mutableStateOf("") }
    var replaceMode by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }

    fun refreshMatches() {
        if (editor == null) return
        matches = computeMatches(editor.getText().toString(), query, caseSensitive, wholeWord)
        currentIndex = 0
    }

    fun jumpTo(index: Int) {
        val list = matches
        if (editor == null || list.isEmpty()) return
        val safe = ((index % list.size) + list.size) % list.size
        currentIndex = safe
        val (line, col) = list[safe]
        editor.setSelection(line, col)
        editor.ensurePositionVisible(line, col)
    }

    fun replaceAll() {
        val e = editor ?: return
        val text = e.getText().toString()
        if (query.isEmpty()) return
        val positions = computeMatches(text, query, caseSensitive, wholeWord)
        if (positions.isEmpty()) return
        // konversi (line,col) -> index
        fun idx(lc: Pair<Int, Int>): Int {
            var cur = 0; var ln = 0
            while (ln < lc.first && cur < text.length) {
                if (text[cur] == '\n') ln++
                cur++
            }
            return (cur + lc.second).coerceIn(0, text.length)
        }
        val sb = StringBuilder()
        var last = 0
        for (p in positions) {
            val i = idx(p)
            sb.append(text, last, i)
            sb.append(replaceWith)
            last = i + query.length
        }
        sb.append(text, last, text.length)
        e.setText(sb.toString())
        matches = emptyList()
        currentIndex = 0
    }

    fun indexOfLineCol(text: String, line: Int, col: Int): Int {
        var cur = 0
        var ln = 0
        while (ln < line && cur < text.length) {
            if (text[cur] == '\n') ln++
            cur++
        }
        return (cur + col).coerceIn(0, text.length)
    }

    fun replaceCurrent() {
        val e = editor ?: return
        if (query.isEmpty() || matches.isEmpty()) return
        val text = e.getText().toString()
        val list = computeMatches(text, query, caseSensitive, wholeWord)
        if (list.isEmpty()) return
        val idx = currentIndex.coerceIn(0, list.size - 1)
        val (line, col) = list[idx]
        val newText = text.substring(0, indexOfLineCol(text, line, col)) +
            replaceWith +
            text.substring(indexOfLineCol(text, line, col) + query.length)
        e.setText(newText)
        matches = computeMatches(newText, query, caseSensitive, wholeWord)
        if (matches.isEmpty()) currentIndex = 0
        else currentIndex = idx.coerceAtMost(matches.size - 1)
        if (matches.isNotEmpty()) {
            val (l, c) = matches[currentIndex]
            e.setSelection(l, c)
            e.ensurePositionVisible(l, c)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactInputField(
                    value = query,
                    onValueChange = { query = it; refreshMatches() },
                    placeholder = "Search",
                    modifier = Modifier.weight(1f).height(38.dp)
                )
                Text(
                    if (matches.isEmpty()) "" else "${currentIndex + 1}/${matches.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { jumpTo(currentIndex - 1) },
                    enabled = matches.isNotEmpty(),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "Previous", Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { jumpTo(currentIndex + 1) },
                    enabled = matches.isNotEmpty(),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Next", Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { replaceMode = !replaceMode },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.SwapVert,
                        if (replaceMode) "Hide replace" else "Show replace",
                        Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { caseSensitive = !caseSensitive; refreshMatches() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Text("Aa", fontSize = 11.sp, fontWeight = if (caseSensitive) FontWeight.Bold else FontWeight.Normal, color = if (caseSensitive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = { wholeWord = !wholeWord; refreshMatches() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Text("ab|", fontSize = 11.sp, fontWeight = if (wholeWord) FontWeight.Bold else FontWeight.Normal, color = if (wholeWord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, "Close", Modifier.size(16.dp))
                }
            }

            if (replaceMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactInputField(
                        value = replaceWith,
                        onValueChange = { replaceWith = it },
                        placeholder = "Replace with",
                        modifier = Modifier.weight(1f).height(38.dp)
                    )
                    Button(
                        onClick = ::replaceCurrent,
                        enabled = query.isNotEmpty() && matches.isNotEmpty(),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Replace", fontSize = 11.sp)
                    }
                    Button(
                        onClick = ::replaceAll,
                        enabled = query.isNotEmpty(),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Replace All", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}