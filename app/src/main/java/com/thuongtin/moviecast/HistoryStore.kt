package com.thuongtin.moviecast

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long
)

data class PlaybackPositionEntry(
    val url: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

class HistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("movie_cast_android_state", Context.MODE_PRIVATE)

    fun lastPageUrl(): String {
        return preferences.getString(KEY_LAST_PAGE_URL, "") ?: ""
    }

    fun history(): List<HistoryEntry> {
        val raw = preferences.getString(KEY_HISTORY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                HistoryEntry(
                    url = item.optString("url"),
                    title = item.optString("title"),
                    visitedAt = item.optLong("visitedAt")
                )
            }.filter { isHistoryUrl(it.url) }.take(HISTORY_LIMIT)
        }.getOrDefault(emptyList())
    }

    fun appMuted(): Boolean {
        return preferences.getBoolean(KEY_APP_MUTED, false)
    }

    fun playbackPositionFor(url: String): PlaybackPositionEntry? {
        if (!isHistoryUrl(url)) return null
        return playbackPositions()
            .firstOrNull { it.url == url }
            ?.takeIf { PlaybackResumePolicy.isResumable(it.positionMs, it.durationMs) }
    }

    fun rememberPlaybackPosition(
        url: String,
        title: String,
        positionMs: Long,
        durationMs: Long
    ): PlaybackPositionEntry? {
        if (!isHistoryUrl(url)) return null
        if (!PlaybackResumePolicy.isResumable(positionMs, durationMs)) return null
        val entry = PlaybackPositionEntry(
            url = url,
            title = title.ifBlank { MediaDetector.hostForUrl(url) },
            positionMs = PlaybackResumePolicy.startPosition(positionMs, durationMs),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAt = System.currentTimeMillis()
        )
        val next = listOf(entry) + playbackPositions().filterNot { it.url == url }
        savePlaybackPositions(next.take(PLAYBACK_POSITION_LIMIT))
        return entry
    }

    fun setAppMuted(muted: Boolean) {
        preferences.edit()
            .putBoolean(KEY_APP_MUTED, muted)
            .apply()
    }

    fun rememberPage(url: String, title: String): List<HistoryEntry> {
        if (!isHistoryUrl(url)) return history()
        val existing = history()
        val entry = HistoryEntry(
            url = url,
            title = title.ifBlank { MediaDetector.hostForUrl(url) },
            visitedAt = System.currentTimeMillis()
        )
        val next = listOf(entry) + existing.filterNot { it.url == url }
        saveState(url, next.take(HISTORY_LIMIT))
        return next.take(HISTORY_LIMIT)
    }

    fun clear(): List<HistoryEntry> {
        preferences.edit()
            .remove(KEY_LAST_PAGE_URL)
            .remove(KEY_HISTORY)
            .remove(KEY_PLAYBACK_POSITIONS)
            .apply()
        return emptyList()
    }

    private fun playbackPositions(): List<PlaybackPositionEntry> {
        val raw = preferences.getString(KEY_PLAYBACK_POSITIONS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                PlaybackPositionEntry(
                    url = item.optString("url"),
                    title = item.optString("title"),
                    positionMs = item.optLong("positionMs"),
                    durationMs = item.optLong("durationMs"),
                    updatedAt = item.optLong("updatedAt")
                )
            }
                .filter { isHistoryUrl(it.url) }
                .filter { PlaybackResumePolicy.isResumable(it.positionMs, it.durationMs) }
                .take(PLAYBACK_POSITION_LIMIT)
        }.getOrDefault(emptyList())
    }

    private fun saveState(lastPageUrl: String, history: List<HistoryEntry>) {
        val array = JSONArray()
        history.forEach { item ->
            array.put(
                JSONObject()
                    .put("url", item.url)
                    .put("title", item.title)
                    .put("visitedAt", item.visitedAt)
            )
        }
        preferences.edit()
            .putString(KEY_LAST_PAGE_URL, lastPageUrl)
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    private fun savePlaybackPositions(positions: List<PlaybackPositionEntry>) {
        val array = JSONArray()
        positions.forEach { item ->
            array.put(
                JSONObject()
                    .put("url", item.url)
                    .put("title", item.title)
                    .put("positionMs", item.positionMs)
                    .put("durationMs", item.durationMs)
                    .put("updatedAt", item.updatedAt)
            )
        }
        preferences.edit()
            .putString(KEY_PLAYBACK_POSITIONS, array.toString())
            .apply()
    }

    private fun isHistoryUrl(url: String): Boolean {
        return url.startsWith("http://", true) || url.startsWith("https://", true)
    }

    private companion object {
        const val KEY_LAST_PAGE_URL = "lastPageUrl"
        const val KEY_HISTORY = "history"
        const val KEY_APP_MUTED = "appMuted"
        const val KEY_PLAYBACK_POSITIONS = "playbackPositions"
        const val HISTORY_LIMIT = 40
        const val PLAYBACK_POSITION_LIMIT = 100
    }
}
