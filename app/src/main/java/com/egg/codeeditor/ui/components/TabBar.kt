package com.egg.codeeditor.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.egg.codeeditor.model.EditorTab
import com.egg.codeeditor.ui.components.fileIconSpec

@Composable
private fun MenuTextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorTabBar(
    tabs: List<EditorTab>,
    activeTabId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (EditorTab) -> Unit,
    onSave: (EditorTab) -> Unit,
    onCloseOthers: (Long) -> Unit,
    onDelete: (EditorTab) -> Unit,
    onNewTab: () -> Unit,
    gitMarkerFor: (EditorTab) -> Char? = { null }
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val listState = rememberLazyListState()
            LaunchedEffect(activeTabId) {
                val idx = tabs.indexOfFirst { it.id == activeTabId }
                if (idx >= 0) listState.animateScrollToItem(idx)
            }
            LazyRow(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                state = listState,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(items = tabs, key = { it.id }) { tab ->
                    EditorTabItem(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        onSelect = onSelect,
                        onClose = onClose,
                        onSave = onSave,
                        onCloseOthers = onCloseOthers,
                        onDelete = onDelete,
                        gitMarkerFor = gitMarkerFor
                    )
                }
            }
            IconButton(onClick = onNewTab, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, "New Tab", Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorTabItem(
    tab: EditorTab,
    isActive: Boolean,
    onSelect: (Long) -> Unit,
    onClose: (EditorTab) -> Unit,
    onSave: (EditorTab) -> Unit,
    onCloseOthers: (Long) -> Unit,
    onDelete: (EditorTab) -> Unit,
    gitMarkerFor: (EditorTab) -> Char? = { null }
) {
    var menuOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box {
        Row(
            modifier = Modifier
                .height(36.dp)
                .combinedClickable(
                    onClick = { onSelect(tab.id) },
                    onLongClick = { menuOpen = true }
                )
                .background(
                    if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fileSpec = fileIconSpec(tab.name)
            if (fileSpec.bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = fileSpec.bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
            } else {
                Icon(
                    fileSpec.icon,
                    null,
                    Modifier.size(13.dp),
                    tint = fileSpec.color
                )
            }
            Spacer(Modifier.width(6.dp))
            val marker = gitMarkerFor(tab)
            val markerColor = when (marker) {
                'M' -> androidx.compose.ui.graphics.Color(0xFFB58900)
                'U' -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                'A' -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                'D' -> androidx.compose.ui.graphics.Color(0xFFC62828)
                else -> null
            }
            Text(
                tab.name,
                fontSize = 12.sp,
                maxLines = 1,
                color = markerColor ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
            if (marker != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    marker.toString(),
                    fontSize = 9.sp,
                    color = markerColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(6.dp))
            if (tab.isDirty) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            RectangleShape
                        )
                )
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = { onClose(tab) }),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Close Tab", Modifier.size(12.dp))
            }
        }

        if (menuOpen) {
            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) { IntOffset(0, 44.dp.roundToPx()) },
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest, // DROPDOWN_STYLE
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(max = 360.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MenuTextAction("Save") { menuOpen = false; onSave(tab) }
                        MenuTextAction("Close") { menuOpen = false; onClose(tab) }
                        MenuTextAction("Close Others") { menuOpen = false; onCloseOthers(tab.id) }
                        if (tab.uri != null) {
                            MenuTextAction("Delete File") { menuOpen = false; onDelete(tab) }
                        }
                    }
                }
            }
        }
    }
}