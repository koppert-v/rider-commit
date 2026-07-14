package dev.koppert.ridercommit

import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcsUtil.VcsUtil
import java.io.StringWriter

object CommitDiffBuilder {
    fun build(project: Project, changes: List<Change>): String {
        val changesByRoot = changes.mapNotNull { change ->
            val path = change.afterRevision?.file ?: change.beforeRevision?.file ?: return@mapNotNull null
            val root = VcsUtil.getVcsRootFor(project, path) ?: return@mapNotNull null
            root to change
        }.groupBy({ it.first }, { it.second })

        return changesByRoot.entries.joinToString("\n") { (root, rootChanges) ->
            val patches = IdeaTextPatchBuilder.buildPatch(
                project,
                rootChanges,
                root.toNioPath(),
                false,
                true,
            )
            StringWriter().also { writer ->
                writer.appendLine("Repository: ${root.path}")
                UnifiedDiffWriter.write(project, patches, writer, "\n", null)
            }.toString()
        }
    }
}
