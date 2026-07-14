package dev.koppert.ridercommit

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object ProviderIcons {
    private val codex = IconLoader.getIcon("/icons/codexCommit.svg", ProviderIcons::class.java)
    private val claudeCode = IconLoader.getIcon("/icons/claudeCode15.svg", ProviderIcons::class.java)

    fun get(provider: AiProvider): Icon = when (provider) {
        AiProvider.CODEX -> codex
        AiProvider.CLAUDE_CODE -> claudeCode
    }
}
