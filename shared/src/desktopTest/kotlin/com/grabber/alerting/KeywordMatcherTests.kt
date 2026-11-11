package com.grabber.alerting

import kotlin.test.Test
import kotlin.test.assertEquals

class KeywordMatcherTests {
    @Test
    fun matchesCaseInsensitiveWholeWordsAndPhrases() {
        assertEquals(
            listOf("mayday", "pan pan"),
            KeywordMatcher.findMatches(
                "MAYDAY, qui IU7IYA. Pan   pan urgente.",
                "mayday, pan pan, day"
            )
        )
    }

    @Test
    fun doesNotMatchKeywordsInsideOtherWords() {
        assertEquals(
            emptyList(),
            KeywordMatcher.findMatches("emergenzaaa e maydays", "emergenza, mayday")
        )
    }
}
