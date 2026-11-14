package com.egg.codeeditor.ui.components

interface FileType {
    val extensions: List<String>
    val names: List<String>? get() = null
    val iconName: String?
    val iconOverride: Map<String, String>? get() = null
    val title: String
    val textmateScope: String? get() = null
    val markdownNames: List<String> get() = emptyList()
}
