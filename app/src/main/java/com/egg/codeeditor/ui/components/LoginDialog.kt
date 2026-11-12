package com.egg.codeeditor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, name: String) -> Unit
) {
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == "login") "Login" else "Sign Up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == "signup") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { mode = if (mode == "login") "signup" else "login" }) {
                        Text(if (mode == "login") "Need an account? Sign up" else "Have an account? Login")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (mode == "login") onLogin(email, password) else onSignUp(email, password, name)
            }) { Text(if (mode == "login") "Login" else "Sign Up") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
