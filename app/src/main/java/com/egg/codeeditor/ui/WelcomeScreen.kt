package com.egg.codeeditor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.util.SupabaseUser

@Composable
fun WelcomeScreen(
    onOpenFolder: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenGit: () -> Unit,
    onOpenAppStorage: () -> Unit,
    recentProjects: List<Pair<String, String>> = emptyList(),
    onOpenRecent: (String, String) -> Unit = { _, _ -> },
    onRemoveRecent: (String) -> Unit = {},
    versionText: String = "",
    supabaseUser: SupabaseUser? = null,
    onLogin: (email: String, password: String) -> Unit = { _, _ -> },
    onSignUp: (email: String, password: String, name: String) -> Unit = { _, _, _ -> },
    onLogout: () -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.egg.codeeditor.R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(128.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("EGG", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("Code editor", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
            Text("Start", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            WelcomeItem(Icons.Default.Folder, "Open Folder",
                "Browse a folder on this device", onOpenFolder)
            WelcomeItem(Icons.Default.Description, "Open File",
                "Edit a single file without a project", onOpenFile)
            WelcomeItem(Icons.Default.Source, "Open Git Repository",
                "Clone a repository and open it", onOpenGit)
            WelcomeItem(Icons.Default.Storage, "Open from App Storage",
                "Open a cloned repository or pulled bucket", onOpenAppStorage)

            if (recentProjects.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Recent", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                recentProjects.forEach { (label, path) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, modifier = Modifier.weight(1f).clickable { onOpenRecent(label, path) },
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { onRemoveRecent(path) }) { Text("Remove", fontSize = 11.sp) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Account", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (supabaseUser == null) {
                LoginForm(onLogin = onLogin, onSignUp = onSignUp)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        supabaseUser.email ?: supabaseUser.name ?: "Signed in",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp
                    )
                    TextButton(onClick = onLogout) { Text("Logout", fontSize = 11.sp) }
                }
            }

            if (versionText.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    Text(versionText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WelcomeItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}



@Composable
private fun LoginForm(
    onLogin: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit
) {
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            Button(
                onClick = { if (mode == "login") onLogin(email, password) else onSignUp(email, password, name) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (mode == "login") "Login" else "Sign Up") }
        }
    }
}