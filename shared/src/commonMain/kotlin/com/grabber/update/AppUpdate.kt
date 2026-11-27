package com.grabber.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Repository GitHub delle release pubbliche (installer Windows e APK Android). */
const val GITHUB_REPOSITORY = "Gig81/KiwiSDR-Grabber"
const val GITHUB_RELEASES_PAGE = "https://github.com/$GITHUB_REPOSITORY/releases/latest"
const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPOSITORY/releases/latest"

/** Esito del controllo degli aggiornamenti mostrato dallo splash screen. */
sealed interface UpdateCheckState {
    data object Checking : UpdateCheckState

    data object UpToDate : UpdateCheckState

    /** È pubblicata una versione più recente di quella in uso. */
    data class Available(val latestVersion: String, val releaseUrl: String) : UpdateCheckState

    /** Controllo non riuscito (rete assente, limite dell'API…) o non applicabile (versione di sviluppo). */
    data class Unavailable(val reason: String) : UpdateCheckState
}

/** Ultima release pubblicata, ricavata dalla risposta di `GET /repos/{repo}/releases/latest`. */
data class LatestRelease(val version: String, val pageUrl: String)

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false
)

private val releaseJson = Json { ignoreUnknownKeys = true }

/**
 * Estrae versione e pagina della release dalla risposta JSON di GitHub.
 * Restituisce null per bozze, pre-release, tag senza numero di versione o JSON non valido.
 */
fun parseLatestRelease(json: String): LatestRelease? {
    val dto = try {
        releaseJson.decodeFromString<GitHubReleaseDto>(json)
    } catch (_: Exception) {
        return null
    }
    if (dto.draft || dto.prerelease) return null
    val version = dto.tagName.trim().removePrefix("v").removePrefix("V")
    if (parseVersion(version) == null) return null
    return LatestRelease(version, dto.htmlUrl.ifBlank { GITHUB_RELEASES_PAGE })
}

/** Versione numerica `major.minor.patch` (parti mancanti = 0); null se non è una versione di rilascio. */
fun parseVersion(version: String): List<Int>? {
    val parts = version.trim().split('.')
    if (parts.isEmpty() || parts.size > 4) return null
    return parts.map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
}

/** Vero se [candidate] è una versione successiva a [current]; falso se una delle due non è valida. */
fun isNewerVersion(candidate: String, current: String): Boolean {
    val newer = parseVersion(candidate) ?: return false
    val installed = parseVersion(current) ?: return false
    val length = maxOf(newer.size, installed.size)
    for (i in 0 until length) {
        val a = newer.getOrElse(i) { 0 }
        val b = installed.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

/** Stato finale del controllo dato l'ultima release pubblicata e la versione in uso. */
fun updateStateFor(release: LatestRelease, currentVersion: String): UpdateCheckState =
    if (isNewerVersion(release.version, currentVersion)) {
        UpdateCheckState.Available(release.version, release.pageUrl)
    } else {
        UpdateCheckState.UpToDate
    }
