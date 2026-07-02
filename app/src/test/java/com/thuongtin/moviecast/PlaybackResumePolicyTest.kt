package com.thuongtin.moviecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResumePolicyTest {
    @Test
    fun ignoresPositionsNearStart() {
        assertFalse(PlaybackResumePolicy.isResumable(positionMs = 4_999L, durationMs = 3_600_000L))
        assertEquals(0L, PlaybackResumePolicy.startPosition(positionMs = 4_999L, durationMs = 3_600_000L))
    }

    @Test
    fun ignoresPositionsNearEnd() {
        assertFalse(PlaybackResumePolicy.isResumable(positionMs = 3_585_000L, durationMs = 3_600_000L))
        assertEquals(0L, PlaybackResumePolicy.startPosition(positionMs = 3_585_000L, durationMs = 3_600_000L))
    }

    @Test
    fun resumesMiddleOfMovie() {
        assertTrue(PlaybackResumePolicy.isResumable(positionMs = 425_000L, durationMs = 3_600_000L))
        assertEquals(425_000L, PlaybackResumePolicy.startPosition(positionMs = 425_000L, durationMs = 3_600_000L))
    }

    @Test
    fun supportsUnknownDuration() {
        assertTrue(PlaybackResumePolicy.isResumable(positionMs = 30_000L, durationMs = 0L))
        assertEquals(30_000L, PlaybackResumePolicy.startPosition(positionMs = 30_000L, durationMs = 0L))
    }
}
