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

    val scheme = editorRef?.colorScheme
    val fgColor = scheme?.getColor(EditorColorScheme.TEXT_NORMAL) ?: fg.toArgb()
    val bgColor = bg.toArgb()
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

    val html = remember(markdown, isDark, editorSettings.themeName, editorSettings.fontSizeSp, editorSettings.fontFamily) {
        val jsMarkdown = org.json.JSONObject.quote(markdown)
        "<!DOCTYPE html><html><head>" +
        "<meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
        "<style>html,body{margin:0;padding:0;overflow-x:hidden}*{box-sizing:border-box;word-wrap:break-word}img{max-width:100%;height:auto}table{max-width:100%;display:block;overflow-x:auto}" +
        "body{background:" + hex(bgColor) + ";color:" + hex(fgColor) + ";font-family:" + fontFamily + ";padding:16px;margin:0;line-height:" + lineHeight + "px;word-wrap:break-word;font-size:" + fontSize + "px;overflow-y:auto}" +
        ".code-block{position:relative;background:" + hex(preBg) + ";border-radius:6px;margin:8px 0;padding:0}" +
        ".lang-label{position:absolute;top:6px;left:10px;font-size:11px;color:" + hex(fgColor) + ";opacity:0.7;font-family:sans-serif;text-transform:uppercase}" +
        ".line-numbers{display:flex;flex-direction:column;position:absolute;left:0;top:28px;bottom:0;width:36px;padding:8px 4px;text-align:right;font-family:monospace;font-size:" + (fontSize - 2) + "px;line-height:" + ((fontSize - 1) * 1.4f) + "px;color:" + hex(fgColor) + ";opacity:0.4;background:" + hex(preBg) + ";border-right:1px solid rgba(127,127,127,0.2);user-select:none;overflow:hidden}" +
        ".line-numbers span{display:block;height:" + ((fontSize - 1) * 1.4f) + "px}" +
        "pre{position:relative;background:transparent;margin:0;padding:8px 8px 8px 44px;padding-top:36px;font-size:" + (fontSize - 1) + "px;line-height:" + ((fontSize - 1) * 1.4f) + "px;white-space:pre-wrap;word-break:break-all;overflow-wrap:anywhere}" +
        "pre code,pre > code{background:transparent !important;padding:0 !important;color:inherit}" +
        "code{background:" + hex(preBg) + ";padding:2px 4px;border-radius:3px;font-family:" + fontFamily + ";font-size:" + (fontSize - 1) + "px}" +
        ".copy-btn{position:absolute;top:6px;right:6px;background:" + hex(primary.toArgb()) + ";color:" + hex(bg.toArgb()) + ";border:none;padding:4px 10px;border-radius:4px;font-size:11px;cursor:pointer;opacity:0.9}" +
        ".copy-btn:active{opacity:0.6}" +
        "h1{color:" + hex(primary.toArgb()) + ";font-size:" + (fontSize + 10) + "px;border-bottom:2px solid " + hex(primary.toArgb()) + ";padding-bottom:6px;margin:16px 0 12px}" +
        "h2{color:" + hex(primary.toArgb()) + ";font-size:" + (fontSize + 6) + "px;border-bottom:1px solid " + hex(surface.toArgb()) + ";padding-bottom:4px;margin:14px 0 10px}" +
        "h3{color:" + hex(primary.toArgb()) + ";font-size:" + (fontSize + 3) + "px;margin:12px 0 8px}" +
        "h4{color:" + hex(primary.toArgb()) + ";font-size:" + (fontSize + 1) + "px;margin:10px 0 6px}" +
        "h5,h6{color:" + hex(primary.toArgb()) + ";font-size:" + fontSize + "px;margin:8px 0 4px;opacity:0.9}" +
        "strong,b{color:" + hex(fgColor) + ";font-weight:700}" +
        "em,i{color:" + hex(fgColor) + ";font-style:italic;opacity:0.85}" +
        "a{color:" + hex(primary.toArgb()) + ";text-decoration:underline}" +
        "a:hover{opacity:0.8}" +
        "p > code,li > code,td > code,th > code,blockquote > code{color:" + hex(keyword) + " !important;background:" + hex(preBg) + ";padding:2px 5px;border-radius:3px;font-family:" + fontFamily + ";font-size:" + (fontSize - 1) + "px;border:none}" +
        "ul,ol{padding-left:24px;margin:6px 0}" +
        "li::marker{color:" + hex(primary.toArgb()) + "}" +
        "table{border-collapse:collapse;margin:8px 0}" +
        "th,td{border:1px solid " + hex(surface.toArgb()) + ";padding:6px 10px;text-align:left}" +
        "th{background:" + hex(preBg) + ";color:" + hex(primary.toArgb()) + ";font-weight:600}" +
        "blockquote{border-left:4px solid " + hex(primary.toArgb()) + ";padding:6px 12px;margin:6px 0;color:" + hex(fgColor) + ";opacity:0.9;background:transparent}" +
        "blockquote p{margin:2px 0}" +
        "hr{border:none;border-top:1px solid " + hex(surface.toArgb()) + ";margin:12px 0}" +
        ".spoiler-inline{background:" + hex(fgColor) + ";color:transparent;border-radius:3px;padding:0 3px;cursor:pointer;transition:all .15s}" +
        ".spoiler-inline.revealed{background:" + hex(surface.toArgb()) + ";color:" + hex(fgColor) + "}" +
        "u{text-decoration:underline;text-decoration-color:" + hex(primary.toArgb()) + ";text-underline-offset:2px}" +
        "del{color:" + hex(comment) + ";opacity:0.7}" +
        "small{font-size:0.82em;opacity:0.75}" +
        ".youtube-embed{position:relative;padding-bottom:56.25%;height:0;margin:8px 0;border-radius:6px;overflow:hidden}" +
        ".youtube-embed iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}" +
        ".discord-ts{color:" + hex(primary.toArgb()) + ";background:" + hex(surface.toArgb()) + ";padding:1px 4px;border-radius:3px;font-size:0.92em;cursor:default}" +
        ".callout{border-left:4px solid;padding:10px 12px;margin:10px 0;border-radius:6px}" +
        ".callout-title{font-weight:600;margin-bottom:4px;display:flex;align-items:center;gap:6px}" +
        ".callout-note{border-color:#4a9eff;background:rgba(74,158,255,0.1)}" +
        ".callout-note .callout-title{color:#4a9eff}" +
        ".callout-info{border-color:#4a9eff;background:rgba(74,158,255,0.1)}" +
        ".callout-info .callout-title{color:#4a9eff}" +
        ".callout-warning{border-color:#f0ad4e;background:rgba(240,173,78,0.1)}" +
        ".callout-warning .callout-title{color:#f0ad4e}" +
        ".callout-success{border-color:#4caf50;background:rgba(76,175,80,0.1)}" +
        ".callout-success .callout-title{color:#4caf50}" +
        ".callout-danger{border-color:#e74c3c;background:rgba(231,76,60,0.1)}" +
        ".callout-danger .callout-title{color:#e74c3c}" +
        ".callout-tip{border-color:#4caf50;background:rgba(76,175,80,0.1)}" +
        ".callout-tip .callout-title{color:#4caf50}" +
        ".callout-body p{margin:0}" +
        ".callout-body p+p{margin-top:6px}" +
        "p{margin:6px 0}" +
        ".task-check{display:inline-block;width:15px;height:15px;border:2px solid " + hex(primary.toArgb()) + ";border-radius:3px;vertical-align:middle;margin-right:6px;position:relative;box-sizing:border-box}" +
        ".task-check.checked{background:" + hex(primary.toArgb()) + "}" +
        ".task-check.checked::after{content:'\\2713';position:absolute;top:-1px;left:2px;color:" + hex(bg.toArgb()) + ";font-size:15px;font-weight:bold;line-height:1}" +
        ".task-done{opacity:0.6;text-decoration:line-through}" +
        "::-webkit-scrollbar{display:none}" +
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
        "md=md.replace(/<t:(\\d+)(?::([a-zA-Z]))?>/g,function(_,ts,fmt){return '<span class=\\\"discord-ts\\\" data-ts=\\\"'+ts+'\\\" data-format=\\\"'+(fmt||'f')+'\\\"></span>';});" +
        "var lines=md.split('\\n');var out=[];var i=0;" +
        "while(i<lines.length){" +
        "var l=lines[i];var m=l.match(/^> \\[!(\\w+)\\]\\s*(.*)$/i);" +
        "if(m){" +
        "var type=m[1].toLowerCase();var title=m[2]||'';var body=[];i++;" +
        "while(i<lines.length && lines[i].startsWith('> ')){body.push(lines[i].slice(2));i++;}" +
        "out.push('<div class=\\\"callout callout-'+type+'\\\"><div class=\\\"callout-title\\\">'+(title||type)+'</div><div class=\\\"callout-body\\\">'+marked.parse(body.join('\\n'))+'</div></div>');" +
        "}else{out.push(l);i++;}}" +
        "md=out.join('\\n');" +
        "md=md.replace(/^- \\[ \\] (.+)$/gm,function(_,t){return '<div class=\\\"task-item\\\"><span class=\\\"task-check\\\"></span>'+t+'</div>';});" +
        "md=md.replace(/^- \\[x\\] (.+)$/gmi,function(_,t){return '<div class=\\\"task-item task-done\\\"><span class=\\\"task-check checked\\\"></span>'+t+'</div>';});" +
        "md=md.replace(/^(?:-# )(.+)$/gm,'<small>$1</small>');" +
        "md=md.replace(/==(.+?)==/g,'<u>$1</u>');" +
        "md=md.replace(/~~(.+?)~~/g,'<del>$1</del>');" +
        "md=md.replace(/\\|\\|(.+?)\\|\\|/g,'<span class=\\\"spoiler-inline\\\">$1</span>');" +
        "md=md.replace(/(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/watch\\?v=|youtu\\.be\\/)([a-zA-Z0-9_-]{11})[^\\s]*/g,function(_,id){return '<div class=\\\"youtube-embed\\\"><iframe src=\\\"https://www.youtube.com/embed/'+id+'\\\" allowfullscreen></iframe></div>';});" +
        "document.getElementById('content').innerHTML=marked.parse(md);" +
        "document.querySelectorAll('.spoiler-inline').forEach(function(el){" +
        "el.addEventListener('click',function(){el.classList.toggle('revealed');});" +
        "});" +
        "function fmtTs(ts,format){var d=new Date(ts*1000);var loc='id-ID';var rtf=new Intl.RelativeTimeFormat(loc,{numeric:'auto'});switch(format){case 'f':return d.toLocaleString(loc,{day:'numeric',month:'long',year:'numeric',hour:'2-digit',minute:'2-digit'});case 'F':return d.toLocaleString(loc,{weekday:'long',day:'numeric',month:'long',year:'numeric',hour:'2-digit',minute:'2-digit'});case 'd':return d.toLocaleDateString(loc);case 'D':return d.toLocaleDateString(loc,{day:'numeric',month:'long',year:'numeric'});case 't':return d.toLocaleTimeString(loc,{hour:'2-digit',minute:'2-digit'});case 'T':return d.toLocaleTimeString(loc,{hour:'2-digit',minute:'2-digit',second:'2-digit'});case 'R':{var diff=Math.floor((d.getTime()-Date.now())/1000);if(Math.abs(diff)<60)return rtf.format(diff,'second');if(Math.abs(diff)<3600)return rtf.format(Math.floor(diff/60),'minute');if(Math.abs(diff)<86400)return rtf.format(Math.floor(diff/3600),'hour');return rtf.format(Math.floor(diff/86400),'day');}default:return d.toLocaleString(loc);}}" +
        "document.querySelectorAll('.discord-ts').forEach(function(el){var ts=parseInt(el.getAttribute('data-ts')||'0');var fmt=el.getAttribute('data-format')||'f';el.textContent=fmtTs(ts,fmt);el.title=new Date(ts*1000).toLocaleString('id-ID');});" +
        "document.querySelectorAll('pre').forEach(function(pre){" +
        "var code=pre.querySelector('code');if(!code)return;" +
        "var langMatch=(code.className||'').match(/language-(\\w+)/);var lang=langMatch?langMatch[1]:'';" +
        "try{hljs.highlightElement(code);}catch(e){}" +
        "var wrapper=document.createElement('div');wrapper.className='code-block';pre.parentNode.insertBefore(wrapper,pre);" +
        "var lb=document.createElement('div');lb.className='lang-label';lb.textContent=(lang||'plaintext');wrapper.appendChild(lb);" +
        "var rawText=code.innerText.replace(/\\n+$/,'');var lineCount=rawText.split('\\n').length;" +
        "var ln=document.createElement('div');ln.className='line-numbers';for(var i=1;i<=lineCount;i++){var sp=document.createElement('span');sp.textContent=i;ln.appendChild(sp);}wrapper.appendChild(ln);" +
        "wrapper.appendChild(pre);" +
        "var btn=document.createElement('button');btn.className='copy-btn';btn.textContent='Copy';" +
        "btn.onclick=function(){var ce=pre.querySelector('code');if(ce&&window.AndroidBridge){window.AndroidBridge.copy(ce.innerText);}" +
        "btn.textContent='Copied!';setTimeout(function(){btn.textContent='Copy';},1500);};" +
        "wrapper.appendChild(btn);});" +
        "</script></body></html>"
    }

    key(isDark, editorSettings.themeName) {
    AndroidView(
        modifier = modifier.fillMaxSize().clipToBounds().background(bg),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
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
