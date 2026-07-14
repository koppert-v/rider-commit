package dev.koppert.ridercommit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

object CommitFileListBuilder {
    fun build(project: Project, changes: List<Change>): String {
        val entries = changes.mapNotNull { change ->
            val currentFile = change.afterRevision?.file ?: change.beforeRevision?.file ?: return@mapNotNull null
            val root = VcsUtil.getVcsRootFor(project, currentFile) ?: return@mapNotNull null
            Entry(
                repository = root.path,
                status = change.type.name,
                path = displayPath(root, currentFile),
                previousPath = change.beforeRevision?.file
                    ?.takeIf { change.type == Change.Type.MOVED }
                    ?.let { displayPath(root, it) },
            )
        }

        return entries.groupBy(Entry::repository).entries.joinToString("\n\n") { (repository, repositoryEntries) ->
            buildString {
                appendLine("Repository: $repository")
                repositoryEntries.forEach { entry ->
                    append("- ${entry.status}: ")
                    if (entry.previousPath != null) {
                        append(entry.previousPath).append(" -> ")
                    }
                    appendLine(entry.path)
                }
            }.trimEnd()
        }
    }

    private fun displayPath(root: VirtualFile, file: FilePath): String = runCatching {
        root.toNioPath().relativize(file.ioFile.toPath()).toString().replace('\\', '/')
    }.getOrElse { file.path }

    private data class Entry(
        val repository: String,
        val status: String,
        val path: String,
        val previousPath: String?,
    )
}
