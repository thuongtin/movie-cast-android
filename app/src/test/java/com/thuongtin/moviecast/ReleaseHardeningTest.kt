package com.thuongtin.moviecast

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseHardeningTest {
    @Test
    fun releaseManifestDoesNotBackUpBrowsingState() {
        val manifest = readProjectFile("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertFalse(manifest.contains("android:allowBackup=\"true\""))
    }

    @Test
    fun releaseNetworkSecurityRejectsCleartextButDebugKeepsItForTesting() {
        val releaseConfig = readProjectFile(
            "app/src/main/res/xml/network_security_config.xml",
            "src/main/res/xml/network_security_config.xml"
        )
        val debugConfig = readProjectFile(
            "app/src/debug/res/xml/network_security_config.xml",
            "src/debug/res/xml/network_security_config.xml"
        )

        assertTrue(releaseConfig.contains("cleartextTrafficPermitted=\"false\""))
        assertTrue(debugConfig.contains("cleartextTrafficPermitted=\"true\""))
    }

    @Test
    fun webViewMuteDoesNotRewriteThirdPartyHtml() {
        val source = readProjectFile(
            "app/src/main/java/com/thuongtin/moviecast/MainActivity.kt",
            "src/main/java/com/thuongtin/moviecast/MainActivity.kt"
        )

        assertTrue(source.contains("WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)"))
        assertTrue(source.contains("WebViewCompat.setAudioMuted"))
        assertFalse(source.contains("interceptHtmlWithAppMuteBridge"))
        assertFalse(source.contains("content-security-policy"))
    }

    private fun readProjectFile(vararg paths: String): String {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val roots = generateSequence(start) { it.parentFile }.take(8).toList()
        for (root in roots) {
            for (path in paths) {
                val file = File(root, path)
                if (file.isFile) return file.readText()
            }
        }
        error("Project file not found: ${paths.joinToString()}")
    }
}
