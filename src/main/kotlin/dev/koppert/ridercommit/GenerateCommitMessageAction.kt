package dev.koppert.ridercommit

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import dev.koppert.ridercommit.settings.CodexCommitConfigurable
import dev.koppert.ridercommit.settings.CodexCommitSettings
import dev.koppert.ridercommit.settings.CommitContextMode

class GenerateCommitMessageAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null &&
            e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        generate(e)
    }

    companion object {
        fun generate(e: AnActionEvent, providerConfigurationId: String? = null) {
        val project = e.project ?: return
        val handler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER) as? AbstractCommitWorkflowHandler<*, *>
            ?: return
        val includedChanges = handler.ui.getIncludedChanges().toList()
        if (includedChanges.isEmpty()) {
            notify(project, "Select at least one change in the Commit tool window", NotificationType.WARNING)
            return
        }
        val settingsService = CodexCommitSettings.getInstance()
        val settings = settingsService.state
        val providerConfiguration = providerConfigurationId
            ?.let { selectedId -> settings.providerConfigurations.firstOrNull { it.id == selectedId } }
            ?: settingsService.selectedOrActiveProvider()
        if (providerConfiguration == null || providerConfiguration.executablePath.isBlank()) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, CodexCommitConfigurable::class.java)
            return
        }
        val provider = AiProvider.fromId(providerConfiguration.providerId)
        val currentMessage = handler.getCommitMessage()
        val projectDirectory = project.basePath?.let { java.nio.file.Path.of(it) }
        if (projectDirectory == null) {
            notify(project, "Cannot determine the project directory", NotificationType.ERROR)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Generating commit message with ${providerConfiguration.name.ifBlank { provider.displayName }}",
            true,
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                indicator.text = "Preparing selected changes"
                val contextMode = CommitContextMode.fromId(settings.contextMode)
                val changeContext = when (contextMode) {
                    CommitContextMode.FILE_LIST -> CommitFileListBuilder.build(project, includedChanges)
                    CommitContextMode.DIFF -> CommitDiffBuilder.build(project, includedChanges)
                }
                if (changeContext.isBlank()) {
                    notify(project, "The selected changes produced no AI context", NotificationType.WARNING)
                    return
                }

                indicator.text = "Waiting for ${providerConfiguration.name.ifBlank { provider.displayName }}"
                try {
                    val prompt = CodexPrompt.create(changeContext, currentMessage, settings, providerConfiguration)
                    val message = AiCliRunner.run(
                        projectDirectory,
                        prompt,
                        settings,
                        providerConfiguration,
                        indicator,
                    )
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) {
                            handler.setCommitMessage(message)
                        }
                    }
                } catch (cancelled: com.intellij.openapi.progress.ProcessCanceledException) {
                    throw cancelled
                } catch (error: Exception) {
                    notify(
                        project,
                        error.message ?: "Failed to generate commit message",
                        NotificationType.ERROR,
                    )
                }
            }
        })
        }

        private fun notify(project: Project, content: String, type: NotificationType) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Codex Commit")
                .createNotification(content, type)
                .notify(project)
        }
    }
}
