package com.egg.codeeditor.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.egg.codeeditor.model.EditorSettings
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

private fun hex(argb: Int): String = "#%06X".format(0xFFFFFF and argb)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownPreview(
    markdown: String,
    editorSettings: EditorSettings,
    editorRef: CodeEditor?,
    modifier: Modifier = Modifier
) {
    val bg = MaterialTheme.colorScheme.background
    val fg = MaterialTheme.colorScheme.onBackground
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val isDark = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f

    // Ambil warna dari Sora EditorColorScheme (fallback ke Compose theme)
    val scheme = editorRef?.colorScheme
    val fgColor = scheme?.getColor(EditorColorScheme.TEXT_NORMAL) ?: fg.toArgb()
    val bgColor = bg.toArgb()
    // Token warna untuk highlight.js — TextMateColorScheme tidak expose KEYWORD/LITERAL/COMMENT,
    // jadi pakai hardcoded sesuai theme GitHub Light / Darcula.
    val keyword: Int
    val stringC: Int
    val comment: Int
    val number: Int
    val preBg: Int
    if (isDark) {
        keyword = 0xFFFF7B72.toInt()
        stringC = 0xFFA5D6FF.toInt()
        comment = 0xFF8B949E.toInt()
        number = 0xFF79C0FF.toInt()
        preBg = surface.toArgb()
    } else {
        keyword = 0xFFD73A49.toInt()
        stringC = 0xFF032F62.toInt()
        comment = 0xFF6A737D.toInt()
        number = 0xFF005CC5.toInt()
        preBg = surface.toArgb()
    }

    val fontFamily = when (editorSettings.fontFamily) {
        "sans" -> "sans-serif"
        "serif" -> "serif"
        else -> "monospace"
    }
    val fontSize = editorSettings.fontSizeSp
    val lineHeight = (fontSize * 1.5f)

    val html = remember(markdown, isDark, editorSettings.themeName) {
        val jsMarkdown = org.json.JSONObject.quote(markdown)
        "<!DOCTYPE html><html><head>" +
        "<meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
        "<style>" +
        "body{background:" + hex(bgColor) + ";color:" + hex(fgColor) + ";font-family:" + fontFamily + ";padding:16px;margin:0;line-height:" + lineHeight + "px;word-wrap:break-word;font-size:" + fontSize + "px}" +
        "pre{position:relative;background:" + hex(preBg) + ";padding:12px;padding-top:36px;border-radius:6px;overflow-x:auto;font-size:" + (fontSize - 1) + "px;line-height:" + ((fontSize - 1) * 1.4f) + "px}" +
        "pre code{background:transparent;padding:0;font-family:" + fontFamily + "}" +
        "code{background:" + hex(preBg) + ";padding:2px 4px;border-radius:3px;font-family:" + fontFamily + ";font-size:" + (fontSize - 1) + "px}" +
        ".copy-btn{position:absolute;top:6px;right:6px;background:" + hex(primary.toArgb()) + ";color:" + hex(bg.toArgb()) + ";border:none;padding:4px 10px;border-radius:4px;font-size:11px;cursor:pointer;opacity:0.9}" +
        ".copy-btn:active{opacity:0.6}" +
        "h1,h2,h3,h4,h5,h6{color:" + hex(fgColor) + ";border-bottom:1px solid " + hex(surface.toArgb()) + ";padding-bottom:4px}" +
        "a{color:" + hex(primary.toArgb()) + "}" +
        "table{border-collapse:collapse;margin:8px 0}" +
        "th,td{border:1px solid " + hex(surface.toArgb()) + ";padding:6px 10px}" +
        "th{background:" + hex(preBg) + "}" +
        "blockquote{border-left:3px solid " + hex(primary.toArgb()) + ";padding-left:12px;margin-left:0;color:" + hex(fgColor) + ";opacity:0.8}" +
        "hr{border:none;border-top:1px solid " + hex(surface.toArgb()) + "}" +
        "::-webkit-scrollbar{display:none}" +
        // highlight.js mapping sesuai Sora scheme
        ".hljs-keyword{color:" + hex(keyword) + "}" +
        ".hljs-built_in{color:" + hex(keyword) + "}" +
        ".hljs-string{color:" + hex(stringC) + "}" +
        ".hljs-comment{color:" + hex(comment) + ";font-style:italic}" +
        ".hljs-number{color:" + hex(number) + "}" +
        ".hljs-title{color:" + hex(keyword) + "}" +
        ".hljs-function{color:" + hex(fgColor) + "}" +
        ".hljs-variable{color:" + hex(fgColor) + "}" +
        "</style></head><body>" +
        "<div id=\"content\"></div>" +
        "<script src=\"file:///android_asset/marked.min.js\"></script>" +
        "<script src=\"file:///android_asset/highlight.min.js\"></script>" +
        "<script>" +
        "var md=" + jsMarkdown + ";" +
        "document.getElementById('content').innerHTML=marked.parse(md);" +
        "document.querySelectorAll('pre code').forEach(function(el){hljs.highlightElement(el);});" +
        "document.querySelectorAll('pre').forEach(function(pre){" +
        "var btn=document.createElement('button');btn.className='copy-btn';btn.textContent='Copy';" +
        "btn.onclick=function(){var code=pre.querySelector('code').innerText;" +
        "if(window.AndroidBridge){window.AndroidBridge.copy(code);}" +
        "btn.textContent='Copied!';setTimeout(function(){btn.textContent='Copy';},1500);};" +
        "pre.appendChild(btn);});" +
        "</script></body></html>"
    }

    key(isDark, editorSettings.themeName) {
    AndroidView(
        modifier = modifier.fillMaxSize().clipToBounds().background(bg),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(bgColor)
                webViewClient = WebViewClient()
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun copy(text: String) {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("md", text))
                    }
                }, "AndroidBridge")
                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            }
        },
        update = { wv ->
            wv.setBackgroundColor(bgColor)
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        },
        onRelease = { (it as WebView).destroy() }
    )
    }
}
