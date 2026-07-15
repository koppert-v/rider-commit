package dev.koppert.ridercommit

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.SplitButtonAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.VcsDataKeys
import dev.koppert.ridercommit.settings.CodexCommitSettings

class GenerateCommitMessageSplitButtonAction : SplitButtonAction(ProviderActionGroup()), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private class ProviderActionGroup : ActionGroup() {
    private val actionsById = mutableMapOf<String, ProviderConfigurationAction>()

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e?.project == null) return emptyArray()
        val settings = CodexCommitSettings.getInstance()
        val selectedId = settings.selectedOrActiveProvider()?.id
        val configurations = settings.state.providerConfigurations.sortedWith(
            compareBy<CodexCommitSettings.ProviderConfigurationState> { it.id != selectedId }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )

        val validIds = configurations.mapTo(mutableSetOf()) { it.id }
        actionsById.keys.retainAll(validIds)

        val actions = mutableListOf<AnAction>()
        configurations.forEachIndexed { index, configuration ->
            if (index == 1) {
                actions += Separator.getInstance()
            }
            actions += actionsById.getOrPut(configuration.id) {
                ProviderConfigurationAction(configuration.id)
            }
        }
        return actions.toTypedArray()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private class ProviderConfigurationAction(
    private val providerConfigurationId: String,
) : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        if (e.project == null) return
        CodexCommitSettings.getInstance().selectProviderForSession(providerConfigurationId)
        GenerateCommitMessageAction.generate(e, providerConfigurationId)
    }

    override fun update(e: AnActionEvent) {
        val configuration = e.project
            ?.let { CodexCommitSettings.getInstance() }
            ?.state
            ?.providerConfigurations
            ?.firstOrNull { it.id == providerConfigurationId }

        if (configuration == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val provider = AiProvider.fromId(configuration.providerId)
        e.presentation.text = configuration.name.ifBlank { provider.displayName }
        e.presentation.description = configuration.model
            .takeIf(String::isNotBlank)
            ?.let { "${provider.displayName} — $it" }
            ?: provider.displayName
        e.presentation.icon = ProviderIcons.get(provider)
        e.presentation.isEnabledAndVisible = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
