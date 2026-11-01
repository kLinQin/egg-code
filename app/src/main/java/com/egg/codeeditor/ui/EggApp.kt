package com.egg.codeeditor.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.egg.codeeditor.MainActivity
import com.egg.codeeditor.R
import com.egg.codeeditor.model.EditorSettings
import com.egg.codeeditor.model.EditorTab
import com.egg.codeeditor.model.FileNode
import com.egg.codeeditor.model.GitStatusInfo
import com.egg.codeeditor.model.rememberCodeEditorState
import com.egg.codeeditor.ui.components.CloudActionBar
import com.egg.codeeditor.ui.components.EditorStatusBar
import com.egg.codeeditor.ui.components.EditorTabBar
import com.egg.codeeditor.ui.components.FileManagerActionBar
import com.egg.codeeditor.ui.components.FindBar
import com.egg.codeeditor.ui.components.GitActionBar
import com.egg.codeeditor.ui.components.InputDialog
import com.egg.codeeditor.ui.components.SettingsDialog
import com.egg.codeeditor.ui.components.SidebarButton
import com.egg.codeeditor.ui.components.TerminalShortcutBar
import com.egg.codeeditor.ui.components.TreeGuides
import com.egg.codeeditor.ui.editor.SoraCodeEditor
import com.egg.codeeditor.ui.editor.applyColorScheme
import com.egg.codeeditor.ui.cloud.CloudPanel
import com.egg.codeeditor.ui.cloud.CloudConnectDialog
import com.egg.codeeditor.ui.git.GitPanel
import com.egg.codeeditor.ui.git.GitRemoteDialog
import com.egg.codeeditor.ui.theme.EggSystemBarStyle
import com.egg.codeeditor.ui.theme.EggTheme
import com.egg.codeeditor.util.B2Account
import com.egg.codeeditor.util.B2Bucket
import com.egg.codeeditor.util.B2FileInfo
import com.egg.codeeditor.util.CloudChange
import com.egg.codeeditor.util.GitCommitInfo
import com.egg.codeeditor.util.b2Authorize
import com.egg.codeeditor.util.b2DownloadBytes
import com.egg.codeeditor.util.b2FindBucket
import com.egg.codeeditor.util.b2ListFileVersions
import com.egg.codeeditor.util.b2ListFiles
import com.egg.codeeditor.util.b2UploadFile
import com.egg.codeeditor.util.computeCloudChanges
import com.egg.codeeditor.util.findOrCreateDocumentFile
import com.egg.codeeditor.util.getFileName
import com.egg.codeeditor.util.getGitRemoteUrl
import com.egg.codeeditor.util.getPathFromTreeUri
import com.egg.codeeditor.util.readFileContent
import com.egg.codeeditor.util.readGitLog
import com.egg.codeeditor.util.readGitStatus
import com.egg.codeeditor.util.writeFileContent
import id.or.oo.pr.engine.*
import java.io.File
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "egg_settings"
private const val KEY_THEME = "theme_name"
private const val KEY_FONT_SIZE = "font_size_sp"
private const val KEY_LINE_NUMBERS = "show_line_numbers"
private const val KEY_WORD_WRAP = "word_wrap"
private const val KEY_TAB_WIDTH = "tab_width"
private const val KEY_SHOW_BOTTOM_BAR = "show_bottom_bar"
private const val KEY_FONT_FAMILY = "font_family"
private const val KEY_CUSTOM_FONT_PATH = "custom_font_path"
private const val KEY_CONFIRM_EXIT = "confirm_exit"
private const val KEY_AUTO_INDENT = "auto_indent"
private const val KEY_AUTOCOMPLETE = "autocomplete"
private const val KEY_INDENT_GUIDE = "indent_guide"
private const val KEY_HIGHLIGHT_ACTIVE_LINE = "highlight_active_line"
private const val KEY_CLOSE_BRACKET = "close_bracket"

// ── Terminal session notification ──
private const val TERMINAL_CHANNEL_ID = "terminal_session"
private const val TERMINAL_NOTIFICATION_ID = 9001
private const val ACTION_CLOSE_TERMINAL = "com.egg.codeeditor.CLOSE_TERMINAL"

/** App's GitHub repository URL. Fill with the real URL; if empty,
 *  the GitHub row in the "About" dialog is not shown. */
private const val GITHUB_URL = ""

/** True once the splash has been shown for this process (cold start only). */
private var splashShownForProcess = false

private fun loadSettings(context: Context): EditorSettings {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return EditorSettings(
        themeName = prefs.getString(KEY_THEME, "github-light") ?: "github-light",
        fontSizeSp = prefs.getInt(KEY_FONT_SIZE, 14),
        showLineNumbers = prefs.getBoolean(KEY_LINE_NUMBERS, true),
        wordWrap = prefs.getBoolean(KEY_WORD_WRAP, false),
        tabWidth = prefs.getInt(KEY_TAB_WIDTH, 4),
        showBottomBar = prefs.getBoolean(KEY_SHOW_BOTTOM_BAR, true),
        fontFamily = prefs.getString(KEY_FONT_FAMILY, "monospace") ?: "monospace",
        customFontPath = prefs.getString(KEY_CUSTOM_FONT_PATH, null),
        confirmExit = prefs.getBoolean(KEY_CONFIRM_EXIT, true),
        autoIndent = prefs.getBoolean(KEY_AUTO_INDENT, true),
        autocomplete = prefs.getBoolean(KEY_AUTOCOMPLETE, true),
        indentGuide = prefs.getBoolean(KEY_INDENT_GUIDE, true),
        highlightActiveLine = prefs.getBoolean(KEY_HIGHLIGHT_ACTIVE_LINE, true),
        closeBracket = prefs.getBoolean(KEY_CLOSE_BRACKET, true)
    )
}

private fun saveSettings(context: Context, settings: EditorSettings) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME, settings.themeName)
        .putInt(KEY_FONT_SIZE, settings.fontSizeSp)
        .putBoolean(KEY_LINE_NUMBERS, settings.showLineNumbers)
        .putBoolean(KEY_WORD_WRAP, settings.wordWrap)
        .putInt(KEY_TAB_WIDTH, settings.tabWidth)
        .putBoolean(KEY_SHOW_BOTTOM_BAR, settings.showBottomBar)
        .putString(KEY_FONT_FAMILY, settings.fontFamily)
        .putString(KEY_CUSTOM_FONT_PATH, settings.customFontPath)
        .putBoolean(KEY_CONFIRM_EXIT, settings.confirmExit)
        .putBoolean(KEY_AUTO_INDENT, settings.autoIndent)
        .putBoolean(KEY_AUTOCOMPLETE, settings.autocomplete)
        .putBoolean(KEY_INDENT_GUIDE, settings.indentGuide)
        .putBoolean(KEY_HIGHLIGHT_ACTIVE_LINE, settings.highlightActiveLine)
        .putBoolean(KEY_CLOSE_BRACKET, settings.closeBracket)
        .apply()
}

/** Create the notification channel for the terminal session (Android 8+). */
private fun ensureTerminalChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(TERMINAL_CHANNEL_ID, "Terminal Session", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Shows the active terminal session"
    }
    nm.createNotificationChannel(channel)
}

