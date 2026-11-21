package com.modx.burp

import android.os.Bundle
import android.util.Base64
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
                        NavigationBarItem(
                            selected = selected == tab,
                            onClick = { selected = tab },
                            icon = { Text(tab.take(1)) },
                            label = { Text(tab) }
                        )
                    }
                }
            }
        ) { padding ->
            when (selected) {
                "Repeater" -> RepeaterScreen(Modifier.padding(padding), history)
                "History" -> HistoryScreen(Modifier.padding(padding), history)
                "Proxy" -> InfoScreen(Modifier.padding(padding), "Proxy capture",
                    "Device-wide traffic capture is not available in this build. This tab will only be marked active after the Android VPN capture engine is implemented, permission-gated and tested.")
                else -> DecoderScreen(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun RepeaterScreen(modifier: Modifier, history: androidx.compose.runtime.snapshots.SnapshotStateList<CapturedExchange>) {
    var method by remember { mutableStateOf("GET") }
    var url by remember { mutableStateOf("https://example.com/") }
    var headers by remember { mutableStateOf("User-Agent: MODx-BURP/0.2") }
    var body by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CapturedExchange?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Repeater", style = MaterialTheme.typography.headlineSmall)
        Text("Manually compose and inspect requests to systems you own or are authorized to test.", color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(method) }
                DropdownMenu(expanded, { expanded = false }) {
                    methods.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { method = item; expanded = false }) }
                }
            }
            OutlinedTextField(url, { url = it }, label = { Text("Request URL") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        OutlinedTextField(headers, { headers = it }, label = { Text("Headers (one per line: Name: value)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(body, { body = it }, label = { Text("Request body (JSON by default)") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        Button(enabled = !busy, onClick = {
            busy = true; error = null; result = null
            scope.launch {
                try {
                    val exchange = withContext(Dispatchers.IO) { executeRequest(method, url.trim(), headers, body) }
                    result = exchange
                    history.add(0, exchange)
                } catch (t: Throwable) { error = t.message ?: "Request failed" }
                finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Sending…" else "Send request") }
        error?.let { Text("Request error: $it", color = MaterialTheme.colorScheme.error) }
        result?.let { exchange ->
            HorizontalDivider()
            Text("Response  •  ${exchange.status}  •  ${exchange.durationMs} ms", style = MaterialTheme.typography.titleMedium)
            Text("Response headers", style = MaterialTheme.typography.labelLarge)
            SelectableText(exchange.responseHeaders.ifBlank { "(no headers)" })
            Text("Response body", style = MaterialTheme.typography.labelLarge)
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                SelectableText(exchange.responseBody.ifBlank { "(empty response)" }, Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
private fun SelectableText(value: String, modifier: Modifier = Modifier) {
    Text(value, modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
}

private fun executeRequest(method: String, url: String, rawHeaders: String, body: String): CapturedExchange {
    val parsed = java.net.URI(url)
    require(parsed.scheme == "http" || parsed.scheme == "https") { "Only HTTP and HTTPS URLs are supported." }
    require(!parsed.host.isNullOrBlank()) { "Enter a valid URL including its hostname." }
    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).build()
    val builder = Request.Builder().url(url)
    rawHeaders.lineSequence().filter { it.isNotBlank() }.forEach { line ->
        val index = line.indexOf(':')
        require(index > 0) { "Invalid header line: $line" }
        val name = line.substring(0, index).trim()
        val value = line.substring(index + 1).trim()
        require(name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) { "Invalid header name: $name" }
        builder.addHeader(name, value)
    }
    val payload = body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    when (method) {
        "GET" -> builder.get()
        "HEAD" -> builder.head()
        "POST", "PUT", "PATCH", "DELETE" -> builder.method(method, if (method == "DELETE" && body.isBlank()) null else payload)
        "OPTIONS" -> builder.method("OPTIONS", null)
        else -> error("Unsupported HTTP method")
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
private fun HistoryScreen(modifier: Modifier, history: androidx.compose.runtime.snapshots.SnapshotStateList<CapturedExchange>) {
    var selected by remember { mutableStateOf<CapturedExchange?>(null) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("HTTP history", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { history.clear(); selected = null }, enabled = history.isNotEmpty()) { Text("Clear") }
        }
        Text("In-memory history for this app session.", color = MaterialTheme.colorScheme.secondary)
        if (history.isEmpty()) Text("No requests yet. Send a request from Repeater.")
        LazyColumn {
            items(history) { item ->
                ListItem(
                    headlineContent = { Text("${item.method}  •  ${item.status}") },
                    supportingContent = { Text(item.url, maxLines = 2) },
                    trailingContent = { Text("${item.durationMs} ms") },
                    modifier = Modifier.clickable { selected = item }
                )
                HorizontalDivider()
            }
        }
    }
    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${item.method} • ${item.status}") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(item.url)
                Text("Response headers", style = MaterialTheme.typography.titleSmall)
                Text(item.responseHeaders)
                Text("Response body", style = MaterialTheme.typography.titleSmall)
                Text(item.responseBody)
            } },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun DecoderScreen(modifier: Modifier) {
    var mode by remember { mutableStateOf("URL") }
    var direction by remember { mutableStateOf("Encode") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Decoder / Encoder", style = MaterialTheme.typography.headlineSmall)
        Text("Local conversion only. No data is uploaded.", color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("URL", "Base64", "HTML").forEach { item ->
                FilterChip(selected = mode == item, onClick = { mode = item; output = ""; error = null }, label = { Text(item) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Encode", "Decode").forEach { item ->
                FilterChip(selected = direction == item, onClick = { direction = item; output = ""; error = null }, label = { Text(item) })
            }
        }
        OutlinedTextField(input, { input = it }, label = { Text("Input") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
        Button(onClick = {
            try {
                error = null
                output = when (mode to direction) {
                    "URL" to "Encode" -> URLEncoder.encode(input, StandardCharsets.UTF_8.name())
                    "URL" to "Decode" -> URLDecoder.decode(input, StandardCharsets.UTF_8.name())
                    "Base64" to "Encode" -> Base64.encodeToString(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                    "Base64" to "Decode" -> String(Base64.decode(input.trim(), Base64.DEFAULT), StandardCharsets.UTF_8)
                    "HTML" to "Encode" -> android.text.TextUtils.htmlEncode(input)
                    "HTML" to "Decode" -> Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString()
                    else -> input
                }
            } catch (t: Throwable) {
                output = ""; error = t.message ?: "Conversion failed"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("$direction $mode") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(output, {}, label = { Text("Output") }, modifier = Modifier.fillMaxWidth(), minLines = 5, readOnly = true)
        TextButton(onClick = { input = output; output = ""; error = null }, enabled = output.isNotEmpty()) { Text("Use output as input") }
    }
}

@Composable
private fun InfoScreen(modifier: Modifier, title: String, message: String) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Text("Not implemented in this build", color = MaterialTheme.colorScheme.secondary)
    }
}
