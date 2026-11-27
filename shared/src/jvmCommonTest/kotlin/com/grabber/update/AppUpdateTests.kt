package com.grabber.update

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateTests {

    private fun releaseJson(tag: String, draft: Boolean = false, prerelease: Boolean = false) = """
        {"tag_name":"$tag","html_url":"https://github.com/$GITHUB_REPOSITORY/releases/tag/$tag",
         "draft":$draft,"prerelease":$prerelease,"assets":[{"name":"KiwiSDRGrabber.apk"}]}
    """.trimIndent()

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(isNewerVersion("1.4.0", "1.3.0"))
        assertTrue(isNewerVersion("1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("2.0", "1.9.9"))
        assertTrue(isNewerVersion("1.3.1", "1.3"))
        assertFalse(isNewerVersion("1.3.0", "1.3.0"))
        assertFalse(isNewerVersion("1.3", "1.3.0"))
        assertFalse(isNewerVersion("1.2.9", "1.3.0"))
    }

    @Test
    fun invalidVersionsAreNeverNewer() {
        assertFalse(isNewerVersion("1.4.0", "dev"))
        assertFalse(isNewerVersion("1.4.0-beta", "1.3.0"))
        assertFalse(isNewerVersion("", "1.3.0"))
    }

    @Test
    fun parsesLatestReleaseAndStripsTagPrefix() {
        val release = parseLatestRelease(releaseJson("v1.4.0"))
        assertEquals(LatestRelease("1.4.0", "https://github.com/$GITHUB_REPOSITORY/releases/tag/v1.4.0"), release)
    }

    @Test
    fun ignoresDraftsPrereleasesAndInvalidJson() {
        assertNull(parseLatestRelease(releaseJson("v1.4.0", draft = true)))
        assertNull(parseLatestRelease(releaseJson("v1.4.0", prerelease = true)))
        assertNull(parseLatestRelease(releaseJson("nightly")))
        assertNull(parseLatestRelease("not json"))
    }

    @Test
    fun checkerReportsAvailableUpdate() = runBlocking {
        val state = UpdateChecker("1.3.0") { releaseJson("v1.4.0") }.check()
        val available = assertIs<UpdateCheckState.Available>(state)
        assertEquals("1.4.0", available.latestVersion)
    }

    @Test
    fun checkerReportsUpToDate() = runBlocking {
        assertEquals(UpdateCheckState.UpToDate, UpdateChecker("1.3.0") { releaseJson("v1.3.0") }.check())
    }

    @Test
    fun networkErrorsAndDevBuildsAreUnavailable() = runBlocking {
        assertIs<UpdateCheckState.Unavailable>(UpdateChecker("1.3.0") { error("offline") }.check())
        var fetched = false
        val devState = UpdateChecker("dev") { fetched = true; releaseJson("v1.4.0") }.check()
        assertIs<UpdateCheckState.Unavailable>(devState)
        assertFalse(fetched, "una versione di sviluppo non deve interrogare GitHub")
    }
}
