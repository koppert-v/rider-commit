package dev.koppert.ridercommit

import dev.koppert.ridercommit.settings.CodexCommitSettings
import dev.koppert.ridercommit.settings.CommitContextMode

object CodexPrompt {
    fun create(
        changeContext: String,
        currentMessage: String,
        settings: CodexCommitSettings.SettingsState,
        providerConfiguration: CodexCommitSettings.ProviderConfigurationState? = null,
    ): String {
        val contextMode = CommitContextMode.fromId(settings.contextMode)
        val preparedContext = when (contextMode) {
            CommitContextMode.FILE_LIST -> changeContext
            CommitContextMode.DIFF -> DiffOptimizer.prepare(
                changeContext,
                settings.maxDiffCharacters,
                settings.optimizeDiff,
            )
        }

        val formatInstruction = if (settings.useConventionalCommits) {
            "Use Conventional Commits format (type(scope): subject) when appropriate."
        } else {
            "Use a clear imperative subject line."
        }

        val skillInvocation = providerConfiguration
            ?.skill
            ?.trim()
            ?.removePrefix("$")
            ?.removePrefix("/")
            ?.takeIf(String::isNotBlank)
            ?.let { skill ->
                when (AiProvider.fromId(providerConfiguration.providerId)) {
                    AiProvider.CODEX -> "$$skill"
                    AiProvider.CLAUDE_CODE -> "/$skill"
                }
            }
            .orEmpty()
        val additionalPrompt = providerConfiguration?.additionalPrompt?.trim().orEmpty()
        val contextRequirements = when (contextMode) {
            CommitContextMode.FILE_LIST -> """
                - Rider supplied the authoritative list of selected changed files below.
                - Inspect only those paths using read-only file tools and scoped git diff commands.
                - Do not inspect unrelated files or changes.
            """.trimIndent()

            CommitContextMode.DIFF -> """
                - Treat the supplied diff as authoritative; do not inspect the repository or working tree.
                - Do not run commands.
            """.trimIndent()
        }
        val contextLabel = if (contextMode == CommitContextMode.FILE_LIST) "Selected changed files" else "Diff"

        return """
            $skillInvocation
            Generate one Git commit message for the supplied diff.

            Requirements:
            - Write the message in ${settings.commitLanguage}.
            - $formatInstruction
            - Keep the subject concise (prefer at most 72 characters).
            - Add a short body only when it provides important context.
            - Describe only changes from the supplied selection.
            $contextRequirements
            - Do not modify files, ask questions, or create a commit.
            - The first output line must be the commit subject.
            - Return only the commit message: no preamble, Markdown wrapper, labels, analysis, or alternatives.
            ${if (currentMessage.isNotBlank()) "- Treat this existing draft as an optional hint: $currentMessage" else ""}

            ${if (additionalPrompt.isNotBlank()) "Additional instructions:\n$additionalPrompt" else ""}

            $contextLabel:
            $preparedContext
        """.trimIndent().trim()
    }
}
