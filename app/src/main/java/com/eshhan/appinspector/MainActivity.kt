package com.eshhan.appinspector

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.Collator

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Bitmap,
    val isSystem: Boolean,
    val launchIntent: Intent?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 245, 252)
        window.navigationBarColor = android.graphics.Color.rgb(247, 245, 252)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF6750A4),
                    secondary = androidx.compose.ui.graphics.Color(0xFF625B71),
                    background = androidx.compose.ui.graphics.Color(0xFFF7F5FC),
                    surface = androidx.compose.ui.graphics.Color.White
                )
            ) {
                AppInspectorScreen(
                    loadApps = { withContext(Dispatchers.IO) { readInstalledApps() } },
                    openApp = { app -> app.launchIntent?.let { startActivity(it) } },
                    openSettings = { app ->
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${app.packageName}")
                        })
                    }
                )
            }
        }
    }

    private fun readInstalledApps(): List<InstalledApp> {
        val pm = packageManager
        val applications = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        val collator = Collator.getInstance()
        return applications.mapNotNull { info ->
            try {
                val label = pm.getApplicationLabel(info).toString().ifBlank { info.packageName }
                val drawable = pm.getApplicationIcon(info)
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                InstalledApp(
                    label = label,
                    packageName = info.packageName,
                    icon = bitmap,
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    launchIntent = pm.getLaunchIntentForPackage(info.packageName)
                )
            } catch (_: Exception) {
                null
            }
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppInspectorScreen(
    loadApps: suspend () -> List<InstalledApp>,
    openApp: (InstalledApp) -> Unit,
    openSettings: (InstalledApp) -> Unit
) {
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        error = null
        try {
            apps = loadApps()
        } catch (e: Exception) {
            error = e.message ?: "Could not load installed apps."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val filtered = remember(apps, query, showSystem) {
        apps.filter { app ->
            (showSystem || !app.isSystem) &&
                (app.label.contains(query, ignoreCase = true) ||
                 app.packageName.contains(query, ignoreCase = true))
        }
    }
    val userCount = apps.count { !it.isSystem }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Inspector", fontWeight = FontWeight.Bold)
                        Text("Installed application manager", fontSize = 12.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refresh() } }) {
                        Icon(Icons.Default.Apps, contentDescription = "App inventory")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("All apps", apps.size.toString(), Modifier.weight(1f))
                StatCard("User apps", userCount.toString(), Modifier.weight(1f))
                StatCard("Shown", filtered.size.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search apps or package names") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showSystem,
                    onClick = { showSystem = !showSystem },
                    label = { Text(if (showSystem) "System apps included" else "User apps only") }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { refresh() } }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh apps")
                }
                TextButton(onClick = { query = ""; showSystem = true }) {
                    Text("Reset")
                }
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Loading installed applications…", modifier = Modifier.padding(16.dp))
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                Button(onClick = { scope.launch { refresh() } }) { Text("Try again") }
            } else {
                Text(
                    "${filtered.size} applications",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(app = app, onClick = { selected = app })
                    }
                }
            }
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
            },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Text(if (app.isSystem) "System application" else "User-installed application")
                    if (app.launchIntent == null) {
                        Text("No launcher activity is available for this package.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    openSettings(app)
                    selected = null
                }) { Text("App info") }
            },
            dismissButton = {
                Row {
                    if (app.launchIntent != null) {
                        TextButton(onClick = {
                            openApp(app)
                            selected = null
                        }) { Text("Open") }
                    }
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            }
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = "${app.label} icon",
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (app.isSystem) "SYSTEM" else "USER APP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
