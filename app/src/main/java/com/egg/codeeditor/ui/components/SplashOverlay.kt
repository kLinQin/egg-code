package com.egg.codeeditor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.R
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal fun crackPoints(w: Float, h: Float): List<Offset> = listOf(
    Offset(w * 0.50f, h * 0.04f),
    Offset(w * 0.44f, h * 0.16f),
    Offset(w * 0.55f, h * 0.28f),
    Offset(w * 0.46f, h * 0.40f),
    Offset(w * 0.57f, h * 0.52f),
    Offset(w * 0.48f, h * 0.64f),
    Offset(w * 0.56f, h * 0.78f),
    Offset(w * 0.50f, h * 0.92f)
)

internal fun buildHalfPath(pts: List<Offset>, left: Boolean, w: Float, h: Float): Path {
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

@Composable
internal fun SplashOverlay() {
    val bgColor = MaterialTheme.colorScheme.background
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

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
    }

    val context = LocalContext.current
    val eggBitmap = remember {
        val drawable = context.getDrawable(R.drawable.ic_launcher_foreground)
        if (drawable != null) drawableToBitmap(drawable, 512).asImageBitmap() else null
    }

    val shake = ((progress.value - 0.10f) / 0.35f).coerceIn(0f, 1f)
    val shakeAmp = (sin(shake * PI * 14) * (1f - shake) * 10f).toFloat()

    val crackT = ((progress.value - 0.30f) / 0.25f).coerceIn(0f, 1f)

    val splitT = ((progress.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val splitEased = if (splitT <= 0f) 0f else 1f - (1f - splitT).pow(2)

    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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

                    withTransform({ translate(leftOff, 0f) }) {
                        clipPath(buildHalfPath(pts, left = true, w = w, h = h)) {
                            drawImage(eggBitmap, srcSize = srcSize, dstSize = dstSize, alpha = alpha)
                        }
                    }

                    withTransform({ translate(rightOff, 0f) }) {
                        clipPath(buildHalfPath(pts, left = false, w = w, h = h)) {
                            drawImage(eggBitmap, srcSize = srcSize, dstSize = dstSize, alpha = alpha)
                        }
                    }
                }

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