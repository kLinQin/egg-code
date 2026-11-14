package com.egg.codeeditor.ui.components

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.egg.codeeditor.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eclipse.jgit.api.Git

@Composable
fun FileManagerActionBar(
    rootFolderUri: Uri?,
    rootFolderPath: String?,
    expandedPaths: Set<String>,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRefresh: () -> Unit,
    onToggleCollapse: () -> Unit,
    onCloseProject: () -> Unit,
    actionDisabled: Boolean = false,
    isSupabaseMode: Boolean = false
) {
    val hasFolder = rootFolderUri != null || rootFolderPath != null || isSupabaseMode
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionPillVertical(onNewFile, Icons.Default.NoteAdd, "New File", enabled = hasFolder && !actionDisabled)
        ActionPillVertical(onNewFolder, Icons.Default.CreateNewFolder, "New Folder", enabled = hasFolder && !actionDisabled)
        ActionPillVertical(onRefresh, painterResource(R.drawable.ic_git_branch), "Refresh", enabled = hasFolder)
        ActionPillVertical(onToggleCollapse,
            if (expandedPaths.isNotEmpty()) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
            "Collapse", enabled = hasFolder)
        ActionPillVertical(onCloseProject, Icons.Default.ExitToApp, "Close", enabled = true)
    }
}

@Composable
fun GitActionBar(
    gitRepo: Git?,
    gitBusy: Boolean,
    commitMessage: String,
    remoteUrl: String?,
    onInit: () -> Unit,
    onClone: () -> Unit,
    onRefresh: () -> Unit,
    onAddRemote: () -> Unit,
    onFetch: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onCommit: () -> Unit,
    changeCount: Int = 0
) {
    if (gitRepo == null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onInit,
                modifier = Modifier.weight(1f),
                enabled = !gitBusy,
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (gitBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Init Repository")
            }
            OutlinedButton(
                onClick = onClone,
                modifier = Modifier.weight(1f),
                enabled = !gitBusy,
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clone", fontSize = 12.sp)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ActionPillVertical(onRefresh, painterResource(R.drawable.ic_git_branch), "Refresh", enabled = !gitBusy)
            if (remoteUrl == null) {
                ActionPillVertical(onAddRemote, Icons.Default.Link, "Remote", enabled = !gitBusy)
            } else {
                ActionPillVertical(onFetch, painterResource(R.drawable.ic_git_fetch), "Fetch", enabled = !gitBusy)
                ActionPillVertical(onPull, painterResource(R.drawable.ic_git_pull), "Pull", enabled = !gitBusy)
                ActionPillVertical(onPush, painterResource(R.drawable.ic_git_push), "Push", enabled = !gitBusy)
            }
            Box {
                ActionPillVertical(onCommit, painterResource(R.drawable.ic_git_commit), "Commit", enabled = !gitBusy && commitMessage.isNotBlank())
                if (changeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .background(MaterialTheme.colorScheme.error, RectangleShape)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(changeCount.toString(), color = MaterialTheme.colorScheme.onError, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPillVertical(onClick: () -> Unit, icon: Any, label: String, enabled: Boolean = true) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        when (icon) {
            is ImageVector -> Icon(icon, label, tint = tint)
            is androidx.compose.ui.graphics.painter.Painter -> Icon(icon, label, tint = tint)
        }
        Text(label, fontSize = 11.sp, maxLines = 1, color = tint)
    }
}

@Composable
private fun GitActionPill(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    label: String
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp)
    }
}