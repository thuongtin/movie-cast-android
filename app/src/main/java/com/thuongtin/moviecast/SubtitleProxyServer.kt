package com.thuongtin.moviecast

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class PreparedSubtitle(
    val url: String,
    val contentType: String,
    val label: String,
    val language: String,
    val trackId: Long = 1L
)

class SubtitlePrepareException(message: String) : Exception(message)

object SubtitleProxyServer {
    private const val MAX_SUBTITLE_BYTES = 2 * 1024 * 1024
    private val subtitles = ConcurrentHashMap<String, String>()
    private val acceptExecutor = Executors.newSingleThreadExecutor()
    private val clientExecutor = Executors.newCachedThreadPool()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var publicBaseUrl: String = ""

    fun prepare(subtitle: SubtitleCandidate): PreparedSubtitle {
        if (!subtitle.requiresConversion) {
            return PreparedSubtitle(
                url = subtitle.url,
                contentType = subtitle.contentType,
                label = MediaDetector.subtitleDisplayName(subtitle),
                language = subtitle.language
            )
        }

        val vtt = convertSrtToWebVtt(fetchSubtitleText(subtitle.url))
        val publicUrl = publish(vtt)
        return PreparedSubtitle(
            url = publicUrl,
            contentType = "text/vtt",
            label = subtitle.label.ifBlank { "SRT đã convert" },
            language = subtitle.language
        )
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        publicBaseUrl = ""
        subtitles.clear()
    }

    private fun publish(webVtt: String): String {
        val baseUrl = ensureStarted()
        val id = sha256(webVtt).take(24)
        subtitles[id] = webVtt
        return "$baseUrl/subtitles/$id.vtt"
    }

    @Synchronized
    private fun ensureStarted(): String {
        serverSocket?.takeIf { !it.isClosed }?.let {
            if (publicBaseUrl.isNotBlank()) return publicBaseUrl
        }

        val address = localLanAddress() ?: throw SubtitlePrepareException("Không tìm thấy IP LAN của thiết bị để TV tải phụ đề.")
        val socket = ServerSocket(0, 16, InetAddress.getByName("0.0.0.0"))
        serverSocket = socket
        publicBaseUrl = "http://$address:${socket.localPort}"
        acceptExecutor.execute {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    clientExecutor.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (!socket.isClosed) continue
                }
            }
        }
        return publicBaseUrl
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII))
            val requestLine = reader.readLine().orEmpty()
            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            val id = path.substringAfter("/subtitles/", "").substringBefore(".vtt")
            val body = subtitles[id]
            val writer = PrintWriter(client.getOutputStream(), false)
            if (body == null) {
                writer.print("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                writer.flush()
                return
            }
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            writer.print(
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/vtt; charset=utf-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
            )
            writer.flush()
            client.getOutputStream().write(bytes)
            client.getOutputStream().flush()
        }
    }

    private fun fetchSubtitleText(rawUrl: String): String {
        val connection = (URL(rawUrl).openConnection() as? HttpURLConnection)
            ?: throw SubtitlePrepareException("Không tải được phụ đề SRT.")
        return try {
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "MovieCast/1.0")
            val length = connection.contentLengthLong
            if (length > MAX_SUBTITLE_BYTES) {
                throw SubtitlePrepareException("File phụ đề quá lớn để convert nhanh.")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw SubtitlePrepareException("Không tải được SRT công khai, HTTP $code.")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_SUBTITLE_BYTES) {
                throw SubtitlePrepareException("File phụ đề quá lớn để convert nhanh.")
            }
            val charset = connection.contentType
                ?.substringAfter("charset=", "")
                ?.substringBefore(";")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: Charsets.UTF_8
            bytes.toString(charset)
        } catch (error: SubtitlePrepareException) {
            throw error
        } catch (_: Exception) {
            throw SubtitlePrepareException("Không tải được SRT công khai, có thể URL cần cookie hoặc header riêng.")
        } finally {
            connection.disconnect()
        }
    }

    private fun convertSrtToWebVtt(raw: String): String {
        val normalized = raw.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("""\n{2,}"""))
        val cues = blocks.mapNotNull { block ->
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.toMutableList()
            if (lines.isEmpty()) return@mapNotNull null
            if (lines.first().all { it.isDigit() }) lines.removeAt(0)
            val timeIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeIndex < 0) return@mapNotNull null
            val timeLine = lines[timeIndex].replace(',', '.')
            val text = lines.drop(timeIndex + 1).joinToString("\n")
            if (text.isBlank()) null else "$timeLine\n$text"
        }
        if (cues.isEmpty()) {
            throw SubtitlePrepareException("SRT không có cue hợp lệ để convert.")
        }
        return "WEBVTT\n\n${cues.joinToString("\n\n")}\n"
    }

    private fun localLanAddress(): String? {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.startsWith("169.254.") != true }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
