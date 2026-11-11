package com.egg.codeeditor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.egg.codeeditor.model.FileNode
import com.egg.codeeditor.ui.components.TreeGuides
import com.egg.codeeditor.ui.components.fileIconSpec
import com.egg.codeeditor.ui.components.folderIconSpec

@androidx.compose.runtime.Composable
private fun gitMarkerColor(marker: Char): androidx.compose.ui.graphics.Color {
    val onSurface = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val lum = 0.299f * onSurface.red + 0.587f * onSurface.green + 0.114f * onSurface.blue
    val dark = lum > 0.5f  // teks terang = bg gelap
    return when (marker) {
        'M' -> if (dark) androidx.compose.ui.graphics.Color(0xFFE2C08D) else androidx.compose.ui.graphics.Color(0xFFB58900)
        'U' -> if (dark) androidx.compose.ui.graphics.Color(0xFF73C991) else androidx.compose.ui.graphics.Color(0xFF2E7D32)
        'A' -> if (dark) androidx.compose.ui.graphics.Color(0xFF73C991) else androidx.compose.ui.graphics.Color(0xFF2E7D32)
        'D' -> if (dark) androidx.compose.ui.graphics.Color(0xFFF48771) else androidx.compose.ui.graphics.Color(0xFFC62828)
        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    }
}


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
internal fun FileManagerPanel(
    vm: EggViewModel,
    guidesByUri: Map<String, List<Boolean>>,
    onOpenFile: (FileNode) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(vm.visibleNodes, key = { it.uri.toString() }) { node ->
            var showContextMenu by remember { mutableStateOf(false) }

            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .combinedClickable(
                            onClick = {
                                if (node.isDirectory) vm.toggleExpand(node.uri.toString())
                                else onOpenFile(node)
                            },
                            onLongClick = { showContextMenu = true }
                        )
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (node.depth > 0) {
                        TreeGuides(
                            depth = node.depth,
                            hasGuide = guidesByUri[node.uri.toString()] ?: emptyList(),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            height = 48.dp
                        )
                    }

                    if (node.isDirectory) {
                        val isExpanded = vm.expandedPaths.contains(node.uri.toString())
                        val folderSpec = folderIconSpec(node.depth)
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            "Expand",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            if (isExpanded) Icons.Default.FolderOpen else folderSpec.icon,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = folderSpec.color
                        )
                    } else {
                        Spacer(Modifier.width(20.dp))
                        val fileSpec = fileIconSpec(node.name)
                        if (fileSpec.bitmap != null) androidx.compose.foundation.Image(fileSpec.bitmap, null, modifier = Modifier.size(18.dp)) else Icon(fileSpec.icon, null, modifier = Modifier.size(18.dp), tint = fileSpec.color)
                    }

                    Spacer(Modifier.width(8.dp))
                    val marker = vm.gitMarkerFor(node)
                    if (marker != null) {
                        val markerColor = when (marker) {
                            'M' -> MaterialTheme.colorScheme.tertiary
                            'U' -> MaterialTheme.colorScheme.primary
                            'A' -> MaterialTheme.colorScheme.primary
                            'D' -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            marker.toString(),
                            fontSize = 10.sp,
                            color = markerColor,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        node.name,
                        maxLines = 1,
                        color = if (marker != null) gitMarkerColor(marker)
                                else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (showContextMenu) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = with(density) { IntOffset(4.dp.roundToPx() - (if (node.isDirectory) (node.depth * 16 + 24).dp.roundToPx() else 0), 44.dp.roundToPx()) },
                    onDismissRequest = { showContextMenu = false },
                    properties = PopupProperties(focusable = true, clippingEnabled = true)
                ) {
                    Surface(
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest, // DROPDOWN_STYLE
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.widthIn(max = 360.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .horizontalScroll(scrollState)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                                MenuTextAction("Copy Path") {
                                    clipboardManager.setText(AnnotatedString(node.uri.toString()))
                                    showContextMenu = false
                                }
                                MenuTextAction("Rename") {
                                    vm.selectedNode = node
                                    vm.showRenameDialog = true
                                    showContextMenu = false
                                }
                                MenuTextAction("Delete") {
                                    vm.selectedNode = node
                                    vm.showDeleteDialog = true
                                    showContextMenu = false
                                }
                                if (node.isDirectory) {
                                    MenuTextAction("New File Here") {
                                        vm.targetParentNode = node
                                        vm.showNewFileDialog = true
                                        showContextMenu = false
                                    }
                                    MenuTextAction("New Folder Here") {
                                        vm.targetParentNode = node
                                        vm.showNewFolderDialog = true
                                        showContextMenu = false
                                    }
                                }
                        }
                    }
                }
                }
            }
        }
    }
}