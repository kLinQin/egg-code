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
private const val KEY_APP_FONT_FAMILY = "app_font_family"
private const val KEY_APP_CUSTOM_FONT_PATH = "app_custom_font_path"
private const val KEY_APP_FONT_SCALE = "app_font_scale"
private const val KEY_CONFIRM_EXIT = "confirm_exit"
private const val KEY_AUTO_INDENT = "auto_indent"
private const val KEY_AUTOCOMPLETE = "autocomplete"
private const val KEY_INDENT_GUIDE = "indent_guide"
private const val KEY_HIGHLIGHT_ACTIVE_LINE = "highlight_active_line"
private const val KEY_CLOSE_BRACKET = "close_bracket"
private const val KEY_LAST_PROJECT_URI = "last_project_uri"
private const val KEY_LAST_PRIVATE_ROOT = "last_private_root"
private const val KEY_RECENT_PROJECTS = "recent_projects"
private const val KEY_OPEN_TABS = "open_tabs"
private const val KEY_B2_KEY_ID = "b2_key_id"
private const val KEY_B2_APP_KEY = "b2_app_key"
private const val KEY_B2_BUCKET = "b2_bucket"
private const val KEY_GITHUB_USER = "github_user"
private const val KEY_GITHUB_TOKEN = "github_token"
private const val KEY_EDITOR_FONT_LIST = "editor_font_list"
private const val KEY_APP_FONT_LIST = "app_font_list"

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
         appFontFamily = prefs.getString(KEY_APP_FONT_FAMILY, "sans") ?: "sans",
        appCustomFontPath = prefs.getString(KEY_APP_CUSTOM_FONT_PATH, null),
        appFontScale = prefs.getFloat(KEY_APP_FONT_SCALE, 1.0f),
       confirmExit = prefs.getBoolean(KEY_CONFIRM_EXIT, true),
        autoIndent = prefs.getBoolean(KEY_AUTO_INDENT, true),
        autocomplete = prefs.getBoolean(KEY_AUTOCOMPLETE, true),
        indentGuide = prefs.getBoolean(KEY_INDENT_GUIDE, true),
        highlightActiveLine = prefs.getBoolean(KEY_HIGHLIGHT_ACTIVE_LINE, true),
        closeBracket = prefs.getBoolean(KEY_CLOSE_BRACKET, true)
    )
}

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
         .putString(KEY_APP_FONT_FAMILY, settings.appFontFamily)
        .putString(KEY_APP_CUSTOM_FONT_PATH, settings.appCustomFontPath)
        .putFloat(KEY_APP_FONT_SCALE, settings.appFontScale)
       .putBoolean(KEY_CONFIRM_EXIT, settings.confirmExit)
        .putBoolean(KEY_AUTO_INDENT, settings.autoIndent)
        .putBoolean(KEY_AUTOCOMPLETE, settings.autocomplete)
        .putBoolean(KEY_INDENT_GUIDE, settings.indentGuide)
        .putBoolean(KEY_HIGHLIGHT_ACTIVE_LINE, settings.highlightActiveLine)
        .putBoolean(KEY_CLOSE_BRACKET, settings.closeBracket)
        .apply()
}

fun saveLastProjectUri(context: Context, uri: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_LAST_PROJECT_URI, uri).apply()
}

fun loadLastProjectUri(context: Context): String? {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LAST_PROJECT_URI, null)
}

fun clearLastProjectUri(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().remove(KEY_LAST_PROJECT_URI).apply()
}

fun saveLastPrivateRoot(context: Context, path: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_LAST_PRIVATE_ROOT, path).apply()
}

fun loadLastPrivateRoot(context: Context): String? =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LAST_PRIVATE_ROOT, null)

fun clearLastPrivateRoot(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().remove(KEY_LAST_PRIVATE_ROOT).apply()
}

fun saveRecentProjects(context: Context, json: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_RECENT_PROJECTS, json).apply()
}

fun loadRecentProjects(context: Context): String? =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_RECENT_PROJECTS, null)

fun clearRecentProjects(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().remove(KEY_RECENT_PROJECTS).apply()
}

fun saveB2Credentials(context: Context, keyId: String, appKey: String, bucket: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_B2_KEY_ID, keyId)
        .putString(KEY_B2_APP_KEY, appKey)
        .putString(KEY_B2_BUCKET, bucket)
        .apply()
}

data class B2Credentials(val keyId: String, val appKey: String, val bucket: String)

fun loadB2Credentials(context: Context): B2Credentials? {
    val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val k = p.getString(KEY_B2_KEY_ID, null) ?: return null
    val a = p.getString(KEY_B2_APP_KEY, null) ?: return null
    val b = p.getString(KEY_B2_BUCKET, null) ?: return null
    if (k.isEmpty() || a.isEmpty() || b.isEmpty()) return null
    return B2Credentials(k, a, b)
}

fun clearB2Credentials(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().remove(KEY_B2_KEY_ID).remove(KEY_B2_APP_KEY).remove(KEY_B2_BUCKET).apply()
}

fun saveOpenTabs(context: Context, json: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_OPEN_TABS, json).apply()
}

fun loadOpenTabs(context: Context): String? =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_OPEN_TABS, null)

fun clearOpenTabs(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().remove(KEY_OPEN_TABS).apply()
}

fun saveGitCredentials(context: Context, user: String, token: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_GITHUB_USER, user).putString(KEY_GITHUB_TOKEN, token).apply()
}

fun loadGitCredentials(context: Context): Pair<String, String>? {
    val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val u = p.getString(KEY_GITHUB_USER, null) ?: return null
    val t = p.getString(KEY_GITHUB_TOKEN, null) ?: return null
    if (u.isEmpty() && t.isEmpty()) return null
    return Pair(u, t)
}