/** Show the "active session" notification in the notification drawer, with a Close Session action. */
private fun postTerminalNotification(context: Context, folderPath: String?) {
    ensureTerminalChannel(context)

    // "Close Session" action → sent to the dynamic BroadcastReceiver in EggApp
    val closeIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(ACTION_CLOSE_TERMINAL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Tap notification → reopen the app
    val openIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Detect the currently active File Manager folder
    val text = folderPath?.let { "Active folder: $it" } ?: "Alpine shell is running"

    val notification = NotificationCompat.Builder(context, TERMINAL_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Alpine Terminal — Session Active")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOngoing(true)
        .setContentIntent(openIntent)
        .addAction(0, "Close Session", closeIntent)
        .setAutoCancel(false)
        .build()

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(TERMINAL_NOTIFICATION_ID, notification)
}

/** Remove the terminal session notification. */
private fun cancelTerminalNotification(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancel(TERMINAL_NOTIFICATION_ID)
}

/** Escape a path for safe use inside a single-quoted POSIX shell string. */
private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/** Build a `cd` command that prefers the /mnt/sdcard bind, then falls back to other aliases. */
private fun buildCdCommand(androidPath: String): String {
    val suffix = androidPath.removePrefix("/storage/emulated/0")
    val candidates = listOf(
        "/mnt/sdcard$suffix",
        androidPath,
        "/sdcard$suffix"
    ).filter { it.isNotEmpty() }.distinct()
    return candidates.joinToString(" || ") { "cd ${shellQuote(it)} 2>/dev/null" }
}

/** Spesifikasi ikon: ImageVector + warna. */
private data class IconSpec(val icon: ImageVector, val color: Color)

/** Palet warna folder: disiklus berdasarkan kedalaman. */
private val FOLDER_PALETTE = listOf(
    Color(0xFFFFB74D), Color(0xFF4FC3F7), Color(0xFF81C784),
    Color(0xFFBA68C8), Color(0xFFE57373), Color(0xFF4DB6AC),
    Color(0xFFFFD54F), Color(0xFF7986CB)
)

private fun folderIconSpec(depth: Int): IconSpec =
    IconSpec(Icons.Default.Folder, FOLDER_PALETTE[depth % FOLDER_PALETTE.size])

/** Ikon file sesuai jenis/bahasa, bukan satu ikon monoton. */
private fun fileIconSpec(fileName: String): IconSpec {
    val n = fileName.lowercase()
    return when {
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
private fun drawableToBitmap(drawable: Drawable, sidePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sidePx, sidePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, sidePx, sidePx)
    drawable.draw(canvas)
    return bitmap
}

/** Zigzag crack line from the top of the egg down to the bottom. */
private fun crackPoints(w: Float, h: Float): List<Offset> = listOf(
    Offset(w * 0.50f, h * 0.04f),
    Offset(w * 0.44f, h * 0.16f),
    Offset(w * 0.55f, h * 0.28f),
    Offset(w * 0.46f, h * 0.40f),
    Offset(w * 0.57f, h * 0.52f),
    Offset(w * 0.48f, h * 0.64f),
    Offset(w * 0.56f, h * 0.78f),
    Offset(w * 0.50f, h * 0.92f)
)

/** Left or right half of the egg, bounded by the crack line. */
private fun buildHalfPath(pts: List<Offset>, left: Boolean, w: Float, h: Float): Path {
    val path = Path()
    if (left) {
        path.moveTo(0f, 0f)
        path.lineTo(pts.first().x, pts.first().y)
        for (p in pts.drop(1)) path.lineTo(p.x, p.y)
        path.lineTo(0f, h)
    } else {
        path.moveTo(w, 0f)
        path.lineTo(pts.first().x, pts.first().y)
        for (p in pts.drop(1)) path.lineTo(p.x, p.y)
        path.lineTo(w, h)
    }
    path.close()
    return path
}

/** Splash overlay shown once per process (cold start). Mengikuti tema aktif (MaterialTheme). */
@Composable
private fun SplashOverlay() {
    val bgColor = MaterialTheme.colorScheme.surface
    val fgColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary

    val bobTransition = rememberInfiniteTransition(label = "splash_bob")
    val bob by bobTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "bob"
    )

    val waveTransition = rememberInfiniteTransition(label = "splash_wave")
    val wave by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )

    // Single 2s progress drives the whole egg-crack sequence.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
    }

    // Decode via Drawable → Bitmap, because ic_launcher_foreground is a VECTOR drawable
    // (BitmapFactory.decodeResource would return null → NPE → crash on cold start).
    val context = LocalContext.current
    val eggBitmap = remember {
        val drawable = context.getDrawable(R.drawable.ic_launcher_foreground)
        if (drawable != null) drawableToBitmap(drawable, 512).asImageBitmap() else null
    }

    // 0.10–0.45: rapid jitter (shake) before cracking, decaying to zero.
    val shake = ((progress.value - 0.10f) / 0.35f).coerceIn(0f, 1f)
    val shakeAmp = (sin(shake * PI * 14) * (1f - shake) * 10f).toFloat()

    // 0.30–0.55: crack line draws from top to bottom.
    val crackT = ((progress.value - 0.30f) / 0.25f).coerceIn(0f, 1f)

    // 0.55–1.00: halves slide apart with a fast-out easing.
    val splitT = ((progress.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val splitEased = if (splitT <= 0f) 0f else 1f - (1f - splitT).pow(2)

    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Egg: bob → shake → crack → split
            Canvas(
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        translationY = bob * density
                        translationX = shakeAmp * density
                    }
            ) {
                val w = size.width
                val h = size.height
                val pts = crackPoints(w, h)
                val leftOff = -splitEased * w * 0.14f
                val rightOff = splitEased * w * 0.14f
                val alpha = 1f - 0.4f * splitEased
                if (eggBitmap != null) {
                    val srcSize = IntSize(eggBitmap.width, eggBitmap.height)
                    val dstSize = IntSize(w.toInt(), h.toInt())

                    // Left half
                    withTransform({ translate(leftOff, 0f) }) {
                        clipPath(buildHalfPath(pts, left = true, w = w, h = h)) {
                            drawImage(eggBitmap, srcSize = srcSize, dstSize = dstSize, alpha = alpha)
                        }
                    }

                    // Right half
                    withTransform({ translate(rightOff, 0f) }) {
                        clipPath(buildHalfPath(pts, left = false, w = w, h = h)) {
                            drawImage(eggBitmap, srcSize = srcSize, dstSize = dstSize, alpha = alpha)
                        }
                    }
                }

                // Crack stroke, revealed incrementally
                if (crackT > 0f) {
                    val total = pts.size - 1
                    val idx = (total * crackT).toInt().coerceIn(0, total)
                    val frac = total * crackT - idx
                    val linePts = pts.take(idx + 1).toMutableList()
                    if (idx < total) {
                        val a = pts[idx]
                        val b = pts[idx + 1]
                        linePts.add(Offset(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac))
                    }
                    val crackPath = Path().apply {
                        moveTo(linePts.first().x, linePts.first().y)
                        for (i in 1 until linePts.size) lineTo(linePts[i].x, linePts[i].y)
                    }
                    drawPath(
                        crackPath,
                        color = accentColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "EGG",
                color = fgColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )
            Spacer(Modifier.height(16.dp))

            // Wave loading indicator: three dots rising & falling in sequence
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { i ->
                    val phase = (2f * PI).toFloat() * i / 3f
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer { translationY = (sin(wave + phase) * 8f * density).toFloat() }
                            .background(accentColor, CircleShape)
                    )
                }
            }
        }
    }
}

