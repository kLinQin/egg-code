package com.egg.codeeditor.model

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.rosemoe.sora.widget.CodeEditor

data class CodeEditorState(
    var editor: CodeEditor? = null,
    val initialText: String = ""
) {
    var text by mutableStateOf(initialText)
}

@Composable
fun rememberCodeEditorState(initialText: String = "") = remember {
    CodeEditorState(initialText = initialText)
}

data class FileNode(
    val name: String,
    val isDirectory: Boolean,
    val uri: Uri,
    val depth: Int
)

data class GitStatusInfo(
    val added: List<String> = emptyList(),
    val modified: List<String> = emptyList(),
    val untracked: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val staged: List<String> = emptyList(),
    val unstaged: List<String> = emptyList()
)

data class EditorTab(
    val id: Long,
    val name: String,
    val uri: Uri?,
    var content: String = "",
    var isDirty: Boolean = false,
    val diffOld: String? = null,
    val diffNew: String? = null
)

/** Editor settings — persisted to SharedPreferences (see loadSettings/saveSettings in EggApp). */
data class EditorSettings(
    val themeName: String = "github-light", // "github-light" | "darcula"
    val fontSizeSp: Int = 14,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val tabWidth: Int = 4,
    val showBottomBar: Boolean = true,
    // ── Apps ──
    val fontFamily: String = "monospace", // "monospace" | "sans" | "serif" | "custom"
    val customFontPath: String? = null,   // path to uploaded font
    // ── App UI font (separate from editor) ──
    val appFontFamily: String = "sans", // "sans" | "serif" | "monospace" | "custom"
    val appCustomFontPath: String? = null,
    val appFontScale: Float = 1.0f,
    // ── Editor ──
    val confirmExit: Boolean = true,
    val autoIndent: Boolean = true,
    val autocomplete: Boolean = true,
    val indentGuide: Boolean = true,
    val highlightActiveLine: Boolean = true,
    val closeBracket: Boolean = true
)

/** Project-wide search result. */
data class ProjectSearchResult(
    val name: String,
    val uri: Uri,
    val preview: String
)