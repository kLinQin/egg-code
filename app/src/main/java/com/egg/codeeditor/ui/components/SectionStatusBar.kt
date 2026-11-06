package com.egg.codeeditor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.model.FileNode
import com.egg.codeeditor.model.GitStatusInfo
import org.eclipse.jgit.api.Git

/** Bottom bar of a panel (File Manager/Git/Cloud): height 36dp, same as EditorStatusBar.
 *  Warna mengikuti header (surface) dengan garis pemisah di ATAS. */
@Composable
internal fun SectionStatusBar(
    section: String,
    rootFolderPath: String?,
    visibleNodes: List<FileNode>,
    gitRepo: Git?,
    gitStatus: GitStatusInfo?,
    cloudBucketName: String?,
    activeFileName: String? = null
) {
    val branchName = remember(gitRepo) {
        runCatching { gitRepo?.repository?.branch }.getOrNull()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Garis di ATAS (kebalikan header yang garisnya di bawah)
        HorizontalDivider()

        Surface(
            color = MaterialTheme.colorScheme.surface, // ← sama dengan warna header
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (section) {
                    "File Manager" -> {
                        val folderText = rootFolderPath ?: activeFileName ?: "No project opened"
                        val files = visibleNodes.count { !it.isDirectory }
                        val dirs = visibleNodes.count { it.isDirectory }
                        Text(
                            folderText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        if (rootFolderPath != null) {
                            Text(
                                "$files files · $dirs folders",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    "Git" -> {
                        Text(
                            branchName ?: "No branch",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (gitStatus != null) {
                            val added = gitStatus.added.size + gitStatus.untracked.size
                            val removed = gitStatus.removed.size + gitStatus.missing.size
                            Text(
                                "${gitStatus.modified.size} modified, $added added, $removed deleted",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "No repository yet",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        Text(
                            "Cloud Storage",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            cloudBucketName ?: "Not connected",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}