/** Bottom bar of a panel (File Manager/Git/Cloud): height 36dp, same as EditorStatusBar. */
@Composable
private fun SectionStatusBar(
    section: String,
    rootFolderPath: String?,
    visibleNodes: List<FileNode>,
    gitRepo: Git?,
    gitStatus: GitStatusInfo?,
    cloudBucketName: String?
) {
    val branchName = remember(gitRepo) {
        runCatching { gitRepo?.repository?.branch }.getOrNull()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (section) {
                "File Manager" -> {
                    val folderText = rootFolderPath ?: "No project opened"
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
                    Text(
                        "$files files · $dirs folders",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

/** Bottom bar of the sidebar rail: shows only the version; height 36dp like the other bars. */
@Composable
private fun RailStatusBar(versionText: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EggApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val clipboardManager = LocalClipboardManager.current

    // Splash screen (shown once per process — cold start only)
    var showSplash by remember { mutableStateOf(!splashShownForProcess) }
    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(2.seconds)
            splashShownForProcess = true
            showSplash = false
        }
    }

    var terminalVisible by remember { mutableStateOf(false) }
    var terminalEmulator by remember { mutableStateOf<TerminalEmulator?>(null) }
    var terminalSession by remember { mutableStateOf<PdnTerminalSession?>(null) }

    // Focus & keyboard for the Terminal (needed so the IME appears when the terminal opens)
    val terminalFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // ── Terminal session notification: permission + receiver for "Close Session" action ──
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* permission result does not need processing */ }

    val closeTerminalReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_CLOSE_TERMINAL) {
                    terminalVisible = false // close terminal → notification gets cleaned up too
                }
            }
        }
    }
    DisposableEffect(Unit) {
        val filter = IntentFilter(ACTION_CLOSE_TERMINAL)
        ContextCompat.registerReceiver(context, closeTerminalReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(closeTerminalReceiver) }
        }
    }

    var currentSection by remember { mutableStateOf("File Manager") }

    var rootFolderUri by remember { mutableStateOf<Uri?>(null) }
    var rootFolderPath by remember { mutableStateOf<String?>(null) }

    // Post / clear notification when terminal opens/closes & when folder changes
    // NOTE: placed AFTER rootFolderPath declaration to avoid unresolved reference.
    LaunchedEffect(terminalVisible, rootFolderPath) {
        if (terminalVisible) {
            // Android 13+ requires POST_NOTIFICATIONS permission for notifications to appear
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            postTerminalNotification(context, rootFolderPath)
        } else {
            cancelTerminalNotification(context)
        }
    }

    var visibleNodes by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var expandedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val folderCache = remember { mutableStateMapOf<String, List<FileNode>>() }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var selectedNode by remember { mutableStateOf<FileNode?>(null) }
    var targetParentNode by remember { mutableStateOf<FileNode?>(null) }

    var gitRepo by remember { mutableStateOf<Git?>(null) }
    var gitStatus by remember { mutableStateOf<GitStatusInfo?>(null) }
    var commitMessage by remember { mutableStateOf("") }
    var gitBusy by remember { mutableStateOf(false) }
    var gitMessage by remember { mutableStateOf<String?>(null) }
    var gitCommits by remember { mutableStateOf<List<GitCommitInfo>>(emptyList()) }
    var gitRemoteUrl by remember { mutableStateOf<String?>(null) }
    var gitUser by remember { mutableStateOf("") }
    var gitToken by remember { mutableStateOf("") }
    var showGitRemoteDialog by remember { mutableStateOf(false) }
    var gitRemoteDialogTitle by remember { mutableStateOf("Clone Repository") }

    var b2Account by remember { mutableStateOf<B2Account?>(null) }
    var b2Bucket by remember { mutableStateOf<B2Bucket?>(null) }
    var cloudChanges by remember { mutableStateOf<List<CloudChange>>(emptyList()) }
    var cloudHistory by remember { mutableStateOf<List<B2FileInfo>>(emptyList()) }
    var cloudBusy by remember { mutableStateOf(false) }
    var cloudMessage by remember { mutableStateOf<String?>(null) }
    var showCloudConnectDialog by remember { mutableStateOf(false) }

    // Editor settings (loaded from SharedPreferences)
    var settings by remember { mutableStateOf(loadSettings(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // App version (used by the rail bottom bar & about dialog)
    val appVersionText = remember {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        "v" + (info?.versionName ?: "?")
    }

    // Auto-save whenever settings change
    LaunchedEffect(settings) {
        saveSettings(context, settings)
    }

    // Search state
    var showFindBar by remember { mutableStateOf(false) }

    // Multi-tab state
    val tabs = remember { mutableStateListOf<EditorTab>() }
    var activeTabId by remember { mutableStateOf<Long?>(null) }
    var nextTabId by remember { mutableStateOf(1L) }
    var pendingCloseTab by remember { mutableStateOf<EditorTab?>(null) }
    var pendingDeleteTab by remember { mutableStateOf<EditorTab?>(null) }
    var pendingCloseAfterSaveId by remember { mutableStateOf<Long?>(null) }
    var pendingSaveAsTabId by remember { mutableStateOf<Long?>(null) }

    val editorState = rememberCodeEditorState()
    val activeTab = tabs.firstOrNull { it.id == activeTabId }

    // Undo/redo toolbar state: polled lightly to stay in sync with Sora's undo stack
    // (changes while typing, undoing, or redoing).
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    LaunchedEffect(editorState.editor) {
        val editor = editorState.editor ?: return@LaunchedEffect
        while (true) {
            canUndo = try { editor.canUndo() } catch (_: Exception) { false }
            canRedo = try { editor.canRedo() } catch (_: Exception) { false }
            delay(150.milliseconds)
        }
    }

    LaunchedEffect(Unit) {
        // Theme & TextMate grammar are loaded synchronously in MainActivity before setContent.
        // Re-apply to the editor if the editor was created first (safe fallback).
        editorState.editor?.let { applyColorScheme(it, settings.themeName) }

        // Default tab on first open
        if (tabs.isEmpty()) {
            val t = EditorTab(nextTabId++, "Untitled 1", null, "")
            tabs.add(t)
            activeTabId = t.id
        }
    }

    val prootHost = remember {
        object : ProotHost {
            override val nativeLibDir: File get() = File(context.applicationInfo.nativeLibraryDir)
            override val prefixDir: File get() = File(context.filesDir, "pdn")
            override val homeDir: File get() = File(context.filesDir, "home")
            override val packageName: String get() = context.packageName
            override val cacheDir: File get() = context.cacheDir
        }
    }

    val pdnRuntime = remember { PdnRuntime(prootHost) }
    val pdnOps = remember { PdnOperations(pdnRuntime) }
    val pdnTerminal = remember { PdnTerminal(pdnRuntime) }

    // ── Tab helpers ──
    fun activeTabIndex(): Int = tabs.indexOfFirst { it.id == activeTabId }

    fun captureActiveTabContent() {
        val i = activeTabIndex()
        if (i >= 0) {
            val text = editorState.editor?.text?.toString() ?: ""
            if (tabs[i].content != text) tabs[i] = tabs[i].copy(content = text)
        }
    }

    fun switchAfterRemoval(removedIdx: Int) {
        if (tabs.isEmpty()) {
            val t = EditorTab(nextTabId++, "Untitled 1", null, "")
            tabs.add(t)
            activeTabId = t.id
        } else {
            activeTabId = tabs[minOf(removedIdx, tabs.lastIndex)].id
        }
    }

    fun reallyCloseTab(id: Long) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val wasActive = activeTabId == id
        tabs.removeAt(idx)
        if (wasActive) switchAfterRemoval(idx)
    }

    fun requestCloseTab(tab: EditorTab) {
        if (tab.isDirty) pendingCloseTab = tab else reallyCloseTab(tab.id)
    }

    fun saveTabAndClose(tab: EditorTab) {
        val i = tabs.indexOfFirst { it.id == tab.id }
        if (i < 0) return
        val t = tabs[i]
        if (t.uri != null) {
            scope.launch {
                writeFileContent(context, t.uri, t.content)
                reallyCloseTab(t.id)
            }
        } else {
            pendingCloseAfterSaveId = t.id
            pendingSaveAsTabId = t.id
            activeTabId = t.id
        }
    }

    fun closeOthers(id: Long) {
        captureActiveTabContent()
        tabs.removeAll { it.id != id }
        activeTabId = id
    }

    fun newUntitledTab() {
        val count = tabs.count { it.uri == null } + 1
        val t = EditorTab(nextTabId++, "Untitled $count", null, "")
        captureActiveTabContent()
        tabs.add(t)
        activeTabId = t.id
    }

    // ── Backup: write settings + all tabs to a JSON file ──
    fun performBackup(uri: Uri) {
        scope.launch {
            captureActiveTabContent()
            try {
                val root = JSONObject()
                val s = JSONObject()
                s.put("themeName", settings.themeName)
                s.put("fontSizeSp", settings.fontSizeSp)
                s.put("showLineNumbers", settings.showLineNumbers)
                s.put("wordWrap", settings.wordWrap)
                s.put("tabWidth", settings.tabWidth)
                s.put("showBottomBar", settings.showBottomBar)
                s.put("fontFamily", settings.fontFamily)
                s.put("customFontPath", settings.customFontPath ?: "")
                s.put("confirmExit", settings.confirmExit)
                s.put("autoIndent", settings.autoIndent)
                s.put("autocomplete", settings.autocomplete)
                s.put("indentGuide", settings.indentGuide)
                s.put("highlightActiveLine", settings.highlightActiveLine)
                s.put("closeBracket", settings.closeBracket)
                root.put("settings", s)

                val arr = JSONArray()
                for (t in tabs) {
                    val o = JSONObject()
                    o.put("name", t.name)
                    t.uri?.toString()?.let { o.put("uri", it) }
                    o.put("content", t.content)
                    arr.put(o)
                }
                root.put("tabs", arr)
                writeFileContent(context, uri, root.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Restore: read JSON, apply settings and rebuild tabs ──
    fun performRestore(uri: Uri) {
        scope.launch {
            try {
                val content = readFileContent(context, uri)
                val root = JSONObject(content)

                root.optJSONObject("settings")?.let { s ->
                    settings = EditorSettings(
                        themeName = s.optString("themeName", settings.themeName),
                        fontSizeSp = s.optInt("fontSizeSp", settings.fontSizeSp),
                        showLineNumbers = s.optBoolean("showLineNumbers", settings.showLineNumbers),
                        wordWrap = s.optBoolean("wordWrap", settings.wordWrap),
                        tabWidth = s.optInt("tabWidth", settings.tabWidth),
                        showBottomBar = s.optBoolean("showBottomBar", settings.showBottomBar),
                        fontFamily = s.optString("fontFamily", settings.fontFamily),
                        customFontPath = s.optString("customFontPath", "").ifEmpty { null },
                        confirmExit = s.optBoolean("confirmExit", settings.confirmExit),
                        autoIndent = s.optBoolean("autoIndent", settings.autoIndent),
                        autocomplete = s.optBoolean("autocomplete", settings.autocomplete),
                        indentGuide = s.optBoolean("indentGuide", settings.indentGuide),
                        highlightActiveLine = s.optBoolean("highlightActiveLine", settings.highlightActiveLine),
                        closeBracket = s.optBoolean("closeBracket", settings.closeBracket)
                    )
                }

                val arr = root.optJSONArray("tabs")
                tabs.clear()
                if (arr != null && arr.length() > 0) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        var tabUri: Uri? = null
                        val uriStr = o.optString("uri", "")
                        if (uriStr.isNotEmpty()) {
                            tabUri = runCatching { Uri.parse(uriStr) }.getOrNull()
                        }
                        val tab = EditorTab(
                            id = nextTabId++,
                            name = o.optString("name", "Untitled"),
                            uri = tabUri,
                            content = o.optString("content", "")
                        )
                        tabs.add(tab)
                    }
                } else {
                    val t = EditorTab(nextTabId++, "Untitled 1", null, "")
                    tabs.add(t)
                }
                activeTabId = tabs.first().id
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Confirm on exit (if enabled in Settings)
    BackHandler(enabled = !drawerState.isOpen) {
        if (settings.confirmExit) {
            showExitConfirm = true
        } else {
            (context as? Activity)?.finish()
        }
    }

    LaunchedEffect(terminalVisible) {
        if (terminalVisible) {
            if (terminalEmulator == null) {
                val emulator = TerminalEmulatorFactory.create(
                    onKeyboardInput = { data ->
                        if (terminalSession != null) {
                            try {
                                terminalSession?.write(data)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    // Sync the PTY session size whenever the on-screen emulator resizes
                    onResize = { dims ->
                        scope.launch {
                            try {
                                terminalSession?.resize(dims.rows, dims.columns)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                )

                terminalEmulator = emulator
            }

            val emulator = terminalEmulator ?: return@LaunchedEffect

            fun printToScreen(msg: String) {
                val b = msg.toByteArray(Charsets.UTF_8)
                emulator.writeInput(b, 0, b.size)
            }

            val alpineDir = File(context.filesDir, "pdn/var/lib/pdn/rootfs/alpine")
            if (!alpineDir.exists()) {
                try {
                    printToScreen("Installing Alpine (automatic)...\r\n")
                    val listener = object : PdnListener {
                        override fun onEvent(event: PdnEvent) {}
                    }
                    val installBuilder = pdnRuntime.install("alpine")
                    pdnOps.run(installBuilder, listener)
                    printToScreen("Installation complete. Starting shell...\r\n")
                } catch (e: Exception) {
                    printToScreen("Install failed: ${e.message}\r\n")
                }
            }

            if (terminalSession == null) {
                try {
                    // Android 11+ (API 30+): tanpa izin "All files access", storage tampak
                    // kosong/filtered di dalam sandbox (scoped storage). Minta akses penuh.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        printToScreen("Grant All Files Access to see your storage...\r\n")
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }.recoverCatching {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    }

                    printToScreen("Starting Alpine shell...\r\n")
                    // Bind Android storage ke sandbox agar terminal bisa mengakses folder File Manager.
                    // /storage/emulated/0 → /mnt/sdcard (diteruskan PRoot sebagai --bind).
                    val pdnConfig = PdnConfiguration(
                        "root",
                        "/workspace",
                        listOf(PdnBind(File("/storage/emulated/0"), "/mnt/sdcard")),
                        emptyMap()
                    )
                    val loginBuilder = pdnRuntime.login("alpine", pdnConfig)
                    val listener = object : PdnTerminalListener {}
                    val session = pdnTerminal.start(loginBuilder, 80, 24, listener)
                    terminalSession = session

                    // Sync the initial session size with the emulator's current dimensions
                    try {
                        session.resize(emulator.dimensions.rows, emulator.dimensions.columns)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 💡 Langsung pindah ke folder yang sedang aktif di File Manager.
                    // Utamakan /mnt/sdcard (bind), lalu path asli, lalu /sdcard; kalau semuanya
                    // gagal, tetap di home default.
                    rootFolderPath?.let { folder ->
                        val cdCmd = buildCdCommand(folder)
                        scope.launch {
                            delay(600.milliseconds) // tunggu prompt shell siap
                            try {
                                terminalSession?.write((cdCmd + "\r\n").toByteArray())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    scope.launch(Dispatchers.IO) {
                        val buffer = ByteArray(4096)
                        try {
                            while (!session.isClosed) {
                                val read = session.read(buffer)
                                if (read > 0) {
                                    emulator.writeInput(buffer, 0, read)
                                } else if (read < 0) {
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            val msg = "\r\n[Terminal closed: ${e.message}]\r\n"
                            val b = msg.toByteArray(Charsets.UTF_8)
                            emulator.writeInput(b, 0, b.size)
                        }
                    }
                } catch (e: Exception) {
                    printToScreen("Failed to start shell: ${e.message}\r\n")
                }
            }
        }
    }

    // Force the keyboard (IME) to appear when the terminal opens — keyed to the emulator too,
    // so it only runs after Terminal is actually composed.
    LaunchedEffect(terminalVisible, terminalEmulator) {
        if (terminalVisible && terminalEmulator != null) {
            delay(200.milliseconds)
            terminalFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    suspend fun getChildren(uri: Uri, depth: Int): List<FileNode> {
        val path = uri.toString()
        folderCache[path]?.let { return it }
        val nodes = withContext(Dispatchers.IO) {
            try {
                val doc = DocumentFile.fromTreeUri(context, uri)
                val files = doc?.listFiles() ?: emptyArray()
                files.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
                    .map { d -> FileNode(d.name ?: "Unknown", d.isDirectory, d.uri, depth) }
            } catch (_: Exception) { emptyList() }
        }
        folderCache[path] = nodes
        return nodes
    }

    suspend fun rebuildVisibleNodes() {
        val rootUri = rootFolderUri ?: return
        val result = mutableListOf<FileNode>()
        suspend fun walk(uri: Uri, depth: Int) {
            val children = getChildren(uri, depth)
            for (node in children) {
                result.add(node)
                if (node.isDirectory && expandedPaths.contains(node.uri.toString())) walk(node.uri, depth + 1)
            }
        }
        walk(rootUri, 0)
        visibleNodes = result
    }

    // Hitung untuk tiap node: di level mana saja garis vertikal harus diteruskan
    // ke bawah. Level l lanjut jika masih ada node SETELAH ini yang kedalamannya
    // > l (masih di dalam subtree yang sama). Scan dari kanan ke kiri.
    val guidesByUri = remember(visibleNodes) {
        val laterHasBelow = mutableMapOf<Int, Boolean>()
        val result = mutableMapOf<String, List<Boolean>>()
        for (i in visibleNodes.indices.reversed()) {
            val node = visibleNodes[i]
            result[node.uri.toString()] = (0 until node.depth).map { l -> laterHasBelow[l] == true }
            // Node ini menjadi "di bawah" bagi baris-baris di atasnya:
            // tandai semua level nenek-moyangnya.
            for (l in 0 until node.depth) laterHasBelow[l] = true
        }
        result
    }

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                rootFolderUri = it
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                rootFolderPath = getPathFromTreeUri(it)
                expandedPaths = emptySet()
                folderCache.clear()

                tabs.clear()
                val t = EditorTab(nextTabId++, "Untitled 1", null, "")
                tabs.add(t)
                activeTabId = t.id

                gitRepo = null
                gitStatus = null
                gitCommits = emptyList()
                gitRemoteUrl = null
                gitUser = ""
                gitToken = ""
                b2Account = null
                b2Bucket = null
                cloudChanges = emptyList()
                cloudHistory = emptyList()
                cloudMessage = null
                rebuildVisibleNodes()
            }
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val i = activeTabIndex()
                if (i >= 0) {
                    val tab = tabs[i]
                    val text = editorState.editor?.text?.toString() ?: ""
                    writeFileContent(context, it, text)
                    tabs[i] = tab.copy(uri = it, name = getFileName(context, it), content = text, isDirty = false)
                }
                pendingCloseAfterSaveId?.let { closeId ->
                    if (tabs.any { it.id == closeId }) reallyCloseTab(closeId)
                    pendingCloseAfterSaveId = null
                    pendingSaveAsTabId = null
                }
            }
        }
    }

    // Trigger the "Save As" dialog when an Untitled tab is asked to be saved
    LaunchedEffect(pendingSaveAsTabId) {
        if (pendingSaveAsTabId != null) {
            val t = tabs.firstOrNull { it.id == pendingSaveAsTabId }
            if (t != null) saveFileLauncher.launch("${t.name}.txt")
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) editorState.editor?.clearFocus()
    }

    // Git action handlers
    val gitInitAction: () -> Unit = {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val newGit = withContext(Dispatchers.IO) { Git.init().setDirectory(File(rootFolderPath!!)).call() }
                gitRepo = newGit; gitStatus = readGitStatus(newGit); gitCommits = readGitLog(newGit); gitRemoteUrl = getGitRemoteUrl(newGit); gitMessage = "Repository initialized successfully."
            } catch (e: Exception) { gitMessage = "Init failed: ${e.message}" } finally { gitBusy = false }
        }
    }
    val gitRefreshAction: () -> Unit = {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: run { withContext(Dispatchers.IO) { Git.open(File(rootFolderPath!!)) }.also { gitRepo = it } }
                gitStatus = readGitStatus(g); gitCommits = readGitLog(g); gitRemoteUrl = getGitRemoteUrl(g)
            } catch (e: Exception) { gitMessage = "Failed to read status: ${e.message}" } finally { gitBusy = false }
        }
    }
    val gitCommitAction: () -> Unit = {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                withContext(Dispatchers.IO) {
                    g.add().addFilepattern(".").call()
                    g.commit().setMessage(commitMessage).setAuthor("Egg User", "egg@local").call()
                }
                commitMessage = ""; gitStatus = readGitStatus(g); gitCommits = readGitLog(g); gitRemoteUrl = getGitRemoteUrl(g); gitMessage = "Commit succeeded."
            } catch (e: Exception) { gitMessage = "Commit failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    val gitDoClone: (String, String, String) -> Unit = { url, user, token ->
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val dest = withContext(Dispatchers.IO) {
                    val repoName = url.trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "cloned-repo" }
                    File(rootFolderPath!!, repoName)
                }
                val cp = if (user.isNotBlank()) UsernamePasswordCredentialsProvider(user, token) else null
                val cloned = withContext(Dispatchers.IO) {
                    Git.cloneRepository()
                        .setURI(url)
                        .setDirectory(dest)
                        .apply { if (cp != null) setCredentialsProvider(cp) }
                        .call()
                }
                gitRepo = cloned
                gitStatus = readGitStatus(cloned)
                gitCommits = readGitLog(cloned)
                gitRemoteUrl = getGitRemoteUrl(cloned)
                gitUser = user; gitToken = token
                gitMessage = "Clone succeeded: ${dest.absolutePath}"
            } catch (e: Exception) { gitMessage = "Clone failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    val gitAddRemote: (String, String, String) -> Unit = { url, user, token ->
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                withContext(Dispatchers.IO) {
                    g.remoteAdd().setName("origin").setUri(URIish(url)).call()
                }
                gitUser = user; gitToken = token
                gitRemoteUrl = url
                gitMessage = "Remote 'origin' added."
            } catch (e: Exception) { gitMessage = "Add remote failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    val gitFetchAction: () -> Unit = {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                withContext(Dispatchers.IO) {
                    val cmd = g.fetch()
                    if (gitUser.isNotBlank() && gitToken.isNotBlank()) cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(gitUser, gitToken))
                    cmd.call()
                }
                gitMessage = "Fetch succeeded."
            } catch (e: Exception) { gitMessage = "Fetch failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    val gitPullAction: () -> Unit = {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                val result = withContext(Dispatchers.IO) {
                    val cmd = g.pull()
                    if (gitUser.isNotBlank() && gitToken.isNotBlank()) cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(gitUser, gitToken))
                    cmd.call()
                }
                gitStatus = readGitStatus(g)
                gitCommits = readGitLog(g)
                gitMessage = if (result.isSuccessful) "Pull succeeded." else "Pull failed: $result"
            } catch (e: Exception) { gitMessage = "Pull failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    val gitPushAction: () -> Unit = {
        scope.launch {
            gitBusy = true; gitMessage = null
            try {
                val g = gitRepo ?: throw IllegalStateException("Repository is not initialized")
                val result = withContext(Dispatchers.IO) {
                    val cmd = g.push()
                    if (gitUser.isNotBlank() && gitToken.isNotBlank()) cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(gitUser, gitToken))
                    cmd.call()
                }
                gitMessage = if (result.iterator().hasNext()) "Push succeeded." else "Push failed: nothing pushed."
            } catch (e: Exception) { gitMessage = "Push failed: ${e.message}" } finally { gitBusy = false }
        }
    }

    // Cloud (B2) action handlers
    val cloudConnect: (String, String, String) -> Unit = { keyId, appKey, bucket ->
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Authorize(keyId, appKey)
                // If the key is restricted to a single bucket, use the bucket from the
                // authorize response (avoids b2_list_buckets, which can return 401 for restricted keys).
                val bucketInfo = when {
                    acc.allowedBucketId != null && acc.allowedBucketName != null ->
                        B2Bucket(acc.allowedBucketId!!, acc.allowedBucketName!!)
                    else -> b2FindBucket(acc, bucket) ?: throw Exception("Bucket '$bucket' not found")
                }
                b2Account = acc; b2Bucket = bucketInfo
                val remote = b2ListFiles(acc, bucketInfo.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remote)
                cloudHistory = b2ListFileVersions(acc, bucketInfo.bucketId)
                cloudMessage = "Connected to bucket '${bucketInfo.bucketName}'."
            } catch (e: Exception) {
                android.util.Log.e("EggB2", "connect failed", e)
                cloudMessage = "Connect failed:\n${e.stackTraceToString().take(1500)}"
            } finally { cloudBusy = false }
        }
    }

    val cloudFetchAction: () -> Unit = {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
                cloudMessage = "Fetch succeeded."
            } catch (e: Exception) { cloudMessage = "Fetch failed: ${e.message}" } finally { cloudBusy = false }
        }
    }

    val cloudRefreshAction: () -> Unit = {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val remote = b2ListFiles(acc, bucket.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remote)
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
            } catch (e: Exception) { cloudMessage = "Failed to read status: ${e.message}" } finally { cloudBusy = false }
        }
    }

    val cloudPushAction: () -> Unit = {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val remote = b2ListFiles(acc, bucket.bucketId)
                val changes = computeCloudChanges(File(rootFolderPath!!), remote)
                var uploaded = 0
                for (c in changes) {
                    val relPath = when (c) {
                        is CloudChange.New -> c.relPath
                        is CloudChange.Modified -> c.relPath
                        is CloudChange.RemoteOnly -> null
                    } ?: continue
                    b2UploadFile(acc, bucket.bucketId, relPath, File(rootFolderPath!!, relPath))
                    uploaded++
                }
                val remoteAfter = b2ListFiles(acc, bucket.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remoteAfter)
                cloudHistory = b2ListFileVersions(acc, bucket.bucketId)
                cloudMessage = if (uploaded > 0) "Push succeeded: $uploaded file(s) uploaded." else "Push succeeded: nothing to upload."
            } catch (e: Exception) { cloudMessage = "Push failed: ${e.message}" } finally { cloudBusy = false }
        }
    }

    val cloudPullAction: () -> Unit = {
        scope.launch {
            cloudBusy = true; cloudMessage = null
            try {
                val acc = b2Account ?: throw IllegalStateException("Not connected")
                val bucket = b2Bucket ?: throw IllegalStateException("Not connected")
                val remote = b2ListFiles(acc, bucket.bucketId)
                val changes = computeCloudChanges(File(rootFolderPath!!), remote)
                var downloaded = 0
                for (c in changes) {
                    if (c !is CloudChange.RemoteOnly) continue
                    val data = b2DownloadBytes(acc, bucket.bucketName, c.relPath)
                    val target = findOrCreateDocumentFile(context, rootFolderUri!!, c.relPath)
                        ?: throw Exception("Cannot create local file: ${c.relPath}")
                    context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(data) }
                        ?: throw Exception("Cannot open output stream: ${c.relPath}")
                    downloaded++
                }
                val remoteAfter = b2ListFiles(acc, bucket.bucketId)
                cloudChanges = computeCloudChanges(File(rootFolderPath!!), remoteAfter)
                cloudMessage = if (downloaded > 0) "Pull succeeded: $downloaded file(s) downloaded." else "Pull succeeded: nothing to pull."
            } catch (e: Exception) { cloudMessage = "Pull failed: ${e.message}" } finally { cloudBusy = false }
        }
    }

    // Global theme: follows the "Theme" setting (Light/Dark), not the system setting
    EggTheme(darkTheme = settings.themeName == "darcula", dynamicColor = false) {
        // Sync status bar & navigation bar:
        // - Terminal open → follows terminal color (black, white icons)
        // - Terminal closed → follows active theme (dark/light)
        EggSystemBarStyle(
            darkTheme = settings.themeName == "darcula" || terminalVisible,
            customBg = if (terminalVisible) 0xFF000000.toInt() else null // black = default terminal
        )

        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                drawerContent = {
                    // Full-width drawer (covers the whole screen when open)
                    ModalDrawerSheet(
                        modifier = Modifier.fillMaxWidth(),
                        drawerShape = RectangleShape,
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                            // ── SIDEBAR RAIL (width 72dp) ──
                            Column(
                                modifier = Modifier.width(72.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // "EGG" title: height matches section header & TopAppBar (40dp)
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "EGG",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Divider aligned with the section header line
                                HorizontalDivider()

                                // Main navigation buttons
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SidebarButton(Icons.Default.Folder, "File", selected = currentSection == "File Manager") { currentSection = "File Manager" }
                                    SidebarButton(Icons.Default.Cloud, "Cloud", selected = currentSection == "Cloud Storage") { currentSection = "Cloud Storage" }
                                    SidebarButton(Icons.Default.AccountTree, "Git", selected = currentSection == "Git") { currentSection = "Git" }
                                }

                                // Push the info button to the bottom
                                Spacer(Modifier.weight(1f))

                                // Info button
                                SidebarButton(Icons.Default.Info, "Info", onClick = { showAboutDialog = true })

                                // Rail bottom bar: version (height 36dp, joins the section bottom bar)
                                RailStatusBar(versionText = appVersionText)
                            }

                            Column(Modifier.fillMaxSize().weight(1f)) {
                                // ── SECTION NAME HEADER (height 40dp, same as TopAppBar) ──
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(40.dp).padding(start = 16.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        currentSection,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { scope.launch { drawerState.close() } }) { Icon(Icons.Default.Close, "Close Drawer") }
                                }
                                HorizontalDivider()

                                // ── ACTION BAR (top) ──
                                val hasActionBar = currentSection == "File Manager" ||
                                        (currentSection == "Git" && rootFolderPath != null) ||
                                        (currentSection == "Cloud Storage" && rootFolderPath != null)
                                if (hasActionBar) {
                                    when (currentSection) {
                                        "File Manager" -> FileManagerActionBar(
                                            rootFolderUri = rootFolderUri,
                                            expandedPaths = expandedPaths,
                                            onOpenFolder = { openFolderLauncher.launch(null) },
                                            onNewFile = { targetParentNode = null; showNewFileDialog = true },
                                            onNewFolder = { targetParentNode = null; showNewFolderDialog = true },
                                            onRefresh = { scope.launch { folderCache.clear(); rebuildVisibleNodes() } },
                                            onToggleCollapse = {
                                                scope.launch {
                                                    if (expandedPaths.isNotEmpty()) expandedPaths = emptySet()
                                                    else {
                                                        val rootUri = rootFolderUri ?: return@launch
                                                        val rootChildren = getChildren(rootUri, 0)
                                                        expandedPaths = rootChildren.filter { it.isDirectory }.map { it.uri.toString() }.toSet()
                                                    }
                                                    rebuildVisibleNodes()
                                                }
                                            },
                                            onCloseProject = {
                                                rootFolderUri = null; rootFolderPath = null; visibleNodes = emptyList(); expandedPaths = emptySet(); folderCache.clear()
                                                tabs.clear()
                                                val t = EditorTab(nextTabId++, "Untitled 1", null, "")
                                                tabs.add(t); activeTabId = t.id
                                                gitRepo = null; gitStatus = null; gitCommits = emptyList(); gitRemoteUrl = null; gitUser = ""; gitToken = ""
                                                b2Account = null; b2Bucket = null; cloudChanges = emptyList(); cloudHistory = emptyList(); cloudMessage = null
                                            }
                                        )
                                        "Git" -> GitActionBar(
                                            gitRepo = gitRepo,
                                            gitBusy = gitBusy,
                                            commitMessage = commitMessage,
                                            remoteUrl = gitRemoteUrl,
                                            onInit = gitInitAction,
                                            onClone = { showGitRemoteDialog = true; gitRemoteDialogTitle = "Clone Repository" },
                                            onRefresh = gitRefreshAction,
                                            onAddRemote = { showGitRemoteDialog = true; gitRemoteDialogTitle = "Add Remote" },
                                            onFetch = gitFetchAction,
                                            onPull = gitPullAction,
                                            onPush = gitPushAction,
                                            onCommit = gitCommitAction
                                        )
                                        "Cloud Storage" -> CloudActionBar(
                                            connected = b2Account != null,
                                            cloudBusy = cloudBusy,
                                            onConnect = { showCloudConnectDialog = true },
                                            onRefresh = cloudRefreshAction,
                                            onFetch = cloudFetchAction,
                                            onPush = cloudPushAction,
                                            onPull = cloudPullAction
                                        )
                                    }
                                }

                                // ── SECTION CONTENT (fills remaining space, free scrolling) ──
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    when (currentSection) {
                                        "File Manager" -> {
                                            if (rootFolderUri == null) {
                                                Text(
                                                    "No folder opened. Choose 'Open Project Folder' above.",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            } else {
                                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                    items(visibleNodes, key = { it.uri.toString() }) { node ->
                                                        var showContextMenu by remember { mutableStateOf(false) }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth()
                                                                .height(48.dp) // ← tinggi baris tetap (harus sama dengan TreeGuides.height)
                                                                .combinedClickable(
                                                                    onClick = {
                                                                        if (node.isDirectory) {
                                                                            scope.launch {
                                                                                val path = node.uri.toString()
                                                                                expandedPaths = if (expandedPaths.contains(path)) expandedPaths - path else expandedPaths + path
                                                                                rebuildVisibleNodes()
                                                                            }
                                                                        } else {
                                                                            scope.launch {
                                                                                val existing = tabs.indexOfFirst { it.uri == node.uri }
                                                                                if (existing >= 0) {
                                                                                    captureActiveTabContent()
                                                                                    activeTabId = tabs[existing].id
                                                                                } else {
                                                                                    val content = readFileContent(context, node.uri)
                                                                                    captureActiveTabContent()
                                                                                    val tab = EditorTab(nextTabId++, node.name, node.uri, content)
                                                                                    tabs.add(tab)
                                                                                    activeTabId = tab.id
                                                                                }
                                                                                drawerState.close()
                                                                            }
                                                                        }
                                                                    },
                                                                    onLongClick = { showContextMenu = true }
                                                                )
                                                                .padding(end = 8.dp), // ← padding vertikal dihapus agar garis tersambung
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            if (node.depth > 0) {
                                                                TreeGuides(
                                                                    depth = node.depth,
                                                                    hasGuide = guidesByUri[node.uri.toString()] ?: emptyList(),
                                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                                    height = 48.dp // ← harus SAMA dengan tinggi Row
                                                                )
                                                            }
                                                            if (node.isDirectory) {
                                                                val isExpanded = expandedPaths.contains(node.uri.toString())
                                                                val folderSpec = folderIconSpec(node.depth)
                                                                Icon(
                                                                    if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                                    "Expand",
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(Modifier.width(6.dp)) // ← jarak panah ↔ folder
                                                                Icon(
                                                                    if (isExpanded) Icons.Default.FolderOpen else folderSpec.icon,
                                                                    null,
                                                                    modifier = Modifier.size(20.dp), // ← offset negatif dihapus
                                                                    tint = folderSpec.color
                                                                )
                                                            } else {
                                                                Spacer(Modifier.width(20.dp))
                                                                val fileSpec = fileIconSpec(node.name)
                                                                Icon(fileSpec.icon, null, tint = fileSpec.color)
                                                            }
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(node.name, maxLines = 1)
                                                        }
                                                        DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
                                                            DropdownMenuItem(text = { Text("Copy Path") }, onClick = { clipboardManager.setText(AnnotatedString(node.uri.toString())); showContextMenu = false })
                                                            DropdownMenuItem(text = { Text("Rename") }, onClick = { selectedNode = node; showRenameDialog = true; showContextMenu = false })
                                                            DropdownMenuItem(text = { Text("Delete") }, onClick = { selectedNode = node; showDeleteDialog = true; showContextMenu = false })
                                                            if (node.isDirectory) {
                                                                DropdownMenuItem(text = { Text("New File Here") }, onClick = { targetParentNode = node; showNewFileDialog = true; showContextMenu = false })
                                                                DropdownMenuItem(text = { Text("New Folder Here") }, onClick = { targetParentNode = node; showNewFolderDialog = true; showContextMenu = false })
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        "Git" -> {
                                            if (rootFolderPath == null) {
                                                Text("Open a project folder first in File Manager.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                GitPanel(
                                                    gitRepo = gitRepo,
                                                    gitStatus = gitStatus,
                                                    gitCommits = gitCommits,
                                                    commitMessage = commitMessage,
                                                    gitBusy = gitBusy,
                                                    gitMessage = gitMessage,
                                                    onCommitMessageChange = { commitMessage = it }
                                                )
                                            }
                                        }
                                        "Cloud Storage" -> {
                                            if (rootFolderPath == null) {
                                                Text("Open a project folder first in File Manager.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else {
                                                CloudPanel(
                                                    connected = b2Account != null,
                                                    cloudChanges = cloudChanges,
                                                    cloudHistory = cloudHistory,
                                                    cloudBusy = cloudBusy,
                                                    cloudMessage = cloudMessage
                                                )
                                            }
                                        }
                                        else -> Text("($currentSection feature coming soon)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                // ── SECTION BOTTOM BAR (height 36dp, same as EditorStatusBar) ──
                                SectionStatusBar(
                                    section = currentSection,
                                    rootFolderPath = rootFolderPath,
                                    visibleNodes = visibleNodes,
                                    gitRepo = gitRepo,
                                    gitStatus = gitStatus,
                                    cloudBucketName = b2Bucket?.bucketName
                                )
                            }
                        }
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {}, // file name removed — already shown in the TabBar
                            modifier = Modifier.height(40.dp),
                            navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Open drawer") } },
                            actions = {
                                // Search: opens the FindBar in the active file directly, no menu
                                IconButton(onClick = {
                                    if (activeTabId != null) showFindBar = true
                                }) { Icon(Icons.Default.Search, "Search") }
                                // Undo: Claude orange when enabled, dimmed when unavailable
                                IconButton(
                                    onClick = {
                                        try { editorState.editor?.undo() } catch (e: Exception) { e.printStackTrace() }
                                    },
                                    enabled = canUndo,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                                // Redo: Claude orange when enabled, dimmed when unavailable
                                IconButton(
                                    onClick = {
                                        try { editorState.editor?.redo() } catch (e: Exception) { e.printStackTrace() }
                                    },
                                    enabled = canRedo,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                                // Save: Claude orange when there are unsaved changes, dimmed otherwise
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val i = activeTabIndex()
                                            if (i >= 0) {
                                                val tab = tabs[i]
                                                if (tab.uri != null) {
                                                    val text = editorState.editor?.text?.toString() ?: ""
                                                    writeFileContent(context, tab.uri, text)
                                                    tabs[i] = tabs[i].copy(content = text, isDirty = false)
                                                } else {
                                                    saveFileLauncher.launch("${tab.name}.txt")
                                                }
                                            }
                                        }
                                    },
                                    enabled = activeTab?.isDirty == true,
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) { Icon(Icons.Default.Save, "Save") }
                                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings") }
                                IconButton(onClick = { terminalVisible = true }) { Icon(Icons.Default.Terminal, "Terminal") }
                            }
                        )
                    },
                    bottomBar = {
                        // VS Code-style status bar, follows theme + can be hidden in Settings
                        if (settings.showBottomBar) {
                            EditorStatusBar(
                                editor = editorState.editor,
                                fileName = activeTab?.name ?: "",
                                settings = settings
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(Modifier.padding(innerPadding).fillMaxSize().imePadding()) {
                        // Tab bar below the header (height 40dp, same as header)
                        EditorTabBar(
                            tabs = tabs,
                            activeTabId = activeTabId,
                            onSelect = { id -> captureActiveTabContent(); activeTabId = id },
                            onClose = { tab -> requestCloseTab(tab) },
                            onSave = { tab ->
                                scope.launch {
                                    val i = tabs.indexOfFirst { it.id == tab.id }
                                    if (i >= 0) {
                                        val t = tabs[i]
                                        if (t.uri != null) {
                                            writeFileContent(context, t.uri, t.content)
                                            tabs[i] = tabs[i].copy(isDirty = false)
                                        } else {
                                            activeTabId = t.id
                                            saveFileLauncher.launch("${t.name}.txt")
                                        }
                                    }
                                }
                            },
                            onCloseOthers = { id -> closeOthers(id) },
                            onDelete = { tab -> pendingDeleteTab = tab },
                            onNewTab = { newUntitledTab() }
                        )

                        // ── Inline FindBar à la VS Code (above the editor) ──
                        if (showFindBar) {
                            FindBar(
                                editor = editorState.editor,
                                onDismiss = { showFindBar = false }
                            )
                        }

                        SoraCodeEditor(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                            state = editorState,
                            fileName = activeTab?.name ?: "Untitled",
                            content = activeTab?.content ?: "",
                            settings = settings,
                            onTextChange = {
                                val i = activeTabIndex()
                                if (i >= 0 && !tabs[i].isDirty) {
                                    tabs[i] = tabs[i].copy(isDirty = true)
                                }
                            }
                        )
                    }
                }
            }

            // ── All dialogs & confirmations INSIDE the theme ──
            if (showNewFileDialog) {
                InputDialog(title = "New File Name", onDismiss = { showNewFileDialog = false }, onConfirm = { name ->
                    showNewFileDialog = false
                    scope.launch {
                        val parent = targetParentNode?.let { DocumentFile.fromTreeUri(context, it.uri) } ?: DocumentFile.fromTreeUri(context, rootFolderUri!!)
                        parent?.createFile("text/plain", name)
                        folderCache.clear()
                        rebuildVisibleNodes()
                    }
                })
            }
            if (showNewFolderDialog) {
                InputDialog(title = "New Folder Name", onDismiss = { showNewFolderDialog = false }, onConfirm = { name ->
                    showNewFolderDialog = false
                    scope.launch {
                        val parent = targetParentNode?.let { DocumentFile.fromTreeUri(context, it.uri) } ?: DocumentFile.fromTreeUri(context, rootFolderUri!!)
                        parent?.createDirectory(name)
                        folderCache.clear()
                        rebuildVisibleNodes()
                    }
                })
            }
            if (showRenameDialog && selectedNode != null) {
                InputDialog(title = "Rename", initialText = selectedNode!!.name, onDismiss = { showRenameDialog = false }, onConfirm = { newName ->
                    showRenameDialog = false
                    scope.launch {
                        DocumentFile.fromSingleUri(context, selectedNode!!.uri)?.renameTo(newName)
                        folderCache.clear()
                        rebuildVisibleNodes()
                    }
                })
            }
            if (showDeleteDialog && selectedNode != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete") },
                    text = { Text("Are you sure you want to delete '${selectedNode!!.name}'?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteDialog = false
                            scope.launch {
                                DocumentFile.fromSingleUri(context, selectedNode!!.uri)?.delete()
                                folderCache.clear()
                                rebuildVisibleNodes()
                            }
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
                )
            }
            if (showAboutDialog) {
                val ctx = LocalContext.current
                val pkg = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
                val appName = remember { runCatching { ctx.applicationInfo.loadLabel(ctx.packageManager).toString() }.getOrDefault("Egg Code Editor") }
                val versionText = if (pkg != null) "${pkg.versionName ?: "?"} (${pkg.longVersionCode})" else "?"
                AlertDialog(
                    onDismissRequest = { showAboutDialog = false },
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
                        TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
                    }
                )
            }
            pendingCloseTab?.let { tab ->
                AlertDialog(
                    onDismissRequest = { pendingCloseTab = null },
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
                            pendingCloseTab = null
                            saveTabAndClose(tab)
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                pendingCloseTab = null
                                reallyCloseTab(tab.id)
                            }) { Text("Don't Save") }
                            TextButton(onClick = { pendingCloseTab = null }) { Text("Cancel") }
                        }
                    }
                )
            }
            pendingDeleteTab?.let { tab ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteTab = null },
                    title = { Text("Delete File") },
                    text = { Text("Are you sure you want to delete '${tab.name}' from storage?") },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingDeleteTab = null
                            scope.launch {
                                DocumentFile.fromSingleUri(context, tab.uri!!)?.delete()
                                val idx = tabs.indexOfFirst { it.id == tab.id }
                                tabs.removeAll { it.id == tab.id }
                                if (activeTabId == tab.id) switchAfterRemoval(idx)
                                folderCache.clear()
                                rebuildVisibleNodes()
                            }
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { pendingDeleteTab = null }) { Text("Cancel") } }
                )
            }

            if (showSettings) {
                SettingsDialog(
                    settings = settings,
                    onSettingsChange = { settings = it },
                    onDismiss = { showSettings = false },
                    onBackup = { uri -> performBackup(uri) },
                    onRestore = { uri -> performRestore(uri) }
                )
            }

            if (showExitConfirm) {
                AlertDialog(
                    onDismissRequest = { showExitConfirm = false },
                    title = { Text("Exit Egg?") },
                    text = { Text("Unsaved changes will be lost.") },
                    confirmButton = {
                        TextButton(onClick = { (context as? Activity)?.finish() }) { Text("Exit") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            if (showGitRemoteDialog) {
                GitRemoteDialog(
                    title = gitRemoteDialogTitle,
                    onConfirm = { url, user, token ->
                        showGitRemoteDialog = false
                        gitUser = user; gitToken = token
                        if (gitRemoteDialogTitle == "Clone Repository") gitDoClone(url, user, token) else gitAddRemote(url, user, token)
                    },
                    onDismiss = { showGitRemoteDialog = false }
                )
            }

            if (showCloudConnectDialog) {
                CloudConnectDialog(
                    onConfirm = { keyId, appKey, bucket ->
                        showCloudConnectDialog = false
                        cloudConnect(keyId, appKey, bucket)
                    },
                    onDismiss = { showCloudConnectDialog = false }
                )
            }

            // ── Terminal (default theme, no color overrides) ──
            if (terminalVisible) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // Terminal header: title + active File Manager folder location
                        Column(
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Alpine Terminal",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    terminalVisible = false
                                    editorState.editor?.requestFocus()
                                }) {
                                    Icon(Icons.Default.Close, "Close Terminal", tint = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                            Text(
                                "Folder: ${rootFolderPath ?: "No folder open"}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val emulator = terminalEmulator
                        if (emulator != null) {
                            Terminal(
                                terminalEmulator = emulator,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                                    .focusRequester(terminalFocusRequester)
                                    // Tap anywhere on the terminal screen → request focus + show the IME again.
                                    // Uses a non-consuming pointerInput so scrolling/keystrokes still work.
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown()
                                            val up = waitForUpOrCancellation()
                                            if (up != null) {
                                                scope.launch {
                                                    terminalFocusRequester.requestFocus()
                                                    delay(150.milliseconds)
                                                    // Attempt 1: standard Compose controller
                                                    try { keyboardController?.show() } catch (e: Exception) { e.printStackTrace() }
                                                    // Attempt 2 (MIUI fallback): force via InputMethodManager
                                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                    val focused = (context as? Activity)?.currentFocus
                                                    if (focused != null) {
                                                        @Suppress("DEPRECATION")
                                                        imm?.showSoftInput(focused, InputMethodManager.SHOW_FORCED)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                keyboardEnabled = true,
                                showSoftKeyboard = true,
                                focusRequester = terminalFocusRequester // if the compiler rejects this, remove this line
                            )
                        } else {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text("Loading terminal...", color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // ── Terminal shortcut bottom bar (horizontal scroll) ──
                        TerminalShortcutBar(
                            onCommand = { cmd ->
                                terminalSession?.let { session ->
                                    runCatching { session.write((cmd + "\r\n").toByteArray()) }
                                }
                            }
                        )
                    }
                }
            }

            // ── Splash overlay (covers everything on cold start) ──
            if (showSplash) {
                SplashOverlay()
            }
        }
    }
}