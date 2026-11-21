package com.modx.burp.capture

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class CaptureApp(val label: String, val packageName: String)

@Composable
fun DnsCaptureScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.let { info -> CaptureApp(it.loadLabel(context.packageManager).toString(), info.packageName) } }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }
    val selected = remember { mutableStateListOf<String>() }
    var records by remember { mutableStateOf<List<String>>(emptyList()) }
    var active by remember { mutableStateOf(false) }
    var pendingLaunch by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        active = context.getSharedPreferences(DnsCaptureService.PREFS, 0).getBoolean(DnsCaptureService.KEY_RUNNING, false)
        val file = File(context.filesDir, DnsCaptureService.LOG_FILE)
        records = if (file.exists()) runCatching { file.readLines().takeLast(500).reversed() }.getOrDefault(emptyList()) else emptyList()
    }

    fun startCapture() {
        if (selected.isEmpty()) { message = "Pehle kam se kam ek app select karein."; return }
        val serviceIntent = Intent(context, DnsCaptureService::class.java)
            .putStringArrayListExtra(DnsCaptureService.EXTRA_PACKAGES, ArrayList(selected))
        ContextCompat.startForegroundService(context, serviceIntent)
        pendingLaunch = true
        message = "DNS metadata capture requested. Android notification mein service status dekhein."
        reload()
    }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startCapture()
        else message = "VPN permission approve nahi hui."
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(records.asReversed().joinToString("\n", postfix = "\n").toByteArray()) }
                    ?: error("Export file open nahi hui")
            }.onSuccess { message = "DNS log export ho gaya." }.onFailure { message = it.message ?: "Export failed." }
        }
    }

    LaunchedEffect(active) {
        if (active && pendingLaunch) {
            pendingLaunch = false
            selected.firstOrNull()?.let { packageName ->
                context.packageManager.getLaunchIntentForPackage(packageName)?.let { context.startActivity(it) }
            }
        }
    }

    LaunchedEffect(Unit) {
        reload()
        while (true) { delay(1000); reload() }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("LIVE CAPTURE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Built-in VPN • DNS metadata", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            AssistChip(onClick = { reload() }, label = { Text(if (active) "ACTIVE" else "STOPPED") })
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Capture controls", fontWeight = FontWeight.Bold)
                Text("This first capture engine records DNS domains and query metadata only. Other IP traffic is not routed through the VPN; HTTPS content is never decrypted.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !active && selected.isNotEmpty(), onClick = {
                        val prepare = VpnService.prepare(context)
                        if (prepare == null) startCapture() else vpnPermission.launch(prepare)
                    }) { Text("START") }
                    OutlinedButton(enabled = active, onClick = {
                        context.stopService(Intent(context, DnsCaptureService::class.java))
                        message = "Capture stop requested."
                        reload()
                    }) { Text("STOP") }
                    OutlinedButton(onClick = {
                        File(context.filesDir, DnsCaptureService.LOG_FILE).delete()
                        message = "Local DNS history cleared."
                        reload()
                    }) { Text("CLEAR") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !active, onClick = { selected.clear(); selected.addAll(apps.map { it.packageName }) }) { Text("SELECT ALL") }
                    TextButton(enabled = !active, onClick = { selected.clear() }) { Text("SELECT NONE") }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = records.isNotEmpty(), onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        exportLauncher.launch("MODx_${stamp}_dns.jsonl")
                    }) { Text("EXPORT JSONL") }
                }
                Text("${selected.size} apps selected • ${records.size} recent records", style = MaterialTheme.typography.labelSmall)
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        Text("Select apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(0.43f).fillMaxWidth()) {
            items(apps, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = app.packageName in selected, enabled = !active, onCheckedChange = { checked ->
                        if (checked && app.packageName !in selected) selected.add(app.packageName)
                        if (!checked) selected.remove(app.packageName)
                    })
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyMedium)
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("DNS activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { reload() }) { Text("REFRESH") }
        }
        LazyColumn(Modifier.weight(0.57f).fillMaxWidth()) {
            items(records) { line ->
                val item = runCatching { JSONObject(line) }.getOrNull()
                if (item?.optString("type") == "dns") {
                    ListItem(
                        headlineContent = { Text(item.optString("domain"), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("QTYPE ${item.optInt("recordType")} • RCODE ${item.optInt("responseCode")} • ${item.optString("time")}") },
                        trailingContent = { Text("${item.optInt("responseBytes")} B", style = MaterialTheme.typography.labelSmall) }
                    )
                    HorizontalDivider()
                } else if (item != null) {
                    ListItem(headlineContent = { Text(item.optString("message", "Capture event")) })
                }
            }
            if (records.isEmpty()) item { Text("No DNS records yet. Select an app and press START.", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.secondary) }
        }
    }
}
