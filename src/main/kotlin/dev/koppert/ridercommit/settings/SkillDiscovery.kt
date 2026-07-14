package dev.koppert.ridercommit.settings

import com.intellij.openapi.project.Project
import dev.koppert.ridercommit.AiProvider
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path

object SkillDiscovery {
    fun find(provider: AiProvider, project: Project): List<String> {
        val projectDirectory = project.basePath?.let(Path::of)
        val homeDirectory = System.getProperty("user.home")?.takeIf(String::isNotBlank)?.let(Path::of)
        val skillRoots = linkedSetOf<Path>()
        val commandRoots = linkedSetOf<Path>()

        when (provider) {
            AiProvider.CODEX -> {
                projectDirectory?.let { addProjectRoots(it, ".agents/skills", skillRoots) }
                homeDirectory?.resolve(".agents/skills")?.let(skillRoots::add)
                val codexHome = System.getenv("CODEX_HOME")
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of)
                    ?: homeDirectory?.resolve(".codex")
                codexHome?.resolve("skills")?.let(skillRoots::add)
            }

            AiProvider.CLAUDE_CODE -> {
                projectDirectory?.let {
                    addProjectRoots(it, ".claude/skills", skillRoots)
                    addProjectRoots(it, ".claude/commands", commandRoots)
                }
                homeDirectory?.resolve(".claude/skills")?.let(skillRoots::add)
                homeDirectory?.resolve(".claude/commands")?.let(commandRoots::add)
            }
        }

        return discoverFromRoots(skillRoots, commandRoots)
    }

    internal fun discoverFromRoots(
        skillRoots: Iterable<Path>,
        commandRoots: Iterable<Path> = emptyList(),
    ): List<String> {
        val names = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
        skillRoots.forEach { root ->
            if (Files.isDirectory(root)) {
                runCatching {
                    Files.walk(root, 5, FileVisitOption.FOLLOW_LINKS).use { paths ->
                        paths
                            .filter { path ->
                                Files.isRegularFile(path) && path.fileName.toString().equals("SKILL.md", ignoreCase = true)
                            }
                            .forEach { skillFile -> names += readSkillName(skillFile) }
                    }
                }
            }
        }
        commandRoots.forEach { root ->
            listChildren(root) { child ->
                if (Files.isRegularFile(child) && child.fileName.toString().endsWith(".md", ignoreCase = true)) {
                    names += child.fileName.toString().substringBeforeLast('.')
                }
            }
        }
        return names.toList()
    }

    private fun readSkillName(skillFile: Path): String = runCatching {
        Files.newBufferedReader(skillFile).useLines { lines ->
            lines
                .take(30)
                .map(String::trim)
                .firstOrNull { it.startsWith("name:") }
                ?.substringAfter("name:")
                ?.trim()
                ?.trim('"', '\'')
                ?.takeIf(String::isNotBlank)
        }
    }.getOrNull() ?: skillFile.parent.fileName.toString()

    private fun addProjectRoots(start: Path, relativePath: String, target: MutableSet<Path>) {
        var directory: Path? = start.toAbsolutePath().normalize()
        while (directory != null) {
            target.add(directory.resolve(relativePath))
            if (Files.exists(directory.resolve(".git"))) break
            directory = directory.parent
        }
    }

    private fun listChildren(root: Path, action: (Path) -> Unit) {
        if (!Files.isDirectory(root)) return
        runCatching {
            Files.list(root).use { children -> children.forEach(action) }
        }
    }
}
