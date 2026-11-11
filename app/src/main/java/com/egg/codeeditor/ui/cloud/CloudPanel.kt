package com.egg.codeeditor.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.util.B2FileInfo
import com.egg.codeeditor.util.CloudChange
import com.egg.codeeditor.util.isErrorMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudPanel(
    connected: Boolean,
    cloudChanges: List<CloudChange>,
    cloudHistory: List<B2FileInfo>,
    cloudBusy: Boolean,
    cloudMessage: String?,
    onPushFile: (String) -> Unit = {},
    onDiscardFile: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteRemote: (String) -> Unit = {},
    onDiscardAll: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (cloudBusy) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
        }

        if (cloudMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                cloudMessage,
                color = if (isErrorMessage(cloudMessage)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }

        if (!connected) {
            Text(
                "Not connected to Backblaze B2. Press Connect above.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            return@Column
        }

        var tab by remember { mutableStateOf(0) }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Changes") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
        }

        Spacer(Modifier.height(8.dp))

        if (tab == 0) {
            val hasDiscardable = cloudChanges.any { it is CloudChange.New || it is CloudChange.Modified }
            if (hasDiscardable) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDiscardAll, enabled = !cloudBusy) { Text("Discard All", fontSize = 12.sp) }
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (cloudChanges.isEmpty()) {
                    item { Text("(In sync)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                }
                items(cloudChanges) { c ->
                    val relPath = when (c) {
                        is CloudChange.New -> c.relPath
                        is CloudChange.Modified -> c.relPath
                        is CloudChange.RemoteOnly -> c.relPath
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val label = when (c) {
                            is CloudChange.New -> "U  $relPath"
                            is CloudChange.Modified -> "M  $relPath"
                            is CloudChange.RemoteOnly -> "D  $relPath"
                        }
                        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        when (c) {
                            is CloudChange.New -> {
                                IconButton(onClick = { onPushFile(relPath) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Upload, "Push", Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDiscardFile(relPath, true) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, "Discard", Modifier.size(16.dp))
                                }
                            }
                            is CloudChange.Modified -> {
                                IconButton(onClick = { onPushFile(relPath) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Upload, "Push", Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDiscardFile(relPath, false) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, "Discard", Modifier.size(16.dp))
                                }
                            }
                            is CloudChange.RemoteOnly -> {
                                IconButton(onClick = { onDeleteRemote(relPath) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, "Delete remote", Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (cloudHistory.isEmpty()) {
                Text("No files in bucket yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(cloudHistory) { f ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(
                                f.fileName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                formatBytes(f.contentLength) + " · " + formatTime(f.uploadTimestamp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloudConnectDialog(
    onConfirm: (keyId: String, appKey: String, bucket: String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyId by remember { mutableStateOf("005add1300778d40000000003") }
    var appKey by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("kode-eggs-2026") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = { Text("Connect Backblaze B2") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyId,
                    onValueChange = { keyId = it },
                    label = { Text("Application Key ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = appKey,
                    onValueChange = { appKey = it },
                    label = { Text("Application Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bucket,
                    onValueChange = { bucket = it },
                    label = { Text("Bucket Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(keyId.trim(), appKey.trim(), bucket.trim()) }, enabled = keyId.isNotBlank() && appKey.isNotBlank() && bucket.isNotBlank()) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatBytes(n: Long): String {
    return when {
        n >= 1024 * 1024 -> "%.1f MB".format(n / (1024.0 * 1024.0))
        n >= 1024 -> "%.1f KB".format(n / 1024.0)
        else -> "$n B"
    }
}

private fun formatTime(millis: Long): String {
    return if (millis <= 0) "unknown" else SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(millis))
}