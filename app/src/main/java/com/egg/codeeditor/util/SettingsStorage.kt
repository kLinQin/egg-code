package com.egg.codeeditor.util

import android.content.Context
import com.egg.codeeditor.model.EditorSettings

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

/** Load editor settings from SharedPreferences (moved from EggApp.kt). */
fun loadSettings(context: Context): EditorSettings {
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

/** Save editor settings to SharedPreferences (moved from EggApp.kt). */
fun saveSettings(context: Context, settings: EditorSettings) {
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