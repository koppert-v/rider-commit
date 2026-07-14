package dev.koppert.ridercommit

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultExecutable: String,
    val supportedEfforts: List<String>,
) {
    CODEX("codex", "Codex CLI", "codex", listOf("", "minimal", "low", "medium", "high", "xhigh")),
    CLAUDE_CODE("claude-code", "Claude Code", "claude", listOf("", "low", "medium", "high", "xhigh", "max")),
    ;

    override fun toString(): String = displayName

    companion object {
        fun fromId(id: String): AiProvider = entries.firstOrNull { it.id == id } ?: CODEX
    }
}
