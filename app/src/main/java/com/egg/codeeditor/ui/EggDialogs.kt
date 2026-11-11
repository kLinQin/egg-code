package com.egg.codeeditor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.ui.components.InputDialog
import com.egg.codeeditor.ui.components.SettingsDialog
import com.egg.codeeditor.ui.git.GitRemoteDialog

private const val GITHUB_URL = ""

@Composable
internal fun EggDialogs(
    vm: EggViewModel,
    onExit: () -> Unit,
) {
    if (vm.showNewFileDialog) {
        InputDialog(
            title = "New File Name",
            onDismiss = { vm.showNewFileDialog = false },
            onConfirm = { name ->
                vm.showNewFileDialog = false
                vm.createFile(name)
            }
        )
    }

    if (vm.showNewFolderDialog) {
        InputDialog(
            title = "New Folder Name",
            onDismiss = { vm.showNewFolderDialog = false },
            onConfirm = { name ->
                vm.showNewFolderDialog = false
                vm.createFolder(name)
            }
        )
    }

    if (vm.showRenameDialog && vm.selectedNode != null) {
        InputDialog(
            title = "Rename",
            initialText = vm.selectedNode!!.name,
            onDismiss = { vm.showRenameDialog = false },
            onConfirm = { newName ->
                vm.showRenameDialog = false
                vm.rename(newName)
            }
        )
    }

    if (vm.showDeleteDialog && vm.selectedNode != null) {
        AlertDialog(
            onDismissRequest = { vm.showDeleteDialog = false },
            title = { Text("Delete") },
            text = { Text("Are you sure you want to delete '${vm.selectedNode!!.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.showDeleteDialog = false
                    vm.deleteSelected()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (vm.showAboutDialog) {
        val ctx = LocalContext.current
        val pkg = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
        val appName = remember {
            runCatching { ctx.applicationInfo.loadLabel(ctx.packageManager).toString() }.getOrDefault("Egg Code Editor")
        }
        val versionText = if (pkg != null) "${pkg.versionName ?: "?"} (${pkg.longVersionCode})" else "?"
        AlertDialog(
            onDismissRequest = { vm.showAboutDialog = false },
            title = { Text("About") },
            text = {
                Column {
                    Text(appName, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Version: $versionText")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A lightweight code editor based on Sora Editor, with an Alpine terminal, Git, and Claude themes.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (GITHUB_URL.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("GitHub:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            GITHUB_URL,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.showAboutDialog = false }) { Text("Close") }
            }
        )
    }

    vm.pendingCloseTab?.let { tab ->
        AlertDialog(
            onDismissRequest = { vm.pendingCloseTab = null },
            title = { Text("Save changes?") },
            text = {
                Column {
                    Text("File '${tab.name}' has unsaved changes.")
                    Spacer(Modifier.height(8.dp))
                    Text("Choose an action to continue.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.pendingCloseTab = null
                    vm.saveTabAndClose(tab)
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.pendingCloseTab = null
                        vm.reallyCloseTab(tab.id)
                    }) { Text("Don't Save") }
                    TextButton(onClick = { vm.pendingCloseTab = null }) { Text("Cancel") }
                }
            }
        )
    }

    vm.pendingDeleteTab?.let { tab ->
        AlertDialog(
            onDismissRequest = { vm.pendingDeleteTab = null },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete '${tab.name}' from storage?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.pendingDeleteTab = null
                    vm.deleteTabFromStorage(tab)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { vm.pendingDeleteTab = null }) { Text("Cancel") }
            }
        )
    }

    if (vm.showSettings) {
        SettingsDialog(
            settings = vm.settings,
            onSettingsChange = { vm.updateSettings(it) },
            onDismiss = { vm.showSettings = false },
            onBackup = { vm.performBackup(it) },
            onRestore = { vm.performRestore(it) },
            discordToken = vm.discordToken,
            discordEnabled = vm.discordEnabled,
            onDiscordTokenChange = { vm.updateDiscordToken(it) },
            onDiscordEnabledChange = { vm.updateDiscordEnabled(it) }
        )
    }

    if (vm.showExitConfirm) {
        AlertDialog(
            onDismissRequest = { vm.showExitConfirm = false },
            title = { Text("Exit Egg?") },
            text = { Text("Unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = onExit) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showExitConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (vm.showGitRemoteDialog) {
        GitRemoteDialog(
            title = vm.gitRemoteDialogTitle,
            onConfirm = { url, user, token ->
                vm.showGitRemoteDialog = false
                if (vm.gitRemoteDialogTitle == "Clone Repository") vm.gitDoClone(url, user, token)
                else vm.gitAddRemote(url, user, token)
            },
            onDismiss = { vm.showGitRemoteDialog = false }
        )
    }

    vm.pendingGitAction?.let { act ->
        val (title, body, confirmLabel) = when (act.kind) {
            EggViewModel.GitActionKind.DISCARD_FILE -> if (act.untracked) Triple(
                "Delete untracked file",
                "Delete local file '${act.relPath}'? It is not tracked by Git.",
                "Delete") else Triple(
                "Discard changes",
                "Revert '${act.relPath}' to the index version? Local changes will be lost.",
                "Discard")
            EggViewModel.GitActionKind.DISCARD_ALL -> Triple(
                "Discard All",
                "Revert all unstaged changes and delete all untracked files? This cannot be undone.",
                "Discard All")
        }
        AlertDialog(
            onDismissRequest = { vm.gitCancelPending() },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = { vm.gitConfirmPending() }) { Text(confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { vm.gitCancelPending() }) { Text("Cancel") }
            }
        )
    }

    if (vm.showAppStorageDialog) {
        AlertDialog(
            onDismissRequest = { vm.showAppStorageDialog = false },
            title = { Text("Open from App Storage") },
            text = {
                if (vm.appStorageItems.isEmpty()) {
                    Text("No cloned repositories or pulled buckets yet.")
                } else {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(vm.appStorageItems.size) { idx ->
                            val (label, path) = vm.appStorageItems[idx]
                            TextButton(onClick = { vm.openPrivateRoot(label, path) }) {
                                Text(label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.showAppStorageDialog = false }) { Text("Cancel") }
            }
        )
    }

}