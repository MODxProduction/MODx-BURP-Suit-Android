package com.modx.burp.core

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class HttpExchange(
    val id: Long,
    val method: String,
    val url: String,
    val statusCode: Int,
    val statusMessage: String,
    val durationMs: Long,
    val responseHeaders: String,
    val responseBody: String,
    val truncated: Boolean
)

object HttpEngine {
    private const val MAX_RESPONSE_BYTES = 1024 * 1024
    private val nextId = AtomicLong(1)
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun execute(method: String, rawUrl: String, rawHeaders: String, body: String, contentType: String): HttpExchange {
        val url = rawUrl.trim()
        val parsed = try { URI(url) } catch (_: Exception) { throw IllegalArgumentException("URL format sahi nahi hai.") }
        require(parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) {
            "Sirf HTTP aur HTTPS URLs allowed hain."
        }
        require(!parsed.host.isNullOrBlank()) { "URL mein valid hostname hona chahiye." }
        require(parsed.userInfo == null) { "URL ke andar username/password mat daalein; headers use karein." }

        val builder = Request.Builder().url(url)
        rawHeaders.lineSequence().forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            val split = line.indexOf(':')
            require(split > 0) { "Header line ${index + 1} galat hai. Format: Name: value" }
            val name = line.substring(0, split).trim()
            val value = line.substring(split + 1).trim()
            require(name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) { "Header name invalid: $name" }
            require(value.none { it == '\r' || it == '\n' }) { "Header value mein new line allowed nahi hai." }
            builder.addHeader(name, value)
        }

        val payload = body.toRequestBody((contentType.ifBlank { "text/plain; charset=utf-8" }).toMediaTypeOrNull())
        when (method.uppercase()) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST", "PUT", "PATCH" -> builder.method(method.uppercase(), payload)
            "DELETE" -> builder.delete(if (body.isBlank()) null else payload)
            "OPTIONS" -> builder.method("OPTIONS", null)
            else -> throw IllegalArgumentException("Unsupported HTTP method.")
        }

        val started = System.nanoTime()
        client.newCall(builder.build()).execute().use { response ->
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            val headers = response.headers.names().sorted().joinToString("\n") { name ->
                "$name: ${response.headers.values(name).joinToString(", ")}"
            }
            val output = ByteArrayOutputStream()
            val input = response.body?.byteStream()
            val buffer = ByteArray(8192)
            var total = 0
            var truncated = false
            if (input != null) {
                input.use { stream ->
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        val allowed = minOf(count, MAX_RESPONSE_BYTES - total)
                        if (allowed > 0) output.write(buffer, 0, allowed)
                        total += allowed
                        if (allowed < count || total >= MAX_RESPONSE_BYTES) {
                            truncated = true
                            break
                        }
                    }
                }
            }
            val charset = response.body?.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            val responseText = output.toByteArray().toString(charset)
            return HttpExchange(
                nextId.getAndIncrement(), method.uppercase(), url, response.code,
                response.message, elapsed, headers, responseText, truncated
            )
        }
    }
}
