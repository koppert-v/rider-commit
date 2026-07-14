package dev.koppert.ridercommit

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import dev.koppert.ridercommit.settings.CodexCommitSettings
import dev.koppert.ridercommit.settings.CodexCommitSettings.ProviderConfigurationState
import dev.koppert.ridercommit.settings.CommitContextMode
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

object ClaudeCliRunner {
    fun run(
        projectDirectory: Path,
        prompt: String,
        settings: CodexCommitSettings.SettingsState,
        providerConfiguration: ProviderConfigurationState,
        indicator: ProgressIndicator,
    ): String {
        val resultFile = Files.createTempFile("rider-claude-commit-", ".txt")
        val logFile = Files.createTempFile("rider-claude-log-", ".txt")
        try {
            val contextMode = CommitContextMode.fromId(settings.contextMode)
            val fileListMode = contextMode == CommitContextMode.FILE_LIST
            val systemPrompt = if (fileListMode) {
                "Rider supplied the authoritative selected file list. Inspect only those paths with read-only tools " +
                    "and scoped git diff commands. Do not inspect unrelated changes, ask questions, modify files, " +
                    "or create a commit. The first output line must be the commit subject. Never include reasoning, " +
                    "inspection summaries, or a preamble. Return only the commit message."
            } else {
                "Rider supplied the authoritative selected diff. Do not inspect the repository, run commands, " +
                    "ask questions, modify files, or create a commit. The first output line must be the commit subject. " +
                    "Never include reasoning or a preamble. Return only the commit message."
            }
            val command = CliCommandResolver.resolve(
                providerConfiguration.executablePath,
                AiProvider.CLAUDE_CODE.defaultExecutable,
            ).toMutableList().apply {
                addAll(
                    listOf(
                        "--print",
                        "--input-format",
                        "text",
                        "--output-format",
                        "text",
                        "--no-session-persistence",
                        "--permission-mode",
                        "dontAsk",
                        "--append-system-prompt",
                        systemPrompt,
                        "--tools",
                        if (fileListMode) "Read,Grep,Glob,Bash" else "",
                    ),
                )
                if (fileListMode) {
                    addAll(
                        listOf(
                            "--allowedTools",
                            "Read,Grep,Glob,Bash(git diff *),Bash(git -C * diff *)",
                        ),
                    )
                }
            }
            if (providerConfiguration.model.isNotBlank()) {
                require(providerConfiguration.model.matches(Regex("[A-Za-z0-9._:/\\[\\]-]+"))) {
                    "Claude model contains unsupported characters"
                }
                command += listOf("--model", providerConfiguration.model)
            }
            if (providerConfiguration.effort.isNotBlank()) {
                command += listOf("--effort", providerConfiguration.effort)
            }

            val process = ProcessBuilder(command)
                .directory(projectDirectory.toFile())
                .redirectOutput(resultFile.toFile())
                .redirectError(logFile.toFile())
                .start()

            try {
                process.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.write(prompt)
                }

                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(settings.timeoutSeconds.toLong())
                while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    indicator.checkCanceled()
                    if (System.nanoTime() >= deadline) {
                        process.destroyForcibly()
                        throw IllegalStateException("Claude Code timed out after ${settings.timeoutSeconds} seconds")
                    }
                }
            } catch (cancelled: ProcessCanceledException) {
                process.destroyForcibly()
                throw cancelled
            }

            val log = Files.readString(logFile, StandardCharsets.UTF_8).trim()
            if (process.exitValue() != 0) {
                throw IllegalStateException("Claude Code exited with code ${process.exitValue()}: ${log.takeLast(2_000)}")
            }

            val result = Files.readString(resultFile, StandardCharsets.UTF_8).trim()
            if (result.isBlank()) {
                throw IllegalStateException("Claude Code returned an empty result. ${log.takeLast(1_000)}")
            }
            return CodexCliRunner.cleanResult(result)
        } finally {
            Files.deleteIfExists(resultFile)
            Files.deleteIfExists(logFile)
        }
    }
}
