package dev.koppert.ridercommit

import kotlin.test.Test
import kotlin.test.assertEquals

class CodexCliRunnerTest {
    @Test
    fun `removes markdown fence`() {
        assertEquals("feat: add commit generator", CodexCliRunner.cleanResult("```\nfeat: add commit generator\n```"))
    }

    @Test
    fun `removes a single pair of quotes`() {
        assertEquals("fix: handle empty diff", CodexCliRunner.cleanResult("\"fix: handle empty diff\""))
    }

    @Test
    fun `removes model preamble and keeps fenced commit body`() {
        val response = """
            This mostly involves binary and project settings changes.

            build(project): обновление настроек проекта

            ```
            1. В .gitignore добавлены исключения
            2. Обновлены настройки освещения
            ```
        """.trimIndent()

        val expected = """
            build(project): обновление настроек проекта

            ```
            1. В .gitignore добавлены исключения
            2. Обновлены настройки освещения
            ```
        """.trimIndent()

        assertEquals(expected, CodexCliRunner.cleanResult(response))
    }

    @Test
    fun `removes wrapper fence after a preamble`() {
        val response = "Commentary before result\n```text\nfeat(ui): add provider selector\n```"

        assertEquals("feat(ui): add provider selector", CodexCliRunner.cleanResult(response))
    }
}
