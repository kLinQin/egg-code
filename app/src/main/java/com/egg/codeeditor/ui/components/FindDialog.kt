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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

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
    if (query.isEmpty() || query.length > text.length) return matches
    var idx = 0
    val limit = text.length - query.length
    while (idx <= limit) {
        if (text.regionMatches(idx, query, 0, query.length, ignoreCase = !caseSensitive)) {
            val ok = if (wholeWord) {
                val before = if (idx == 0) ' ' else text[idx - 1]
                val afterIdx = idx + query.length
                val after = if (afterIdx >= text.length) ' ' else text[afterIdx]
                !before.isLetterOrDigit() && before != '_' &&
                    !after.isLetterOrDigit() && after != '_'
            } else true
            if (ok) matches.add(indexToLineCol(text, idx))
            idx += query.length
        } else {
            idx++
        }
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
    var query by remember {
        mutableStateOf(
            editor?.let { e ->
                val cur = e.cursor
                if (cur.isSelected) {
                    e.text.substring(cur.left, cur.right)
                } else ""
            } ?: ""
        )
    }
    var replaceWith by remember { mutableStateOf("") }
    var replaceMode by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }

    suspend fun refreshMatches() {
        val e = editor ?: return
        val q = query
        if (q.isEmpty()) {
            withContext(Dispatchers.Main) {
                e.searcher.stopSearch()
                matches = emptyList()
                currentIndex = 0
            }
            return
        }
        val type = if (wholeWord) EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                   else EditorSearcher.SearchOptions.TYPE_NORMAL
        withContext(Dispatchers.Main) {
            e.searcher.setCyclicJumping(true)
            e.searcher.search(q, EditorSearcher.SearchOptions(type, !caseSensitive))
        }
    }

    fun jumpTo(index: Int) {
        val e = editor ?: return
        if (!e.searcher.hasQuery() || e.searcher.matchedPositionCount == 0) return
        val n = e.searcher.matchedPositionCount
        val safe = ((index % n) + n) % n
        currentIndex = safe
        val cur = e.searcher.currentMatchedPositionIndex
        val delta = safe - (if (cur < 0) 0 else cur)
        if (delta >= 0) {
            repeat(delta) { e.searcher.gotoNext() }
        } else {
            repeat(-delta) { e.searcher.gotoPrevious() }
        }
    }

    fun replaceAll() {
        val e = editor ?: return
        val content = e.text
        val text = content.toString()
        if (query.isEmpty()) return
        val positions = computeMatches(text, query, caseSensitive, wholeWord)
        if (positions.isEmpty()) return
        for (i in positions.indices.reversed()) {
            val (l, col) = positions[i]
            val s = content.getCharIndex(l, col)
            val en = s + query.length
            if (en <= content.length) content.replace(s, en, replaceWith)
        }
        matches = emptyList()
        currentIndex = 0
    }

    fun replaceCurrent() {
        val e = editor ?: return
        if (query.isEmpty() || matches.isEmpty()) return
        val content = e.text
        val text = content.toString()
        val list = computeMatches(text, query, caseSensitive, wholeWord)
        if (list.isEmpty()) return
        val idx = currentIndex.coerceIn(0, list.size - 1)
        val (line, col) = list[idx]
        val s = content.getCharIndex(line, col)
        val en = s + query.length
        if (en > content.length) return
        content.replace(s, en, replaceWith)
        val after = content.toString()
        matches = computeMatches(after, query, caseSensitive, wholeWord)
        if (matches.isEmpty()) {
            currentIndex = 0
        } else {
            val nextStart = s + replaceWith.length
            var pick = matches.indexOfFirst { (l, cl) ->
                content.getCharIndex(l, cl) >= nextStart
            }
            if (pick < 0) pick = 0
            currentIndex = pick
            val (l, cl) = matches[currentIndex]
            e.setSelection(l, cl)
            e.ensurePositionVisible(l, cl, true)
        }
    }

    LaunchedEffect(query, caseSensitive, wholeWord) {
        delay(120)
        refreshMatches()
    }

    DisposableEffect(editor) {
        val e = editor
        val receipt = e?.subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
            val searcher = e.searcher
            matches = if (searcher.hasQuery()) {
                val n = searcher.matchedPositionCount
                val q = query
                List(n) { 0 to 0 }
            } else emptyList()
            currentIndex = 0
        }
        onDispose {
            receipt?.unsubscribe()
            e?.searcher?.stopSearch()
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
                    onValueChange = { query = it },
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
                    onClick = { caseSensitive = !caseSensitive },
                    modifier = Modifier.size(30.dp)
                ) {
                    Text("Aa", fontSize = 11.sp, fontWeight = if (caseSensitive) FontWeight.Bold else FontWeight.Normal, color = if (caseSensitive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = { wholeWord = !wholeWord },
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