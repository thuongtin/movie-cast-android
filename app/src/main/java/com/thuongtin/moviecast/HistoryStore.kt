package com.thuongtin.moviecast

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val url: String,
    val title: String,
    val visitedAt: Long
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
            .apply()
        return emptyList()
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

    private fun isHistoryUrl(url: String): Boolean {
        return url.startsWith("http://", true) || url.startsWith("https://", true)
    }

    private companion object {
        const val KEY_LAST_PAGE_URL = "lastPageUrl"
        const val KEY_HISTORY = "history"
        const val KEY_APP_MUTED = "appMuted"
        const val HISTORY_LIMIT = 40
    }
}
