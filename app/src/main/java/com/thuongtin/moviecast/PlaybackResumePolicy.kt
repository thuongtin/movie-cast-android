package com.thuongtin.moviecast

object PlaybackResumePolicy {
    private const val MIN_RESUME_POSITION_MS = 5_000L
    private const val END_MARGIN_MS = 20_000L

    fun isResumable(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs < MIN_RESUME_POSITION_MS) return false
        if (durationMs > 0L && positionMs > durationMs - END_MARGIN_MS) return false
        return true
    }

    fun startPosition(positionMs: Long, durationMs: Long): Long {
        if (!isResumable(positionMs, durationMs)) return 0L
        return if (durationMs > 0L) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)
    }
}
