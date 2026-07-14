package dev.koppert.ridercommit.settings

import dev.koppert.ridercommit.AiProvider

object ProviderOptions {
    val languages = listOf(
        "English",
        "Russian",
        "Ukrainian",
        "German",
        "French",
        "Spanish",
        "Italian",
        "Portuguese",
        "Polish",
        "Chinese",
        "Japanese",
        "Korean",
    )

    fun models(provider: AiProvider): List<String> = when (provider) {
        AiProvider.CODEX -> listOf(
            "gpt-5.6",
            "gpt-5.6-sol",
            "gpt-5.6-terra",
            "gpt-5.6-luna",
        )

        AiProvider.CLAUDE_CODE -> listOf(
            "sonnet",
            "opus",
            "haiku",
            "sonnet[1m]",
            "opus[1m]",
            "opusplan",
        )
    }
}
