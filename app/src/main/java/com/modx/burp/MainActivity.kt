package com.modx.burp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class CapturedExchange(
    val method: String, val url: String, val status: Int, val durationMs: Long,
    val responseHeaders: String, val responseBody: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MODxBurpApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MODxBurpApp() {
    var selected by remember { mutableStateOf("Repeater") }
    val history = remember { mutableStateListOf<CapturedExchange>() }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("MODx BURP") }) },
            bottomBar = {
                NavigationBar {
                    listOf("Proxy", "History", "Repeater", "Decoder").forEach { tab ->
                        NavigationBarItem(selected == tab, { selected = tab }, icon = { Text(tab.take(1)) }, label = { Text(tab) })
                    }
                }
            }
        ) { padding ->
            when (selected) {
                "Repeater" -> RepeaterScreen(Modifier.padding(padding), history)
                "History" -> HistoryScreen(Modifier.padding(padding), history)
                "Proxy" -> InfoScreen(Modifier.padding(padding), "Proxy capture",
                    "Live device-wide proxy capture is not implemented yet. This screen is intentionally marked as unavailable until the VPN/proxy engine is implemented and tested.")
                else -> InfoScreen(Modifier.padding(padding), "Decoder", "URL and Base64 decoder utilities are planned.")
            }
        }
    }
}

@Composable
private fun RepeaterScreen(modifier: Modifier, history: androidx.compose.runtime.snapshots.SnapshotStateList<CapturedExchange>) {
    var method by remember { mutableStateOf("GET") }
    var url by remember { mutableStateOf("https://example.com/") }
    var headers by remember { mutableStateOf("User-Agent: MODx-Burp-Android/0.1") }
    var body by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CapturedExchange?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Repeater", style = MaterialTheme.typography.headlineSmall)
        Text("Send manually composed requests to systems you own or are authorized to test.", color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(method) }
                DropdownMenu(expanded, { expanded = false }) {
                    methods.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { method = item; expanded = false }) }
                }
            }
            OutlinedTextField(url, { url = it }, label = { Text("Request URL") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        OutlinedTextField(headers, { headers = it }, label = { Text("Headers (one per line)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(body, { body = it }, label = { Text("Request body") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(enabled = !busy && (url.startsWith("http://") || url.startsWith("https://")), onClick = {
            busy = true; error = null; result = null
            scope.launch {
                try {
                    val exchange = withContext(Dispatchers.IO) { executeRequest(method, url, headers, body) }
                    result = exchange
                    history.add(0, exchange)
                } catch (t: Throwable) { error = t.message ?: "Request failed" }
                finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Sending…" else "Send request") }
        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        result?.let { exchange ->
            Text("Response  •  ${exchange.status}  •  ${exchange.durationMs} ms", style = MaterialTheme.typography.titleMedium)
            Text("Response headers", style = MaterialTheme.typography.labelLarge)
            Text(exchange.responseHeaders, style = MaterialTheme.typography.bodySmall)
            Text("Response body", style = MaterialTheme.typography.labelLarge)
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Text(exchange.responseBody.ifBlank { "(empty response)" }, Modifier.padding(10.dp).heightIn(max = 280.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun executeRequest(method: String, url: String, rawHeaders: String, body: String): CapturedExchange {
    val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(false).build()
    val builder = Request.Builder().url(url)
    rawHeaders.lineSequence().filter { it.isNotBlank() && ':' in it }.forEach { line ->
        val index = line.indexOf(':')
        builder.addHeader(line.substring(0, index).trim(), line.substring(index + 1).trim())
    }
    val payload = body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    when (method) {
        "GET" -> builder.get()
        "HEAD" -> builder.head()
        "POST", "PUT", "PATCH", "DELETE" -> builder.method(method, if (method == "DELETE" && body.isBlank()) null else payload)
        else -> builder.method(method, null)
    }
    val started = System.nanoTime()
    client.newCall(builder.build()).execute().use { response ->
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        val responseHeaders = response.headers.toMultimap().entries.joinToString("\n") { "${it.key}: ${it.value.joinToString(", ")}" }
        val responseBody = response.body?.string().orEmpty()
        return CapturedExchange(method, url, response.code, elapsed, responseHeaders, responseBody)
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, history: List<CapturedExchange>) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("HTTP history", style = MaterialTheme.typography.headlineSmall)
        if (history.isEmpty()) Text("No requests yet. Send a request from Repeater.")
        LazyColumn {
            items(history) { item ->
                ListItem(headlineContent = { Text("${item.method}  ${item.status}") },
                    supportingContent = { Text(item.url, maxLines = 2) },
                    trailingContent = { Text("${item.durationMs} ms") })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun InfoScreen(modifier: Modifier, title: String, message: String) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Text("Status: planned / not yet implemented", color = MaterialTheme.colorScheme.secondary)
    }
}
