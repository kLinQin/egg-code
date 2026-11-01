package com.egg.codeeditor.ui.git

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.egg.codeeditor.model.GitStatusInfo
import com.egg.codeeditor.util.GitCommitInfo
import org.eclipse.jgit.api.Git
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GitPanel(
    gitRepo: Git?,
    gitStatus: GitStatusInfo?,
    gitCommits: List<GitCommitInfo>,
    commitMessage: String,
    gitBusy: Boolean,
    gitMessage: String?,
    onCommitMessageChange: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (gitRepo == null) {
            Text(
                "Repository is not initialized. Use Init or Clone above.",
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
            OutlinedTextField(
                value = commitMessage,
                onValueChange = onCommitMessageChange,
                label = { Text("Commit Message", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !gitBusy
            )
            Spacer(Modifier.height(8.dp))
            if (gitStatus != null) {
                Text("Changes:", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    val allChanges = buildList {
                        gitStatus.added.forEach { add("A  $it") }
                        gitStatus.modified.forEach { add("M  $it") }
                        gitStatus.untracked.forEach { add("?? $it") }
                        gitStatus.removed.forEach { add("D  $it") }
                        gitStatus.missing.forEach { add("!  $it") }
                    }
                    if (allChanges.isEmpty()) item { Text("(Clean)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    items(allChanges) { line -> Text(line, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                }
            } else {
                Text("Press Refresh to load.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            if (gitCommits.isEmpty()) {
                Text("No commits yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(gitCommits) { c ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(
                                c.message,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${c.shortId} · ${c.author} · ${formatCommitTime(c.time)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        if (gitMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(gitMessage, color = if (gitMessage.contains("failed")) Color.Red else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}

@Composable
fun GitRemoteDialog(
    title: String,
    onConfirm: (url: String, user: String, token: String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Repository URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token / Password (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(url.trim(), user.trim(), token.trim()) }, enabled = url.isNotBlank()) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatCommitTime(millis: Long): String {
    return SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(millis))
}