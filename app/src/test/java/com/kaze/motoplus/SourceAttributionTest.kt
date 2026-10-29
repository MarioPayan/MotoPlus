package com.kaze.motoplus

import org.junit.Assert.*
import org.junit.Test

class SourceAttributionTest {
    @Test fun unknownReferrersDoNotBecomeAiProviders() {
        listOf(null, "com.android.shell", "com.android.chrome", "example.com", "com.kaze.motoplus").forEach { assertNull(aiSourceResource(it)) }
    }

    @Test fun onlyKnownAiAppsAndSitesReceiveAttribution() {
        assertEquals(R.string.p2_source_chatgpt, aiSourceResource("com.openai.chatgpt"))
        assertEquals(R.string.p2_source_gemini, aiSourceResource("gemini.google.com"))
        assertEquals(R.string.p2_source_claude, aiSourceResource("com.anthropic.claude"))
        assertEquals(R.string.p3_source_copilot, aiSourceResource("com.microsoft.copilot"))
        assertEquals(R.string.p3_source_perplexity, aiSourceResource("perplexity.ai"))
    }
}
