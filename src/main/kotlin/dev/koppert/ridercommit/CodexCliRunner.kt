package dev.koppert.ridercommit

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import dev.koppert.ridercommit.settings.CodexCommitSettings
import dev.koppert.ridercommit.settings.CodexCommitSettings.ProviderConfigurationState
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

object CodexCliRunner {
    fun run(
        projectDirectory: Path,
        prompt: String,
        settings: CodexCommitSettings.SettingsState,
        providerConfiguration: ProviderConfigurationState,
        indicator: ProgressIndicator,
    ): String {
        val resultFile = Files.createTempFile("rider-codex-commit-", ".txt")
        val logFile = Files.createTempFile("rider-codex-log-", ".txt")
        try {
            val command = CliCommandResolver.resolve(
                providerConfiguration.executablePath,
                AiProvider.CODEX.defaultExecutable,
            ).toMutableList().apply {
                addAll(listOf(
                "exec",
                "--skip-git-repo-check",
                "--ephemeral",
                "--sandbox",
                "read-only",
                "--color",
                "never",
                "--output-last-message",
                resultFile.toString(),
                ))
            }
            if (providerConfiguration.model.isNotBlank()) {
                require(providerConfiguration.model.matches(Regex("[A-Za-z0-9._:/\\[\\]-]+"))) {
                    "Model contains unsupported characters"
                }
                command += listOf("--model", providerConfiguration.model)
            }
            if (providerConfiguration.effort.isNotBlank()) {
                command += listOf("--config", "model_reasoning_effort=\"${providerConfiguration.effort}\"")
            }
            command += "-"

            val process = ProcessBuilder(command)
                .directory(projectDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
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
                        throw IllegalStateException("Codex CLI timed out after ${settings.timeoutSeconds} seconds")
                    }
                }
            } catch (cancelled: ProcessCanceledException) {
                process.destroyForcibly()
                throw cancelled
            }

            val log = Files.readString(logFile, StandardCharsets.UTF_8).trim()
            if (process.exitValue() != 0) {
                throw IllegalStateException("Codex CLI exited with code ${process.exitValue()}: ${log.takeLast(2_000)}")
            }

            val result = Files.readString(resultFile, StandardCharsets.UTF_8).trim()
            if (result.isBlank()) {
                throw IllegalStateException("Codex CLI returned an empty result. ${log.takeLast(1_000)}")
            }
            return cleanResult(result)
        } finally {
            Files.deleteIfExists(resultFile)
            Files.deleteIfExists(logFile)
        }
    }

    internal fun cleanResult(value: String): String {
        var result = value.trim()
        if (result.startsWith("```") && result.endsWith("```")) {
            result = result.removePrefix("```").removeSuffix("```").trim()
            result = result.removePrefix("text").trimStart()
        }

        val conventionalSubject = Regex(
            "(?im)^(?:build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)" +
                "(?:\\([^)]+\\))?!?:[ \\t]+\\S.*$",
        ).find(result)
        if (conventionalSubject != null && conventionalSubject.range.first > 0) {
            result = result.substring(conventionalSubject.range.first).trim()
            if (result.endsWith("```") && result.windowed(3).count { it == "```" } == 1) {
                result = result.removeSuffix("```").trimEnd()
            }
        }

        if (result.length >= 2 && result.first() == '"' && result.last() == '"' && !result.contains('\n')) {
            result = result.substring(1, result.length - 1)
        }
        return result
    }

}
