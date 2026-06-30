package com.thuongtin.moviecast

import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.max

data class SubtitleCandidate(
    val url: String,
    val contentType: String,
    val source: String,
    val pageUrl: String = "",
    val label: String = "",
    val language: String = "",
    val format: String = "",
    val isDefault: Boolean = false,
    val castSupported: Boolean = true,
    val requiresConversion: Boolean = false,
    val unsupportedReason: String = "",
    val score: Int = 0,
    val seenAt: Long = System.currentTimeMillis()
)

data class MediaCandidate(
    val url: String,
    val contentType: String,
    val source: String,
    val pageUrl: String = "",
    val title: String = "",
    val candidateKind: String = "unknown",
    val server: String = "",
    val episodeName: String = "",
    val confidence: String = "",
    val reason: String = "",
    val poster: String = "",
    val subtitles: List<SubtitleCandidate> = emptyList(),
    val score: Int = 0,
    val seenAt: Long = System.currentTimeMillis()
)

object MediaDetector {
    private val mediaPattern = Regex("""\.(m3u8|mpd|mp4|m4v|webm|mov|mkv|avi|ogv)(\?|#|$)""", RegexOption.IGNORE_CASE)
    private val mediaUrlPattern = Regex("""https?:\\?/\\?/[^\s"'<>\\]+?\.(?:m3u8|mpd|mp4|m4v|webm|mov|mkv|avi|ogv)(?:[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)
    private val masterLinkPattern = Regex("""(?:https?:\\?/\\?/[^\s"'<>\\]+)?\\?/player\\?/master\\?/[A-Za-z0-9_/-]+""", RegexOption.IGNORE_CASE)
    private val subtitlePattern = Regex("""\.(vtt|webvtt|srt|ttml|dfxp)(\?|#|$)""", RegexOption.IGNORE_CASE)
    private val subtitleUrlPattern = Regex("""https?:\\?/\\?/[^\s"'<>\\]+?\.(?:vtt|webvtt|srt|ttml|dfxp)(?:[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)
    private val adPattern = Regex("""adcenter|/(ads?|adserver|banner|banners|popup|promos?)/|(^|[.-])ads?([.-]|$)|casino|bet|qq88|net88|ok9|gem88|debet|sunwin""", RegexOption.IGNORE_CASE)

    fun normalizeNavigationUrl(rawValue: String): String {
        val value = rawValue.trim()
        if (value.isEmpty()) return ""
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) return value
        if (value.contains(".") && !value.contains(" ")) return "https://$value"
        return "https://www.google.com/search?q=${encode(value)}"
    }

    fun detect(
        rawUrl: String?,
        source: String,
        pageUrl: String = "",
        title: String = "",
        score: Int? = null,
        candidateKind: String = "unknown",
        reason: String = "",
        poster: String = "",
        subtitles: List<SubtitleCandidate> = emptyList()
    ): MediaCandidate? {
        val normalized = absoluteUrl(rawUrl ?: return null, pageUrl) ?: return null
        if (!isSupportedMediaUrl(normalized)) return null
        val defaultScore = when (source) {
            "DOM" -> 65
            "Page data" -> 52
            "Network" -> 38
            else -> 35
        }
        return MediaCandidate(
            url = normalized,
            contentType = contentTypeForUrl(normalized),
            source = source,
            pageUrl = pageUrl,
            title = title,
            candidateKind = candidateKind,
            confidence = if (candidateKind == "episode") "high" else "",
            reason = reason,
            poster = poster,
            subtitles = subtitles.distinctBy { it.url },
            score = score ?: defaultScore
        )
    }

    fun mergeCandidates(existing: MediaCandidate?, incoming: MediaCandidate): MediaCandidate {
        if (existing == null) return incoming
        return incoming.copy(
            title = incoming.title.ifBlank { existing.title },
            pageUrl = incoming.pageUrl.ifBlank { existing.pageUrl },
            subtitles = mergeSubtitles(existing.subtitles, incoming.subtitles),
            score = max(existing.score, incoming.score),
            seenAt = System.currentTimeMillis()
        )
    }

    fun contentTypeForUrl(url: String): String {
        val clean = url.substringBefore("?").lowercase()
        return when {
            clean.endsWith(".m3u8") || clean.contains("/player/master/") -> "application/x-mpegurl"
            clean.endsWith(".mpd") -> "application/dash+xml"
            clean.endsWith(".mp4") || clean.endsWith(".m4v") -> "video/mp4"
            clean.endsWith(".webm") -> "video/webm"
            clean.endsWith(".mov") -> "video/quicktime"
            clean.endsWith(".ogv") -> "video/ogg"
            else -> "video/mp4"
        }
    }

    fun mediaTypeLabel(contentType: String, url: String): String {
        val value = "$contentType $url".lowercase()
        return when {
            value.contains("mpegurl") || value.contains(".m3u8") || value.contains("/player/master/") -> "HLS"
            value.contains("dash") || value.contains(".mpd") -> "DASH"
            value.contains("webm") -> "WebM"
            value.contains("mp4") -> "MP4"
            else -> "Video"
        }
    }

    fun detectSubtitle(
        rawUrl: String?,
        source: String,
        pageUrl: String = "",
        label: String = "",
        language: String = "",
        isDefault: Boolean = false,
        score: Int? = null
    ): SubtitleCandidate? {
        val normalized = absoluteResourceUrl(rawUrl ?: return null, pageUrl) ?: return null
        if (!isSubtitleUrl(normalized)) return null
        val format = subtitleFormatForUrl(normalized)
        val requiresConversion = format == "srt"
        val supported = format == "webvtt" || format == "ttml" || requiresConversion
        val defaultScore = when (source) {
            "DOM" -> 80
            "Page data" -> 62
            "Network" -> 50
            "Fetch", "XHR" -> 48
            else -> 35
        } + if (isDefault) 8 else 0
        return SubtitleCandidate(
            url = normalized,
            contentType = subtitleContentTypeForUrl(normalized),
            source = source,
            pageUrl = pageUrl,
            label = label,
            language = language,
            format = format,
            isDefault = isDefault,
            castSupported = supported,
            requiresConversion = requiresConversion,
            unsupportedReason = if (supported) "" else "Định dạng phụ đề này chưa được Chromecast hỗ trợ.",
            score = score ?: defaultScore
        )
    }

    fun looseSubtitleCandidates(text: String, pageUrl: String): List<SubtitleCandidate> {
        val decoded = decodeJsonish(text)
        return subtitleUrlPattern.findAll(decoded).take(40).mapNotNull { match ->
            detectSubtitle(
                rawUrl = match.value,
                source = "Page data",
                pageUrl = pageUrl,
                score = 62
            )
        }.toList()
    }

    fun hlsSubtitleCandidates(manifestText: String, manifestUrl: String): List<SubtitleCandidate> {
        return manifestText.lineSequence()
            .filter { it.startsWith("#EXT-X-MEDIA", ignoreCase = true) && it.contains("TYPE=SUBTITLES", ignoreCase = true) }
            .take(24)
            .mapNotNull { line ->
                val attrs = parseHlsAttributes(line)
                val uri = attrs["URI"] ?: return@mapNotNull null
                val resolved = absoluteResourceUrl(uri, manifestUrl) ?: return@mapNotNull null
                val label = attrs["NAME"].orEmpty()
                val language = attrs["LANGUAGE"].orEmpty()
                val isDefault = attrs["DEFAULT"].equals("YES", ignoreCase = true)
                detectSubtitle(
                    rawUrl = resolved,
                    source = "HLS",
                    pageUrl = manifestUrl,
                    label = label,
                    language = language,
                    isDefault = isDefault,
                    score = 78
                ) ?: if (resolved.substringBefore("?").lowercase().endsWith(".m3u8")) {
                    SubtitleCandidate(
                        url = resolved,
                        contentType = "application/x-mpegurl",
                        source = "HLS",
                        pageUrl = manifestUrl,
                        label = label,
                        language = language,
                        format = "hls-vtt",
                        isDefault = isDefault,
                        castSupported = true,
                        score = 78
                    )
                } else {
                    null
                }
            }
            .toList()
    }

    fun subtitleContentTypeForUrl(url: String): String {
        return when (subtitleFormatForUrl(url)) {
            "webvtt" -> "text/vtt"
            "ttml" -> "application/ttml+xml"
            "srt" -> "application/x-subrip"
            else -> "text/vtt"
        }
    }

    fun subtitleDisplayName(subtitle: SubtitleCandidate): String {
        val base = subtitle.label.ifBlank {
            when {
                subtitle.language.isNotBlank() -> subtitle.language.uppercase()
                subtitle.format == "srt" -> "SRT"
                subtitle.format == "ttml" -> "TTML"
                subtitle.format == "hls-vtt" -> "HLS Sub"
                else -> "WebVTT"
            }
        }
        return if (subtitle.requiresConversion) "$base (convert)" else base
    }

    fun hostForUrl(rawUrl: String): String {
        return try {
            URL(rawUrl).host
        } catch (_: Exception) {
            "unknown"
        }
    }

    fun safeTitle(candidate: MediaCandidate): String {
        if (candidate.candidateKind == "episode" || candidate.confidence == "high") return "Link tập phim đề xuất"
        if (candidate.source == "Manual") return "Link nhập thủ công"
        if (candidate.title.isNotBlank()) return candidate.title
        return "${mediaTypeLabel(candidate.contentType, candidate.url)} từ ${hostForUrl(candidate.url)}"
    }

    fun isVisibleCandidate(candidate: MediaCandidate): Boolean {
        if (isLikelyAdMediaUrl(candidate.url)) return false
        return candidate.source == "Manual" ||
            candidate.candidateKind == "episode" ||
            candidate.confidence == "high" ||
            candidate.source in setOf("DOM", "Network", "Fetch", "XHR", "Page data")
    }

    fun rank(candidate: MediaCandidate): Int {
        val base = when {
            candidate.source == "Manual" -> 320
            candidate.candidateKind == "episode" || candidate.confidence == "high" -> 260
            candidate.source in setOf("DOM", "Network", "Fetch", "XHR", "Page data") -> 80
            else -> 20
        }
        return base + candidate.score
    }

    fun isSupportedMediaUrl(url: String): Boolean {
        val clean = decodeJsonish(url).substringBefore("?").lowercase()
        return !isLikelyAdMediaUrl(url) && (mediaPattern.containsMatchIn(url) || clean.contains("/player/master/"))
    }

    fun isSubtitleUrl(url: String): Boolean {
        return !isLikelyAdMediaUrl(url) && subtitlePattern.containsMatchIn(decodeJsonish(url))
    }

    fun looseMediaCandidates(text: String, pageUrl: String, title: String): List<MediaCandidate> {
        val decoded = decodeJsonish(text)
        val looseMatches = (mediaUrlPattern.findAll(decoded) + masterLinkPattern.findAll(decoded)).take(30)
        return looseMatches.mapNotNull { match ->
            detect(
                rawUrl = match.value,
                source = "Page data",
                pageUrl = pageUrl,
                title = title,
                score = 52,
                candidateKind = "loose",
                reason = "Found in page script"
            )
        }.toList()
    }

    fun structuredEpisodeCandidates(text: String, pageUrl: String, title: String): List<MediaCandidate> {
        val decoded = decodeJsonish(text)
        val currentEpisode = currentEpisodeNumber(pageUrl)
        val wantsVietsub = title.lowercase().contains("vietsub")
        val wantsDub = title.lowercase().contains("lồng tiếng") ||
            title.lowercase().contains("long tieng") ||
            title.lowercase().contains("thuyết minh") ||
            title.lowercase().contains("thuyet minh")
        val pattern = Regex(""""server":"([^"]+)"[\s\S]{0,260}?"name":"([^"]+)"[\s\S]{0,260}?"type":"([^"]+)"[\s\S]{0,260}?"link":"([^"]+)"""")

        return pattern.findAll(decoded).take(24).mapNotNull { match ->
            val server = match.groupValues[1]
            val name = match.groupValues[2]
            val type = match.groupValues[3]
            val link = match.groupValues[4]
            val episode = episodeNumberFromName(name)
            val matchesEpisode = currentEpisode == null || episode == currentEpisode
            if (!matchesEpisode || !type.contains(Regex("m3u8|embed", RegexOption.IGNORE_CASE))) {
                return@mapNotNull null
            }
            val serverText = server.lowercase()
            val languageScore =
                (if (wantsVietsub && serverText.contains("vietsub")) 36 else 0) +
                    (if (wantsVietsub && serverText.contains("lồng tiếng")) -36 else 0) +
                    (if (wantsDub && (serverText.contains("lồng tiếng") || serverText.contains("thuyết minh"))) 36 else 0)
            val score = 120 + (if (episode == currentEpisode) 40 else 0) + (if (type.contains("m3u8", true)) 12 else 0) + languageScore
            detect(
                rawUrl = link,
                source = "Page data",
                pageUrl = pageUrl,
                title = "$title - $server - $name",
                score = score,
                candidateKind = "episode",
                reason = "Current episode data"
            )?.copy(server = server, episodeName = name, confidence = "high")
        }.toList()
    }

    private fun isLikelyAdMediaUrl(rawUrl: String): Boolean {
        return try {
            val parsed = URL(rawUrl)
            adPattern.containsMatchIn("${parsed.host.lowercase()} ${parsed.path.lowercase()}")
        } catch (_: Exception) {
            false
        }
    }

    private fun absoluteUrl(value: String, baseUrl: String): String? {
        val parsed = absoluteResourceUrl(value, baseUrl)?.let { URL(it) } ?: return null
        return try {
            val nested = nestedMediaUrl(parsed)
            if (nested != null && isSupportedMediaUrl(nested)) nested else parsed.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun absoluteResourceUrl(value: String, baseUrl: String): String? {
        val cleaned = decodeJsonish(value).trim()
        if (cleaned.isEmpty() || cleaned.startsWith("blob:", true)) return null
        return try {
            val parsed = when {
                cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true) -> URL(cleaned)
                cleaned.startsWith("//") -> URL("https:$cleaned")
                baseUrl.isNotBlank() -> URL(URL(baseUrl), cleaned)
                else -> URL(cleaned)
            }
            parsed.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun nestedMediaUrl(parsed: URL): String? {
        val query = parsed.query ?: return null
        return query.split("&").firstNotNullOfOrNull { part ->
            val raw = part.substringAfter("url=", missingDelimiterValue = "")
            if (raw.isBlank()) null else URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        }
    }

    private fun decodeJsonish(value: String): String {
        return value
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("&amp;", "&")
            .trimEnd(')', ',', '.', ';')
    }

    private fun encode(value: String): String {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun currentEpisodeNumber(url: String): Int? {
        return Regex("""(?:tap|episode|ep)-?(\d+)""", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun episodeNumberFromName(name: String): Int? {
        return Regex("""(\d+)""").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun mergeSubtitles(existing: List<SubtitleCandidate>, incoming: List<SubtitleCandidate>): List<SubtitleCandidate> {
        return (existing + incoming)
            .groupBy { it.url }
            .map { (_, items) ->
                items.maxBy { it.score }.copy(seenAt = items.maxOf { item -> item.seenAt })
            }
            .sortedWith(compareByDescending<SubtitleCandidate> { it.isDefault }.thenByDescending { it.score })
    }

    private fun subtitleFormatForUrl(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#").lowercase()
        return when {
            clean.endsWith(".vtt") || clean.endsWith(".webvtt") -> "webvtt"
            clean.endsWith(".ttml") || clean.endsWith(".dfxp") -> "ttml"
            clean.endsWith(".srt") -> "srt"
            else -> ""
        }
    }

    private fun parseHlsAttributes(line: String): Map<String, String> {
        return Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""", RegexOption.IGNORE_CASE)
            .findAll(line)
            .associate { match ->
                val key = match.groupValues[1].uppercase()
                val value = match.groupValues[2].trim().trim('"')
                key to value
            }
    }
}
