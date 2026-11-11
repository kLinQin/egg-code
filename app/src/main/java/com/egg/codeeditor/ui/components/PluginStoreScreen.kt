package com.egg.codeeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.egg.codeeditor.extension.ExtensionManager
import com.egg.codeeditor.extension.ExtensionLoader
import com.egg.codeeditor.extension.PluginStoreService
import com.egg.codeeditor.extension.StorePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PluginStoreScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var plugins by remember { mutableStateOf<List<StorePlugin>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf<String>("all") }

    suspend fun refresh() {
        loading = true
        error = null
        val res = PluginStoreService.fetchIndex()
        res.onSuccess { plugins = it }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plugin Store", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { refresh() } }) { Text("Refresh", fontSize = 12.sp) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "All", "theme" to "Theme", "grammar" to "Grammar").forEach { (k, label) ->
                FilterChip(
                    selected = filter == k,
                    onClick = { filter = k },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        status?.let {
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
        }
        error?.let {
            Text("Error: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            return@Column
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val filtered = if (filter == "all") plugins else plugins.filter { it.type == filter }
        if (filtered.isEmpty()) {
            Text("No plugins.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filtered.forEach { plugin ->
                val installed = PluginStoreService.isInstalled(context, plugin.id)
                val isInstalling = installing.contains(plugin.id)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(plugin.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "v${plugin.version}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${plugin.type} · by ${plugin.author}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (plugin.description.isNotEmpty()) {
                                Text(plugin.description, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        if (isInstalling) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (installed) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            ExtensionManager.unload(plugin.id)
                                            val dir = java.io.File(ExtensionLoader.extensionsRoot(context), plugin.id.substringAfterLast('.'))
                                            dir.deleteRecursively()
                                            val egg = java.io.File(ExtensionLoader.extensionsRoot(context), plugin.id.substringAfterLast('.') + ".egg")
                                            egg.delete()
                                        }
                                        ExtensionManager.loadAll(context)
                                        status = "Uninstalled: ${plugin.name}"
                                    }
                                }
                            ) { Text("Uninstall", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                        } else {
                            Button(
                                onClick = {
                                    installing = installing + plugin.id
                                    scope.launch {
                                        val res = PluginStoreService.installFromStore(context, plugin)
                                        installing = installing - plugin.id
                                        status = res.fold(
                                            onSuccess = {
                                                ExtensionManager.loadAll(context)
                                                "Installed: ${plugin.name}"
                                            },
                                            onFailure = { "Install failed: ${it.message}" }
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("Install", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}
