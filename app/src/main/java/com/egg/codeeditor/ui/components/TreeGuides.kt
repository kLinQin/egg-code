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
                    drawLine(color, Offset(x, 0f), Offset(x, midY), strokeWidth.toPx())

                    drawLine(color, Offset(x, midY), Offset(size.width, midY), strokeWidth.toPx())

                    if (hasBelow) {
                        drawLine(color, Offset(x, midY), Offset(x, size.height), strokeWidth.toPx())
                    }
                } else if (hasBelow) {
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth.toPx())
                }
            }
        }
    }
}