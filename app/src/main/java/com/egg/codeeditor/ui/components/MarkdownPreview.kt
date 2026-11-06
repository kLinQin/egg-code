package com.egg.codeeditor.ui.components

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

@Composable
fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.background
    val fg = MaterialTheme.colorScheme.onBackground
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    AndroidView(
        modifier = modifier.fillMaxSize().clipToBounds().background(bg),
        factory = { ctx ->
            android.widget.ScrollView(ctx).apply {
                setBackgroundColor(bg.toArgb())
                isFillViewport = true
                val tv = TextView(ctx).apply {
                    setTextColor(fg.toArgb())
                    setBackgroundColor(bg.toArgb())
                    setPadding(32, 32, 32, 32)
                    textSize = 14f
                    setLineSpacing(0f, 1.2f)
                }
                addView(tv)
            }
        },
        update = { sv ->
            val tv = sv.getChildAt(0) as TextView
            tv.setTextColor(fg.toArgb())
            tv.setBackgroundColor(bg.toArgb())
            sv.setBackgroundColor(bg.toArgb())
            markwon.setMarkdown(tv, markdown)
        }
    )
}
