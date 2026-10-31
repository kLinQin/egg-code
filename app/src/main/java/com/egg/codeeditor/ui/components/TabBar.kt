package com.egg.codeeditor.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.model.EditorTab

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
    onNewTab: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    EditorTabItem(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        onSelect = onSelect,
                        onClose = onClose,
                        onSave = onSave,
                        onCloseOthers = onCloseOthers,
                        onDelete = onDelete
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
    onDelete: (EditorTab) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .height(40.dp)
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
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                null,
                Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                tab.name,
                fontSize = 12.sp,
                maxLines = 1,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.width(6.dp))
            if (tab.isDirty) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(
                onClick = { onClose(tab) },
                modifier = Modifier.size(18.dp)
            ) {
                Icon(Icons.Default.Close, "Close Tab", Modifier.size(12.dp))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Save") }, onClick = { menuOpen = false; onSave(tab) })
            DropdownMenuItem(text = { Text("Close") }, onClick = { menuOpen = false; onClose(tab) })
            DropdownMenuItem(text = { Text("Close Others") }, onClick = { menuOpen = false; onCloseOthers(tab.id) })
            if (tab.uri != null) {
                DropdownMenuItem(text = { Text("Delete File") }, onClick = { menuOpen = false; onDelete(tab) })
            }
        }
    }
}