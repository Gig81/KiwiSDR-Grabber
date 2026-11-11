package com.grabber.alerting

import java.util.Locale

object KeywordMatcher {
    fun findMatches(transcription: String, configuredWords: String): List<String> {
        val normalizedText = transcription.lowercase(Locale.ROOT)
        return configuredWords.split(',', ';', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .filter { phrase ->
                val escapedPhrase = phrase.lowercase(Locale.ROOT)
                    .split(Regex("\\s+"))
                    .joinToString("\\s+") { Regex.escape(it) }
                Regex(
                    "(?<![\\p{L}\\p{N}_])$escapedPhrase(?![\\p{L}\\p{N}_])",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(normalizedText)
            }
    }
}
