package dev.koppert.ridercommit

import com.intellij.openapi.components.Service
import dev.koppert.ridercommit.settings.CodexCommitSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexCommitSettingsMigrationTest {
    @Test
    fun `settings service is application scoped`() {
        val annotation = CodexCommitSettings::class.java.getAnnotation(Service::class.java)

        assertEquals(Service.Level.APP, annotation.value.single())
    }

    @Test
    fun `migrates legacy Codex and Claude settings into provider configurations`() {
        val legacy = CodexCommitSettings.SettingsState().also {
            it.provider = AiProvider.CLAUDE_CODE.id
            it.cliPath = "custom-codex"
            it.model = "gpt-test"
            it.claudeCliPath = "custom-claude"
            it.claudeModel = "sonnet"
        }

        val service = CodexCommitSettings()
        service.loadState(legacy)

        val state = service.state
        assertEquals(2, state.providerConfigurations.size)
        assertEquals("custom-codex", state.providerConfigurations[0].executablePath)
        assertEquals("gpt-test", state.providerConfigurations[0].model)
        assertEquals("custom-claude", state.providerConfigurations[1].executablePath)
        assertEquals("sonnet", state.providerConfigurations[1].model)
        assertEquals(AiProvider.CLAUDE_CODE.id, state.activeProvider()?.providerId)
    }

    @Test
    fun `split button remembers its provider without changing the saved default`() {
        val service = CodexCommitSettings()

        assertEquals(CodexCommitSettings.DEFAULT_CODEX_ID, service.state.activeProviderId)

        service.selectProviderForSession(CodexCommitSettings.DEFAULT_CLAUDE_ID)

        assertEquals(CodexCommitSettings.DEFAULT_CLAUDE_ID, service.selectedOrActiveProvider()?.id)
        assertEquals(CodexCommitSettings.DEFAULT_CODEX_ID, service.state.activeProviderId)
    }
}
