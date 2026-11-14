package com.egg.codeeditor.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.vectorResource
import com.egg.codeeditor.R

internal data class IconSpec(val icon: ImageVector, val color: Color, val bitmap: androidx.compose.ui.graphics.ImageBitmap? = null)

internal val FOLDER_PALETTE = listOf(
    Color(0xFFFFB74D), Color(0xFF4FC3F7), Color(0xFF81C784),
    Color(0xFFBA68C8), Color(0xFFE57373), Color(0xFF4DB6AC),
    Color(0xFFFFD54F), Color(0xFF7986CB)
)

internal fun folderIconSpec(depth: Int): IconSpec =
    IconSpec(Icons.Default.Folder, FOLDER_PALETTE[depth % FOLDER_PALETTE.size])

@Composable
internal fun fileIconSpec(fileName: String): IconSpec {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext.isNotEmpty()) {
        val ov = com.egg.extension.api.IconPackRegistry.get(ext)
        if (ov != null) {
            val color = ov.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Color.Unspecified
            val bmp = com.egg.extension.api.IconPackRegistry.drawablePath(ov)?.let { f ->
                runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
            }
            if (bmp != null) {
                return IconSpec(Icons.Default.Description, color, bmp)
            }
            if (color != Color.Unspecified) {
                return IconSpec(Icons.Default.Description, color)
            }
        }
    }
    val type = FileTypeManager.fromFileName(fileName)
    val name = type.iconOverride?.get(ext) ?: type.iconName
    val resId = if (name != null) {
        androidx.compose.ui.platform.LocalContext.current.resources.getIdentifier(name, "drawable", androidx.compose.ui.platform.LocalContext.current.packageName)
    } else 0
    val tint = when (type) {
        BuiltinFileType.IMAGE -> Color(0xFF81C784)
        BuiltinFileType.AUDIO -> Color(0xFFBA68C8)
        BuiltinFileType.VIDEO -> Color(0xFF4DB6AC)
        BuiltinFileType.ARCHIVE -> Color(0xFFFFB74D)
        BuiltinFileType.APK -> Color(0xFFAED581)
        BuiltinFileType.MARKDOWN -> Color(0xFF42A5F5)
        BuiltinFileType.CSS, BuiltinFileType.SCSS, BuiltinFileType.LESS -> Color(0xFF1572B6)
        BuiltinFileType.XML -> Color(0xFFE37933)
        BuiltinFileType.HTML, BuiltinFileType.HTMX -> Color(0xFFE34F26)
        BuiltinFileType.JAVASCRIPT, BuiltinFileType.JSX -> Color(0xFFF7DF1E)
        BuiltinFileType.TYPESCRIPT, BuiltinFileType.TSX -> Color(0xFF3178C6)
        BuiltinFileType.KOTLIN -> Color(0xFF7F52FF)
        BuiltinFileType.JAVA -> Color(0xFFE76F00)
        BuiltinFileType.PYTHON -> Color(0xFF3776AB)
        BuiltinFileType.PHP -> Color(0xFF777BB4)
        BuiltinFileType.SQL -> Color(0xFF4479A1)
        else -> Color(0xFF90A4AE)
    }
    if (resId != 0) {
        return IconSpec(ImageVector.vectorResource(resId), tint)
    }
    return when {
        ext in listOf("png","jpg","jpeg","gif","webp","svg") -> IconSpec(Icons.Default.Image, Color(0xFF81C784))
        ext in listOf("mp3","wav","flac","ogg") -> IconSpec(Icons.Default.Audiotrack, Color(0xFFBA68C8))
        ext in listOf("mp4","mkv","avi","mov") -> IconSpec(Icons.Default.Movie, Color(0xFF4DB6AC))
        ext == "pdf" -> IconSpec(Icons.Default.PictureAsPdf, Color(0xFFE57373))
        ext in listOf("zip","tar","gz","7z","rar") -> IconSpec(Icons.Default.FolderZip, Color(0xFFFFB74D))
        ext in listOf("csv","xls","xlsx") -> IconSpec(Icons.Default.TableChart, Color(0xFF81C784))
        ext in listOf("db","sqlite") -> IconSpec(Icons.Default.Storage, Color(0xFF64B5F6))
        else -> IconSpec(Icons.Default.Description, Color(0xFF90A4AE))
    }
}

internal fun drawableToBitmap(drawable: Drawable, sidePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sidePx, sidePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, sidePx, sidePx)
    drawable.draw(canvas)
    return bitmap
}