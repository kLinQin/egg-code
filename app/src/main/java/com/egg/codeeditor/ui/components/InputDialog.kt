package com.egg.codeeditor.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.height

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape

@Composable
fun InputDialog(
    title: String,
    initialText: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") })
                if (extraContent != null) {
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
                    extraContent()
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}