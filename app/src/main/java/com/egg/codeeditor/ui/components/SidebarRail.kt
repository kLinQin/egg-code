package com.egg.codeeditor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bottom bar of the sidebar rail: shows only the version; height 36dp like the other bars.
 *  Garis pemisah di ATAS, warna tetap surfaceVariant (tidak diubah). */
@Composable
internal fun RailStatusBar(versionText: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Garis di ATAS, sama seperti SectionStatusBar
        HorizontalDivider()

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant, // ← warna TIDAK diubah
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    versionText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}