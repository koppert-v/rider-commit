package dev.koppert.ridercommit

import dev.koppert.ridercommit.settings.CodexCommitSettings
import dev.koppert.ridercommit.settings.CommitContextMode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class CodexPromptTest {
    @Test
    fun `uses Codex skill syntax and includes additional prompt`() {
        val settings = CodexCommitSettings.SettingsState()
        settings.contextMode = CommitContextMode.DIFF.id
        val provider = configuration(AiProvider.CODEX).also {
            it.skill = "commit-message"
            it.additionalPrompt = "Use the repository ticket identifier in the scope."
        }

        val prompt = CodexPrompt.create("diff content", "", settings, provider)

        assertTrue(prompt.startsWith("\$commit-message"))
        assertContains(prompt, "Additional instructions:")
        assertContains(prompt, provider.additionalPrompt)
        assertContains(prompt, "Treat the supplied diff as authoritative")
        assertContains(prompt, "Do not run commands")
    }

    @Test
    fun `uses Claude Code skill syntax`() {
        val settings = CodexCommitSettings.SettingsState()
        val provider = configuration(AiProvider.CLAUDE_CODE).also {
            it.skill = "/commit-message"
        }

        val prompt = CodexPrompt.create("diff content", "", settings, provider)

        assertTrue(prompt.startsWith("/commit-message"))
    }

    @Test
    fun `file list mode tells the AI to inspect only selected paths`() {
        val settings = CodexCommitSettings.SettingsState()
        settings.contextMode = CommitContextMode.FILE_LIST.id

        val prompt = CodexPrompt.create("Repository: D:/project\n- MODIFICATION: src/App.kt", "", settings)

        assertContains(prompt, "Selected changed files:")
        assertContains(prompt, "Inspect only those paths")
        assertContains(prompt, "src/App.kt")
    }

    private fun configuration(provider: AiProvider) =
        CodexCommitSettings.ProviderConfigurationState().also {
            it.providerId = provider.id
            it.executablePath = provider.defaultExecutable
        }
}
