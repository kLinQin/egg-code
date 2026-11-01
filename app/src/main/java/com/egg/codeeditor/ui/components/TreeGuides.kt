package com.egg.codeeditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Indent guide vertikal ala VS Code.
 *
 * Untuk tiap level l < depth:
 * - Jika masih ada saudara DI BAWAH baris ini di level l → garis vertikal PENUH
 *   (menanjang dari atas ke bawah melewati baris, sehingga tersambung kontinu
 *   ke folder berikutnya di kolom yang sama).
 * - Untuk level terdalam, garis dari atas ke tengah baris (penghubung dari
 *   garis induk), lalu siku pendek ke kanan (menuju arrow/ikon) dan turun
 *   lagi jika masih ada saudara di bawah (bentuk T).
 */
@Composable
fun TreeGuides(
    depth: Int,
    hasGuide: List<Boolean>,
    color: Color = Color(0xFF9E9E9E),
    strokeWidth: Dp = 1.5.dp,
    indent: Dp = 16.dp,
    height: Dp = 48.dp,
) {
    Box(modifier = Modifier.width(indent * depth).height(height)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val indentPx = indent.toPx()
            val midY = size.height / 2f

            for (l in 0 until depth) {
                val x = l * indentPx + indentPx / 2f
                val hasBelow = hasGuide.getOrNull(l) == true

                if (l == depth - 1) {
                    // Level terdalam: garis dari induk turun ke tengah baris.
                    drawLine(color, Offset(x, 0f), Offset(x, midY), strokeWidth.toPx())

                    // Siku pendek ke kanan MENUJU arrow — SELALU digambar,
                    // jadi > / V tidak pernah terlihat "mengambang" dari garis.
                    drawLine(color, Offset(x, midY), Offset(size.width, midY), strokeWidth.toPx())

                    // Kalau masih ada anak/saudara di bawah → teruskan ke bawah (bentuk T).
                    if (hasBelow) {
                        drawLine(color, Offset(x, midY), Offset(x, size.height), strokeWidth.toPx())
                    }
                } else if (hasBelow) {
                    // Level antara: garis vertikal penuh (kontinu).
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth.toPx())
                }
            }
        }
    }
}