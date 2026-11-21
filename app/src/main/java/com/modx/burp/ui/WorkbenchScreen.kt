package com.modx.burp.ui

import android.text.Html
import android.text.TextUtils
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.modx.burp.core.HttpEngine
import com.modx.burp.core.HttpExchange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val tabs = listOf("Repeater", "History", "Decoder", "About")
private val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchScreen() {
    var tab by rememberSaveable { mutableStateOf("Repeater") }
    val history = remember { mutableStateListOf<HttpExchange>() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("MODx BURP", fontWeight = FontWeight.Black); Text("HTTP WORKBENCH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(item) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            "Repeater" -> RepeaterScreen(Modifier.padding(padding), history)
            "History" -> HistoryScreen(Modifier.padding(padding), history)
            "Decoder" -> DecoderScreen(Modifier.padding(padding))
            else -> AboutScreen(Modifier.padding(padding))
        }
    }
}

@Composable
private fun RepeaterScreen(modifier: Modifier, history: androidx.compose.runtime.snapshots.SnapshotStateList<HttpExchange>) {
    var method by rememberSaveable { mutableStateOf("GET") }
    var url by rememberSaveable { mutableStateOf("https://httpbin.org/get") }
    var headers by rememberSaveable { mutableStateOf("User-Agent: MODx-BURP/1.0\nAccept: */*") }
    var body by rememberSaveable { mutableStateOf("") }
    var contentType by rememberSaveable { mutableStateOf("application/json; charset=utf-8") }
    var exchange by remember { mutableStateOf<HttpExchange?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var methodMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Request builder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Manual HTTP requests • use only on systems you own or have permission to test.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { methodMenu = true }) { Text(method) }
                DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                    methods.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { method = value; methodMenu = false }) }
                }
            }
            OutlinedTextField(url, { url = it }, modifier = Modifier.weight(1f), label = { Text("URL") }, singleLine = true)
        }
        OutlinedTextField(headers, { headers = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Headers (one per line)") }, minLines = 3)
        OutlinedTextField(contentType, { contentType = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Body Content-Type") }, singleLine = true)
        OutlinedTextField(body, { body = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Request body") }, minLines = 4)
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && url.isNotBlank(),
            onClick = {
                loading = true; error = null; exchange = null
                scope.launch {
                    try {
                        val response = withContext(Dispatchers.IO) { HttpEngine.execute(method, url, headers, body, contentType) }
                        exchange = response
                        history.add(0, response)
                        while (history.size > 100) history.removeAt(history.lastIndex)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "Request failed. URL aur network connection check karein."
                    } finally { loading = false }
                }
            }
        ) { Text(if (loading) "Sending request…" else "SEND REQUEST") }

        error?.let { MessageCard("Request failed", it, true) }
        exchange?.let { response ->
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Response", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AssistChip(onClick = {}, label = { Text("${response.statusCode} • ${response.durationMs} ms") })
            }
            if (response.statusCode >= 400) Text("Server returned an HTTP error status. The request itself completed.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            ResponseSection("Response headers", response.responseHeaders.ifBlank { "(no headers)" })
            ResponseSection("Response body", response.responseBody.ifBlank { "(empty response)" })
            if (response.truncated) Text("Response 1 MB par truncate ki gayi hai taaki app ki memory safe rahe.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
            PassiveChecks(response)
        }
    }
}

@Composable
private fun ResponseSection(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PassiveChecks(response: HttpExchange) {
    val headerNames = response.responseHeaders.lineSequence().map { it.substringBefore(':').trim().lowercase() }.toSet()
    val checks = listOf("content-security-policy", "x-content-type-options", "referrer-policy", "permissions-policy")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Passive response checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Basic observations only; not a complete security audit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        checks.forEach { name ->
            val present = name in headerNames
            ListItem(
                headlineContent = { Text(name) },
                supportingContent = { Text(if (present) "Header observed in response." else "Header not observed; review whether it applies.") },
                leadingContent = { Text(if (present) "✓" else "•", color = if (present) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) }
            )
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, history: androidx.compose.runtime.snapshots.SnapshotStateList<HttpExchange>) {
    var selected by remember { mutableStateOf<HttpExchange?>(null) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Request history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { history.clear(); selected = null }, enabled = history.isNotEmpty()) { Text("CLEAR") }
        }
        Text("Last 100 requests in this app session.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No requests yet. Send one from Repeater.") }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(history, key = { it.id }) { item ->
                    ListItem(
                        modifier = Modifier.clickable { selected = item },
                        headlineContent = { Text("${item.method}  •  ${item.statusCode}", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(item.url, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Text("${item.durationMs} ms", style = MaterialTheme.typography.labelSmall) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${item.method} • ${item.statusCode}") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.url)
                    Text("Response headers", fontWeight = FontWeight.Bold)
                    SelectionContainer { Text(item.responseHeaders, style = MaterialTheme.typography.bodySmall) }
                    Text("Response body", fontWeight = FontWeight.Bold)
                    SelectionContainer { Text(item.responseBody, style = MaterialTheme.typography.bodySmall) }
                    if (item.truncated) Text("Body truncated at 1 MB.")
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("CLOSE") } }
        )
    }
}

@Composable
private fun DecoderScreen(modifier: Modifier) {
    var mode by rememberSaveable { mutableStateOf("URL") }
    var direction by rememberSaveable { mutableStateOf("Encode") }
    var input by rememberSaveable { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Encoder / Decoder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("All conversions happen locally on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("URL", "Base64", "HTML").forEach { item -> FilterChip(selected = mode == item, onClick = { mode = item; output = ""; error = null }, label = { Text(item) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Encode", "Decode").forEach { item -> FilterChip(selected = direction == item, onClick = { direction = item; output = ""; error = null }, label = { Text(item) }) }
        }
        OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), label = { Text("Input") }, minLines = 5)
        Button(onClick = {
            try {
                error = null
                output = when (mode to direction) {
                    "URL" to "Encode" -> URLEncoder.encode(input, "UTF-8")
                    "URL" to "Decode" -> URLDecoder.decode(input, "UTF-8")
                    "Base64" to "Encode" -> Base64.encodeToString(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                    "Base64" to "Decode" -> String(Base64.decode(input.trim(), Base64.DEFAULT), StandardCharsets.UTF_8)
                    "HTML" to "Encode" -> TextUtils.htmlEncode(input)
                    "HTML" to "Decode" -> Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString()
                    else -> input
                }
            } catch (failure: Exception) { output = ""; error = failure.message ?: "Conversion failed" }
        }, Modifier.fillMaxWidth()) { Text("$direction $mode") }
        error?.let { MessageCard("Conversion error", it, true) }
        OutlinedTextField(output, {}, Modifier.fillMaxWidth(), label = { Text("Output") }, minLines = 5, readOnly = true)
        OutlinedButton(onClick = { input = output; output = ""; error = null }, enabled = output.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("USE OUTPUT AS INPUT") }
    }
}

@Composable
private fun AboutScreen(modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MODx BURP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("Native Android HTTP workbench", color = MaterialTheme.colorScheme.primary)
        Text("Available now", fontWeight = FontWeight.Bold)
        listOf("Manual HTTP request builder", "Response headers and body viewer", "Session request history (up to 100)", "URL, Base64 and HTML conversion", "Basic passive response-header observations", "Response-size and network time limits").forEach { Text("✓  $it") }
        HorizontalDivider()
        Text("Not included in this build", fontWeight = FontWeight.Bold)
        listOf("Device-wide VPN traffic capture", "HTTPS certificate interception", "WebSocket frame inspection").forEach { Text("•  $it") }
        Text("Use only with systems you own or have permission to test.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun MessageCard(title: String, message: String, isError: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
