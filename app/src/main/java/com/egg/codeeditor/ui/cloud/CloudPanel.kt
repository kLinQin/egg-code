package com.egg.codeeditor.ui.cloud

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudPanel(
    connected: Boolean,
    cloudChanges: List<CloudChange>,
    cloudHistory: List<B2FileInfo>,
    cloudBusy: Boolean,
    cloudMessage: String?
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Busy indicator: always visible when an operation is running
        if (cloudBusy) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
        }

        // Message (success or error): always visible, even when not connected
        if (cloudMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                cloudMessage,
                color = if (cloudMessage.contains("failed")) Color.Red else MaterialTheme.colorScheme.primary,
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
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (cloudChanges.isEmpty()) {
                    item { Text("(In sync)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                }
                items(cloudChanges) { c ->
                    when (c) {
                        is CloudChange.New -> Text("+  ${c.relPath}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        is CloudChange.Modified -> Text("~  ${c.relPath}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        is CloudChange.RemoteOnly -> Text("-  ${c.relPath}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
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