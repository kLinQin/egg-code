package com.egg.codeeditor.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
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
    fun sc(k: Int, fallback: Int): Int =
        runCatching { scheme?.getColor(k) }.getOrNull()?.takeIf { it != 0 } ?: fallback

    val keyword = sc(EditorColorScheme.KEYWORD, if (isDark) 0xFFFF7B72.toInt() else 0xFFD73A49.toInt())
    val stringC = sc(EditorColorScheme.LITERAL, if (isDark) 0xFFA5D6FF.toInt() else 0xFF032F62.toInt())
    val comment = sc(EditorColorScheme.COMMENT, if (isDark) 0xFF8B949E.toInt() else 0xFF6A737D.toInt())
    val number = sc(EditorColorScheme.OPERATOR, if (isDark) 0xFF79C0FF.toInt() else 0xFF005CC5.toInt())
    val function = sc(EditorColorScheme.FUNCTION_NAME, keyword)
    val annotation = sc(EditorColorScheme.ANNOTATION, keyword)
    val preBg = surface.toArgb()
    val accentInfo = MaterialTheme.colorScheme.tertiary.toArgb()
    val accentWarn = MaterialTheme.colorScheme.primary.toArgb()
    val accentDanger = MaterialTheme.colorScheme.error.toArgb()
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.toArgb()

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
        ".code-block{position:relative;background:" + hex(preBg) + ";border-radius:0;margin:8px 0;padding:0}" +
        ".lang-label{position:absolute;top:6px;left:10px;font-size:11px;color:" + hex(fgColor) + ";opacity:0.7;font-family:sans-serif;text-transform:uppercase}" +
        ".line-numbers{display:flex;flex-direction:column;position:absolute;left:0;top:28px;bottom:0;width:36px;padding:8px 4px;text-align:right;font-family:monospace;font-size:" + (fontSize - 2) + "px;line-height:" + ((fontSize - 1) * 1.4f) + "px;color:" + hex(fgColor) + ";opacity:0.4;background:" + hex(preBg) + ";border-right:1px solid " + hex(outlineVariant) + ";user-select:none;overflow:hidden}" +
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
        "th,td{border:1px solid " + hex(outlineVariant) + ";padding:6px 10px;text-align:left}" +
        "th{background:" + hex(preBg) + ";color:" + hex(primary.toArgb()) + ";font-weight:600}" +
        "blockquote{border-left:4px solid " + hex(primary.toArgb()) + ";padding:6px 12px;margin:6px 0;color:" + hex(fgColor) + ";opacity:0.9;background:transparent}" +
        "blockquote p{margin:2px 0}" +
        "hr{border:none;border-top:1px solid " + hex(outlineVariant) + ";margin:12px 0}" +
        ".spoiler-inline{background:" + hex(fgColor) + ";color:transparent;border-radius:3px;padding:0 3px;cursor:pointer;transition:all .15s}" +
        ".spoiler-inline.revealed{background:" + hex(surface.toArgb()) + ";color:" + hex(fgColor) + "}" +
        "u{text-decoration:underline;text-decoration-color:" + hex(primary.toArgb()) + ";text-underline-offset:2px}" +
        "del{color:" + hex(comment) + ";opacity:0.7}" +
        "small{font-size:0.82em;opacity:0.75}" +
        ".youtube-embed{position:relative;padding-bottom:56.25%;height:0;margin:8px 0;border-radius:0;overflow:hidden}" +
        ".youtube-embed iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}" +
        ".twitter-card{display:block;padding:10px 12px;margin:8px 0;background:" + hex(preBg) + ";border-left:4px solid " + hex(accentInfo) + ";border-radius:0;text-decoration:none;color:" + hex(fgColor) + "}" +
        ".twitter-card .tw-user{display:block;color:hex(accentInfo);font-weight:600;font-size:13px}" +
        ".twitter-card .tw-body{display:block;font-size:12px;opacity:0.7;margin-top:2px}" +
        ".discord-ts{color:" + hex(primary.toArgb()) + ";background:" + hex(surface.toArgb()) + ";padding:1px 4px;border-radius:3px;font-size:0.92em;cursor:default}" +
        ".callout{border-left:4px solid;padding:10px 12px;margin:10px 0;border-radius:0}" +
        ".callout-title{font-weight:600;margin-bottom:4px;display:flex;align-items:center;gap:6px}" +
        ".callout-note{border-color:" + hex(accentInfo) + ";background:rgba(74,158,255,0.1)}" +
        ".callout-note .callout-title{color:hex(accentInfo)}" +
        ".callout-info{border-color:" + hex(accentInfo) + ";background:rgba(74,158,255,0.1)}" +
        ".callout-info .callout-title{color:hex(accentInfo)}" +
        ".callout-warning{border-color:" + hex(accentWarn) + ";background:rgba(240,173,78,0.1)}" +
        ".callout-warning .callout-title{color:hex(accentWarn)}" +
        ".callout-success{border-color:#4caf50;background:rgba(76,175,80,0.1)}" +
        ".callout-success .callout-title{color:#4caf50}" +
        ".callout-danger{border-color:" + hex(accentDanger) + ";background:rgba(231,76,60,0.1)}" +
        ".callout-danger .callout-title{color:hex(accentDanger)}" +
        ".callout-tip{border-color:#4caf50;background:rgba(76,175,80,0.1)}" +
        ".callout-tip .callout-title{color:#4caf50}" +
        ".callout-body p{margin:0}" +
        ".callout-body p+p{margin-top:6px}" +
        "p{margin:6px 0}" +
        ".task-check{display:inline-block;width:15px;height:15px;border:2px solid " + hex(primary.toArgb()) + ";border-radius:3px;vertical-align:middle;margin-right:6px;position:relative;box-sizing:border-box}" +
        ".task-check.checked{background:" + hex(primary.toArgb()) + "}" +
        ".task-check.checked::after{content:'\\2713';position:absolute;top:-1px;left:2px;color:" + hex(bg.toArgb()) + ";font-size:15px;font-weight:bold;line-height:1}" +
        ".task-done{opacity:0.6;text-decoration:line-through}" +
        ".badge-inline{display:inline-flex;align-items:center;gap:4px;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:600;font-family:sans-serif;background:" + hex(surface.toArgb()) + ";color:" + hex(fgColor) + ";border:1px solid " + hex(primary.toArgb()) + ";margin:0 2px}" +
        ".badge-inline.badge-left{float:left;margin:0 8px 4px 0}" +
        ".badge-inline.badge-right{float:right;margin:0 0 4px 8px}" +
        ".badge-inline.badge-completed,.badge-inline.badge-success{color:#4caf50;border-color:#4caf50}" +
        ".badge-inline.badge-warning{color:hex(accentWarn);border-color:hex(accentWarn)}" +
        ".badge-inline.badge-failed,.badge-inline.badge-error{color:hex(accentDanger);border-color:hex(accentDanger)}" +
        ".audio-block{margin:8px 0;padding:8px;background:" + hex(preBg) + ";border-radius:0}" +
        ".audio-block audio{width:100%;display:block}" +
        ".audio-label{font-size:11px;opacity:0.6;margin-top:4px}" +
        ".chart-block{background:" + hex(preBg) + ";border-radius:0;margin:8px 0;padding:12px}" +
        ".chart-title{font-weight:600;color:" + hex(primary.toArgb()) + ";margin-bottom:8px}" +
        ".chart-note{font-size:12px;opacity:0.7;margin-top:8px}" +
        ".bar-row{display:flex;align-items:center;gap:8px;margin:4px 0}" +
        ".bar-label{flex:0 0 100px;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
        ".bar-track{flex:1;height:16px;background:" + hex(surface.toArgb()) + ";border-radius:3px;overflow:hidden;position:relative}" +
        ".bar-fill{height:100%;background:" + hex(primary.toArgb()) + ";transition:width .3s}" +
        ".bar-val{font-size:11px;opacity:0.8;min-width:52px;text-align:right;font-family:monospace}" +
        ".polar-wrap{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}" +
        ".polar-item{padding:4px 8px;border-radius:4px;background:" + hex(surface.toArgb()) + ";font-size:12px}" +
        ".step-block{background:" + hex(preBg) + ";border-radius:0;margin:8px 0;padding:10px 12px}" +
        ".step-title{font-weight:600;color:" + hex(primary.toArgb()) + ";margin-bottom:8px}" +
        ".step-item{display:flex;gap:10px;padding:6px 0;border-bottom:1px solid " + hex(outlineVariant) + "}" +
        ".step-item:last-child{border-bottom:none}" +
        ".step-idx{flex-shrink:0;width:22px;height:22px;border-radius:50%;background:" + hex(primary.toArgb()) + ";color:" + hex(bg.toArgb()) + ";font-size:12px;font-weight:600;display:flex;align-items:center;justify-content:center}" +
        ".step-label{font-weight:600}" +
        ".step-detail{font-size:12px;opacity:0.75;margin-top:2px}" +
        ".step-repeat .step-idx{background:" + hex(comment) + "}" +
        ".timeline-block{background:" + hex(preBg) + ";border-radius:0;margin:8px 0;padding:10px 12px}" +
        ".timeline-title{font-weight:600;color:" + hex(primary.toArgb()) + ";margin-bottom:8px}" +
        ".timeline-block ul{list-style:none;padding-left:0;margin:0;position:relative}" +
        ".timeline-block ul::before{content:'';position:absolute;left:8px;top:6px;bottom:6px;width:2px;background:" + hex(primary.toArgb()) + ";opacity:0.3}" +
        ".timeline-block li{position:relative;padding-left:24px;margin:6px 0}" +
        ".timeline-block li::before{content:'';position:absolute;left:4px;top:6px;width:10px;height:10px;border-radius:50%;background:" + hex(primary.toArgb()) + "}" +
        ".tl-label{font-weight:600;color:" + hex(fgColor) + "}" +
        ".tl-time{display:block;font-size:12px;opacity:0.7;margin-top:2px;white-space:pre-wrap}" +
        ".tabs-block{margin:8px 0;background:" + hex(preBg) + ";border-radius:0;overflow:hidden}" +
        ".tabs-nav{display:flex;border-bottom:1px solid " + hex(outlineVariant) + ";overflow-x:auto}" +
        ".tab-btn{flex-shrink:0;background:transparent;border:none;padding:8px 14px;color:" + hex(fgColor) + ";font-size:13px;cursor:pointer;opacity:0.6;border-bottom:2px solid transparent}" +
        ".tab-btn.active{opacity:1;border-bottom-color:" + hex(primary.toArgb()) + ";color:" + hex(primary.toArgb()) + "}" +
        ".tabs-body{padding:10px 12px}" +
        ".tab-pane{display:none}" +
        ".tab-pane.active{display:block}" +
        ".accordion{background:" + hex(preBg) + ";border-radius:0;margin:8px 0;padding:0;border:1px solid " + hex(outlineVariant) + "}" +
        ".accordion summary{padding:10px 12px;cursor:pointer;font-weight:600;color:" + hex(primary.toArgb()) + ";list-style:none}" +
        ".accordion summary::-webkit-details-marker{display:none}" +
        ".accordion summary::before{content:'\\25B8';display:inline-block;margin-right:6px;transition:transform .2s}" +
        ".accordion[open] summary::before{transform:rotate(90deg)}" +
        ".accordion-body{padding:0 12px 10px}" +
        ".progress-block{position:relative;background:" + hex(preBg) + ";border-radius:0;height:22px;margin:8px 0;overflow:hidden}" +
        ".progress-bar{height:100%;background:" + hex(primary.toArgb()) + "}" +
        ".progress-block[data-color='green'] .progress-bar{background:#4caf50}" +
        ".progress-block[data-color='red'] .progress-bar{background:hex(accentDanger)}" +
        ".progress-block[data-color='yellow'] .progress-bar{background:hex(accentWarn)}" +
        ".progress-block[data-color='purple'] .progress-bar{background:#9c27b0}" +
        ".progress-label{position:absolute;top:0;left:8px;line-height:22px;font-size:12px;color:" + hex(fgColor) + ";font-family:monospace}" +
        "::-webkit-scrollbar{display:none}" +
        ".hljs-keyword{color:" + hex(keyword) + "}" +
        ".hljs-built_in{color:" + hex(keyword) + "}" +
        ".hljs-string{color:" + hex(stringC) + "}" +
        ".hljs-comment{color:" + hex(comment) + ";font-style:italic}" +
        ".hljs-number{color:" + hex(number) + "}" +
        ".hljs-title{color:" + hex(keyword) + "}" +
        ".hljs-function{color:" + hex(function) + "}" +
        ".hljs-variable{color:" + hex(fgColor) + "}.hljs-annotation{color:" + hex(annotation) + "}.hljs-type{color:" + hex(keyword) + "}.hljs-meta{color:" + hex(comment) + "}" +
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
        "var type=m[1].toLowerCase();if(type==='accordion'||type==='timeline'||type==='step'||type==='chart'){out.push(l);i++;continue;}var title=m[2]||'';var body=[];i++;" +
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
        "md=md.replace(/(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/watch\\?v=|youtu\\.be\\/)([a-zA-Z0-9_-]{11})[^\\s]*/g,function(_,id){return '<div class=\\\"youtube-embed\\\"><iframe src=\\\"https://www.youtube.com/embed/'+id+'?rel=0\\\" allow=\\\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share\\\" allowfullscreen referrerpolicy=\\\"strict-origin-when-cross-origin\\\"></iframe></div>';});" +
        "md=md.replace(/(?:https?:\\/\\/)?(?:www\\.)?(?:twitter\\.com|x\\.com)\\/([a-zA-Z0-9_]+)\\/status\\/(\\d+)[^\\s]*/g,function(_,user,tid){return '<a class=\\\"twitter-card\\\" href=\\\"https://twitter.com/'+user+'/status/'+tid+'\\\" target=\\\"_blank\\\" rel=\\\"noopener\\\"><span class=\\\"tw-user\\\">@'+user+'</span><span class=\\\"tw-body\\\">Lihat Tweet '+(tid||'')+' di Twitter</span></a>';});" +
        "var _al=md.split('\\n');var _aout=[];var _ai=0;while(_ai<_al.length){var _ac=_al[_ai];var _am=_ac.match(/^> \\[!accordion\\]\\s*(.*)$/i);if(_am){var _at=_am[1]||'Details';var _ab=[];_ai++;while(_ai<_al.length&&_al[_ai].startsWith('> ')){_ab.push(_al[_ai].slice(2));_ai++;}_aout.push('<details class=\\\"accordion\\\"><summary>'+_at+'</summary><div class=\\\"accordion-body\\\">'+marked.parse(_ab.join('\\n'))+'</div></details>');}else{_aout.push(_ac);_ai++;}}md=_aout.join('\\n');" +
        "md=md.replace(/!\\[badge(?::(left|right))?\\]\\(([^)]+)\\)/g,function(_,pos,content){var parts=content.split(':');var st=(parts[0]||'info').toLowerCase().trim();var tx=(parts[1]||st).trim();var cls='badge-inline '+(pos?'badge-'+pos:'');return '<span class=\\\"'+cls+' badge-'+st+'\\\">'+tx.toUpperCase()+'</span>';});" +
        "md=md.replace(/!\\[audio\\]\\(([^)]*)\\)/g,function(_,url){return '<div class=\\\"audio-block\\\"><audio controls preload=\\\"none\\\" src=\\\"'+url+'\\\"></audio><div class=\\\"audio-label\\\">'+(url?'Audio':'Audio (no source)')+'</div></div>';});" +
        "var _chl=md.split('\\n');var _cho=[];var _chi=0;while(_chi<_chl.length){var _chc=_chl[_chi];var _chm=_chc.match(/^> \\[!chart\\]\\s*(.*)$/i);if(_chm){var _cht=_chm[1]||'Chart';var _chb=[];_chi++;while(_chi<_chl.length&&_chl[_chi].startsWith('> ')){_chb.push(_chl[_chi].slice(2));_chi++;}var _cl2=_chb.map(function(x){return x.trim();}).filter(Boolean);var _ctype=(_cl2[0]||'bar').toLowerCase();var _cdata=[];var _cnote='';for(var _q=1;_q<_cl2.length;_q++){var _cq=_cl2[_q];if(_cq.indexOf('::')<0)continue;var _cp=_cq.split('::');var _lb=_cp[0].trim();if(_lb.toLowerCase()==='note'){_cnote=_cp.slice(1).join('::').trim();continue;}var _vv=parseFloat(_cp[1])||0;var _mpct=_cp[2]!==undefined?parseFloat(_cp[2]):undefined;if(_lb)_cdata.push({label:_lb,value:_vv,pct:_mpct});}var _tot=_cdata.reduce(function(s2,d2){return s2+d2.value;},0);_cdata.forEach(function(d2){if(d2.pct===undefined||isNaN(d2.pct))d2.pct=_tot>0?Math.round(d2.value/_tot*100):0;});var _chh='<div class=\\\"chart-block\\\"><div class=\\\"chart-title\\\">'+_cht+'</div>';if(_ctype==='bar'){_cdata.forEach(function(d2){_chh+='<div class=\\\"bar-row\\\"><div class=\\\"bar-label\\\">'+d2.label+'</div><div class=\\\"bar-track\\\"><div class=\\\"bar-fill\\\" style=\\\"width:'+d2.pct+'%\\\"></div></div><div class=\\\"bar-val\\\">'+d2.value+' ('+d2.pct+'%)</div></div>';});}else{_chh+='<div class=\\\"polar-wrap\\\">';_cdata.forEach(function(d2){_chh+='<div class=\\\"polar-item\\\">'+d2.label+': '+d2.value+' ('+d2.pct+'%)</div>';});_chh+='</div>';}if(_cnote)_chh+='<div class=\\\"chart-note\\\">'+_cnote+'</div>';_chh+='</div>';_cho.push(_chh);}else{_cho.push(_chc);_chi++;}}md=_cho.join('\\n');" +
        "var _stl=md.split('\\n');var _sto=[];var _sti=0;while(_sti<_stl.length){var _stc=_stl[_sti];var _stm=_stc.match(/^> \\[!step\\]\\s*(.*)$/i);if(_stm){var _stt=_stm[1]||'Steps';var _stb=[];_sti++;while(_sti<_stl.length&&_stl[_sti].startsWith('> ')){_stb.push(_stl[_sti].slice(2));_sti++;}var _hasLoop=_stb.join('\\n').indexOf('::loop')>=0;var _clean=_stb.join('\\n').replace(/::loop/g,'');var _items=[];var _cl=_clean.split('\\n');for(var _n=0;_n<_cl.length;_n++){var _ln=_cl[_n].trim();if(!_ln||_ln.indexOf('::')<0)continue;var _rep=_ln.endsWith('::repeat');if(_rep)_ln=_ln.slice(0,-8).trim();var _sp=_ln.split('::');_items.push({label:_sp[0].trim(),detail:_sp.slice(1).join('::').trim(),repeat:_rep});}var _sth='<div class=\\\"step-block\\\"><div class=\\\"step-title\\\">'+_stt+'</div>';for(var _n2=0;_n2<_items.length;_n2++){var _it=_items[_n2];_sth+='<div class=\\\"step-item'+(_it.repeat?' step-repeat':'')+'\\\"><span class=\\\"step-idx\\\">'+(_n2+1)+'</span><div><div class=\\\"step-label\\\">'+_it.label+'</div>'+(_it.detail?'<div class=\\\"step-detail\\\">'+_it.detail+'</div>':'')+'</div></div>';}_sth+='</div>';_sto.push(_sth);}else{_sto.push(_stc);_sti++;}}md=_sto.join('\\n');" +
        "var _tml=md.split('\\n');var _tmo=[];var _tmi=0;while(_tmi<_tml.length){var _tlc=_tml[_tmi];var _tmm=_tlc.match(/^> \\[!timeline\\]\\s*(.*)$/i);if(_tmm){var _ttm=_tmm[1]||'Timeline';var _tmb=[];_tmi++;while(_tmi<_tml.length&&_tml[_tmi].startsWith('> ')){_tmb.push(_tml[_tmi].slice(2));_tmi++;}var _evs=[];var _cur=null;for(var _k=0;_k<_tmb.length;_k++){var _lk=_tmb[_k].trim();if(!_lk)continue;if(_lk.indexOf('::')>=0){var _pp=_lk.split('::');_cur={label:_pp[0].trim(),time:_pp.slice(1).join('::').trim()};if(_cur.label)_evs.push(_cur);}else if(_cur){_cur.time+='\\n'+_lk;}}var _tth='<div class=\\\"timeline-block\\\"><div class=\\\"timeline-title\\\">'+_ttm+'</div><ul>';for(var _k2=0;_k2<_evs.length;_k2++){_tth+='<li><span class=\\\"tl-label\\\">'+_evs[_k2].label+'</span><span class=\\\"tl-time\\\">'+_evs[_k2].time+'</span></li>';}_tth+='</ul></div>';_tmo.push(_tth);}else{_tmo.push(_tlc);_tmi++;}}md=_tmo.join('\\n');" +
        "var _tl=md.split('\\n');var _to=[];var _ti=0;while(_ti<_tl.length){var _tl2=_tl[_ti];var _tm2=_tl2.trim().match(/^::tabs\\s+\\[(.+?)\\]/);if(_tm2){var _tabs=[];while(_ti<_tl.length){var _tln=_tl[_ti].trim();var _tm3=_tln.match(/^::tabs\\s+\\[(.+?)\\]/);if(_tm3){_tabs.push({title:_tm3[1],body:[]});_ti++;continue;}if(_tln==='::tabs-end'){_ti++;break;}if(_tabs.length>0)_tabs[_tabs.length-1].body.push(_tl[_ti]);_ti++;}var _th='<div class=\\\"tabs-block\\\"><div class=\\\"tabs-nav\\\">';for(var _u=0;_u<_tabs.length;_u++){_th+='<button class=\\\"tab-btn'+(_u===0?' active':'')+'\\\" data-tab=\\\"'+_u+'\\\">'+_tabs[_u].title+'</button>';}_th+='</div><div class=\\\"tabs-body\\\">';for(var _u2=0;_u2<_tabs.length;_u2++){_th+='<div class=\\\"tab-pane'+(_u2===0?' active':'')+'\\\" data-pane=\\\"'+_u2+'\\\">'+marked.parse(_tabs[_u2].body.join('\\n'))+'</div>';}_th+='</div></div>';_to.push(_th);}else{_to.push(_tl2);_ti++;}}md=_to.join('\\n');" +
        "md=md.replace(/\\[progress\\s+(\\d+)%\\s*(\\w+)?\\]/g,function(_,p,c){c=c||'blue';return '<div class=\\\"progress-block\\\" data-color=\\\"'+c+'\\\"><div class=\\\"progress-bar\\\" style=\\\"width:'+p+'%\\\"></div><div class=\\\"progress-label\\\">'+p+'%</div></div>';});" +
        "document.getElementById('content').innerHTML=marked.parse(md);" +
        "document.querySelectorAll('.tabs-block').forEach(function(tb){var btns=tb.querySelectorAll('.tab-btn');var panes=tb.querySelectorAll('.tab-pane');btns.forEach(function(b){b.addEventListener('click',function(){var idx=b.getAttribute('data-tab');btns.forEach(function(x){x.classList.remove('active');});b.classList.add('active');panes.forEach(function(x){x.classList.toggle('active',x.getAttribute('data-pane')===idx);});});});});" +
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
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                setBackgroundColor(bgColor)
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
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
