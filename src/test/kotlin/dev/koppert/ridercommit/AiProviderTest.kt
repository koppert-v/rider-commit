package dev.koppert.ridercommit

import kotlin.test.Test
import kotlin.test.assertEquals

class AiProviderTest {
    @Test
    fun `resolves Claude Code provider`() {
        assertEquals(AiProvider.CLAUDE_CODE, AiProvider.fromId("claude-code"))
    }

    @Test
    fun `falls back to Codex for an unknown provider`() {
        assertEquals(AiProvider.CODEX, AiProvider.fromId("unknown"))
    }
}
