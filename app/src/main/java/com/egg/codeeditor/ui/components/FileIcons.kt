package com.egg.codeeditor.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.vectorResource
import com.egg.codeeditor.R

/** Spesifikasi ikon: ImageVector + warna. */
internal data class IconSpec(val icon: ImageVector, val color: Color)

/** Palet warna folder: disiklus berdasarkan kedalaman. */
internal val FOLDER_PALETTE = listOf(
    Color(0xFFFFB74D), Color(0xFF4FC3F7), Color(0xFF81C784),
    Color(0xFFBA68C8), Color(0xFFE57373), Color(0xFF4DB6AC),
    Color(0xFFFFD54F), Color(0xFF7986CB)
)

internal fun folderIconSpec(depth: Int): IconSpec =
    IconSpec(Icons.Default.Folder, FOLDER_PALETTE[depth % FOLDER_PALETTE.size])

/** Ikon file sesuai jenis/bahasa, bukan satu ikon monoton. */
@Composable
internal fun fileIconSpec(fileName: String): IconSpec {
    val n = fileName.lowercase()
    val dark = isSystemInDarkTheme()
    return when {
        n.endsWith(".js") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_js_dark else R.drawable.ic_file_js), Color.Unspecified)
        n.endsWith(".ts") || n.endsWith(".tsx") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_ts_dark else R.drawable.ic_file_ts), Color.Unspecified)
        n.endsWith(".kt") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_kt_dark else R.drawable.ic_file_kt), Color.Unspecified)
        n.endsWith(".kts") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_kts_dark else R.drawable.ic_file_kts), Color.Unspecified)
        n.endsWith(".java") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_java_dark else R.drawable.ic_file_java), Color.Unspecified)
        n.endsWith(".py") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_py_dark else R.drawable.ic_file_py), Color.Unspecified)
        n.endsWith(".html") || n.endsWith(".htm") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_html_dark else R.drawable.ic_file_html), Color.Unspecified)
        n.endsWith(".css") || n.endsWith(".scss") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_css_dark else R.drawable.ic_file_css), Color.Unspecified)
        n.endsWith(".json") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_json_dark else R.drawable.ic_file_json), Color.Unspecified)
        n.endsWith(".md") || n.endsWith(".markdown") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_md_dark else R.drawable.ic_file_md), Color.Unspecified)
        n.endsWith(".xml") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_xml_dark else R.drawable.ic_file_xml), Color.Unspecified)
        n.endsWith(".php") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_php_dark else R.drawable.ic_file_php), Color.Unspecified)
        n.endsWith(".sql") -> IconSpec(ImageVector.vectorResource(if (dark) R.drawable.ic_file_sql_dark else R.drawable.ic_file_sql), Color.Unspecified)
        n.endsWith(".kt") || n.endsWith(".kts") || n.endsWith(".java") ||
                n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".py") ||
                n.endsWith(".sh") || n.endsWith(".cpp") || n.endsWith(".c") ||
                n.endsWith(".h") || n.endsWith(".rs") || n.endsWith(".go") ||
                n.endsWith(".rb") || n.endsWith(".php") ->
            IconSpec(Icons.Default.Code, Color(0xFF64B5F6))
        n.endsWith(".json") || n.endsWith(".xml") || n.endsWith(".yaml") ||
                n.endsWith(".yml") || n.endsWith(".toml") ->
            IconSpec(Icons.Default.DataObject, Color(0xFFFFD54F))
        n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".css") ||
                n.endsWith(".scss") ->
            IconSpec(Icons.Default.Language, Color(0xFFFF8A65))
        n.endsWith(".md") || n.endsWith(".txt") ->
            IconSpec(Icons.Default.Notes, Color(0xFF90A4AE))
        n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") ||
                n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".svg") ->
            IconSpec(Icons.Default.Image, Color(0xFF81C784))
        n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".flac") ||
                n.endsWith(".ogg") ->
            IconSpec(Icons.Default.Audiotrack, Color(0xFFBA68C8))
        n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") ||
                n.endsWith(".mov") ->
            IconSpec(Icons.Default.Movie, Color(0xFF4DB6AC))
        n.endsWith(".pdf") ->
            IconSpec(Icons.Default.PictureAsPdf, Color(0xFFE57373))
        n.endsWith(".zip") || n.endsWith(".tar") || n.endsWith(".gz") ||
                n.endsWith(".7z") || n.endsWith(".rar") ->
            IconSpec(Icons.Default.FolderZip, Color(0xFFFFB74D))
        n.endsWith(".csv") || n.endsWith(".xls") || n.endsWith(".xlsx") ->
            IconSpec(Icons.Default.TableChart, Color(0xFF81C784))
        n.endsWith(".sql") || n.endsWith(".db") || n.endsWith(".sqlite") ->
            IconSpec(Icons.Default.Storage, Color(0xFF64B5F6))
        n.endsWith(".apk") ->
            IconSpec(Icons.Default.Android, Color(0xFFAED581))
        else -> IconSpec(Icons.Default.Description, Color(0xFF90A4AE))
    }
}

/** Convert any drawable (incl. vector/adaptive-icon foregrounds) into a bitmap for Canvas slicing. */
internal fun drawableToBitmap(drawable: Drawable, sidePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sidePx, sidePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, sidePx, sidePx)
    drawable.draw(canvas)
    return bitmap
}