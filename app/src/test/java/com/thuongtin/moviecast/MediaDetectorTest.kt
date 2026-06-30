package com.thuongtin.moviecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDetectorTest {
    @Test
    fun detectsHlsMediaUrl() {
        val candidate = MediaDetector.detect(
            rawUrl = "https://cdn.example.com/movie/master.m3u8?token=abc",
            source = "Network",
            pageUrl = "https://example.com/tap-1"
        )

        assertNotNull(candidate)
        assertEquals("application/x-mpegurl", candidate?.contentType)
        assertEquals("HLS", MediaDetector.mediaTypeLabel(candidate!!.contentType, candidate.url))
    }

    @Test
    fun detectsMp4MediaUrl() {
        val candidate = MediaDetector.detect(
            rawUrl = "https://example.com/video.mp4",
            source = "Manual",
            pageUrl = "about:blank"
        )

        assertNotNull(candidate)
        assertEquals("video/mp4", candidate?.contentType)
    }

    @Test
    fun rejectsAdMediaUrl() {
        val candidate = MediaDetector.detect(
            rawUrl = "https://ads.example.com/banner/video.mp4",
            source = "Network",
            pageUrl = "https://example.com/tap-1"
        )

        assertNull(candidate)
    }

    @Test
    fun extractsCurrentEpisodeFromStructuredPageData() {
        val script = """
            {"server":"Vietsub #1","name":"Tập 1","type":"m3u8","link":"https:\/\/stream.example.com\/movie\/ep1.m3u8"}
            {"server":"Vietsub #1","name":"Tập 2","type":"m3u8","link":"https:\/\/stream.example.com\/movie\/ep2.m3u8"}
        """.trimIndent()

        val candidates = MediaDetector.structuredEpisodeCandidates(
            text = script,
            pageUrl = "https://motchille.ac/phim/example/tap-1",
            title = "Xem phim Example Tập 1 Vietsub"
        )

        assertEquals(1, candidates.size)
        assertTrue(candidates.first().url.endsWith("/ep1.m3u8"))
        assertEquals("episode", candidates.first().candidateKind)
        assertEquals("high", candidates.first().confidence)
    }

    @Test
    fun normalizesPlainHostAndSearchText() {
        assertEquals("https://motchille.ac", MediaDetector.normalizeNavigationUrl("motchille.ac"))
        assertEquals(
            "https://www.google.com/search?q=avatar+vietsub",
            MediaDetector.normalizeNavigationUrl("avatar vietsub")
        )
    }

    @Test
    fun detectsWebVttSubtitle() {
        val subtitle = MediaDetector.detectSubtitle(
            rawUrl = "/subs/vi.vtt",
            source = "DOM",
            pageUrl = "https://example.com/watch/tap-1",
            label = "Tiếng Việt",
            language = "vi",
            isDefault = true
        )

        assertNotNull(subtitle)
        assertEquals("https://example.com/subs/vi.vtt", subtitle?.url)
        assertEquals("text/vtt", subtitle?.contentType)
        assertEquals("webvtt", subtitle?.format)
        assertTrue(subtitle!!.castSupported)
        assertTrue(subtitle.isDefault)
    }

    @Test
    fun marksSrtSubtitleForConversion() {
        val subtitle = MediaDetector.detectSubtitle(
            rawUrl = "https://cdn.example.com/subs/movie.srt",
            source = "Network",
            pageUrl = "https://example.com/watch/tap-1"
        )

        assertNotNull(subtitle)
        assertEquals("application/x-subrip", subtitle?.contentType)
        assertEquals("srt", subtitle?.format)
        assertTrue(subtitle!!.castSupported)
        assertTrue(subtitle.requiresConversion)
    }

    @Test
    fun extractsLooseSubtitleFromPageData() {
        val script = """{"subtitle":"https:\/\/cdn.example.com\/movie\/vi.vtt?token=abc"}"""

        val subtitles = MediaDetector.looseSubtitleCandidates(script, "https://example.com/watch")

        assertEquals(1, subtitles.size)
        assertEquals("text/vtt", subtitles.first().contentType)
        assertTrue(subtitles.first().url.contains("vi.vtt"))
    }

    @Test
    fun extractsHlsSubtitleGroup() {
        val manifest = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Tiếng Việt",LANGUAGE="vi",DEFAULT=YES,URI="subs/vi.m3u8"
        """.trimIndent()

        val subtitles = MediaDetector.hlsSubtitleCandidates(manifest, "https://cdn.example.com/master.m3u8")

        assertEquals(1, subtitles.size)
        assertEquals("https://cdn.example.com/subs/vi.m3u8", subtitles.first().url)
        assertEquals("hls-vtt", subtitles.first().format)
        assertTrue(subtitles.first().isDefault)
    }
}
