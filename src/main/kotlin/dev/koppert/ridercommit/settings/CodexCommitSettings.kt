package dev.koppert.ridercommit.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import dev.koppert.ridercommit.AiProvider

@Service(Service.Level.PROJECT)
@State(name = "CodexCommitSettings", storages = [Storage("rider-codex-commit.xml")])
class CodexCommitSettings : PersistentStateComponent<CodexCommitSettings.SettingsState> {
    private var state = SettingsState().also(::ensureProviderConfigurations)
    private var splitButtonProviderId: String? = null

    override fun getState(): SettingsState = state.also(::ensureProviderConfigurations)

    override fun loadState(state: SettingsState) {
        ensureProviderConfigurations(state)
        this.state = state
    }

    fun selectedOrActiveProvider(): ProviderConfigurationState? =
        splitButtonProviderId
            ?.let { selectedId -> state.providerConfigurations.firstOrNull { it.id == selectedId } }
            ?: state.activeProvider()

    fun selectProviderForSession(providerId: String) {
        splitButtonProviderId = providerId
    }

    class SettingsState {
        var activeProviderId: String = ""
        var providerConfigurations: MutableList<ProviderConfigurationState> = mutableListOf()

        // Legacy 0.2 settings. Kept so existing installations can migrate automatically.
        var provider: String = AiProvider.CODEX.id
        var cliPath: String = "codex"
        var model: String = ""
        var reasoningEffort: String = "medium"
        var claudeCliPath: String = "claude"
        var claudeModel: String = ""
        var claudeEffort: String = "medium"
        var timeoutSeconds: Int = 120
        var commitLanguage: String = "English"
        var maxDiffCharacters: Int = 60_000
        var optimizeDiff: Boolean = true
        var contextMode: String = CommitContextMode.FILE_LIST.id
        var useConventionalCommits: Boolean = true

        fun activeProvider(): ProviderConfigurationState? =
            providerConfigurations.firstOrNull { it.id == activeProviderId } ?: providerConfigurations.firstOrNull()
    }

    class ProviderConfigurationState {
        var id: String = ""
        var name: String = ""
        var providerId: String = AiProvider.CODEX.id
        var executablePath: String = "codex"
        var model: String = ""
        var effort: String = "medium"
        var skill: String = ""
        var additionalPrompt: String = ""

        fun copyState(): ProviderConfigurationState = ProviderConfigurationState().also {
            it.id = id
            it.name = name
            it.providerId = providerId
            it.executablePath = executablePath
            it.model = model
            it.effort = effort
            it.skill = skill
            it.additionalPrompt = additionalPrompt
        }

        override fun toString(): String = name
    }

    companion object {
        const val DEFAULT_CODEX_ID = "default-codex"
        const val DEFAULT_CLAUDE_ID = "default-claude"

        fun getInstance(project: Project): CodexCommitSettings =
            project.getService(CodexCommitSettings::class.java)

        private fun ensureProviderConfigurations(state: SettingsState) {
            if (state.providerConfigurations.isEmpty()) {
                state.providerConfigurations += ProviderConfigurationState().also {
                    it.id = DEFAULT_CODEX_ID
                    it.name = "Codex CLI"
                    it.providerId = AiProvider.CODEX.id
                    it.executablePath = state.cliPath.ifBlank { AiProvider.CODEX.defaultExecutable }
                    it.model = state.model
                    it.effort = state.reasoningEffort
                }
                state.providerConfigurations += ProviderConfigurationState().also {
                    it.id = DEFAULT_CLAUDE_ID
                    it.name = "Claude Code"
                    it.providerId = AiProvider.CLAUDE_CODE.id
                    it.executablePath = state.claudeCliPath.ifBlank { AiProvider.CLAUDE_CODE.defaultExecutable }
                    it.model = state.claudeModel
                    it.effort = state.claudeEffort
                }
            }

            if (state.activeProviderId.isBlank() || state.providerConfigurations.none { it.id == state.activeProviderId }) {
                val legacyProvider = AiProvider.fromId(state.provider)
                state.activeProviderId = state.providerConfigurations
                    .firstOrNull { AiProvider.fromId(it.providerId) == legacyProvider }
                    ?.id
                    ?: state.providerConfigurations.first().id
            }
        }
    }
}
