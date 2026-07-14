package dev.koppert.ridercommit

import com.intellij.openapi.progress.ProgressIndicator
import dev.koppert.ridercommit.settings.CodexCommitSettings
import dev.koppert.ridercommit.settings.CodexCommitSettings.ProviderConfigurationState
import java.nio.file.Path

object AiCliRunner {
    fun run(
        projectDirectory: Path,
        prompt: String,
        settings: CodexCommitSettings.SettingsState,
        indicator: ProgressIndicator,
    ): String {
        val providerConfiguration = settings.activeProvider()
            ?: throw IllegalStateException("No AI CLI provider is configured")
        return run(projectDirectory, prompt, settings, providerConfiguration, indicator)
    }

    fun run(
        projectDirectory: Path,
        prompt: String,
        settings: CodexCommitSettings.SettingsState,
        providerConfiguration: ProviderConfigurationState,
        indicator: ProgressIndicator,
    ): String = when (AiProvider.fromId(providerConfiguration.providerId)) {
        AiProvider.CODEX -> CodexCliRunner.run(projectDirectory, prompt, settings, providerConfiguration, indicator)
        AiProvider.CLAUDE_CODE -> ClaudeCliRunner.run(projectDirectory, prompt, settings, providerConfiguration, indicator)
    }
}
