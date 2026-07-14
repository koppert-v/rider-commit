package dev.koppert.ridercommit

object DiffOptimizer {
    private const val CONTEXT_LINES = 2
    private const val TRUNCATION_MARKER = "\n[File diff truncated]\n"

    fun prepare(diff: String, maxCharacters: Int, optimize: Boolean): String {
        val prepared = if (optimize) compact(diff) else diff
        return clipByFile(prepared, maxCharacters.coerceAtLeast(1_000))
    }

    internal fun compact(diff: String): String {
        val lines = diff.lines()
        val result = mutableListOf<String>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("@@")) {
                val hunkEnd = findHunkEnd(lines, index + 1)
                result += compactHunk(lines.subList(index, hunkEnd))
                index = hunkEnd
                continue
            }

            if (shouldKeepMetadata(line)) {
                result += line
            }
            index++
        }

        return result.joinToString("\n").trim()
    }

    private fun findHunkEnd(lines: List<String>, start: Int): Int {
        var index = start
        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("@@") || line.startsWith("Index: ") ||
                line.startsWith("diff --git ") || line.startsWith("Repository: ")
            ) {
                break
            }
            index++
        }
        return index
    }

    private fun compactHunk(lines: List<String>): List<String> {
        if (lines.size <= 1) return lines

        val keep = BooleanArray(lines.size)
        keep[0] = true
        lines.indices.drop(1).forEach { index ->
            val line = lines[index]
            if (line.startsWith("+") || line.startsWith("-") || line.startsWith("\\ No newline")) {
                val from = (index - CONTEXT_LINES).coerceAtLeast(1)
                val to = (index + CONTEXT_LINES).coerceAtMost(lines.lastIndex)
                for (nearbyIndex in from..to) {
                    keep[nearbyIndex] = true
                }
            }
        }

        val result = mutableListOf<String>()
        var omitted = false
        lines.indices.forEach { index ->
            if (keep[index]) {
                if (omitted) {
                    result += " ..."
                    omitted = false
                }
                result += lines[index]
            } else {
                omitted = true
            }
        }
        return result
    }

    private fun shouldKeepMetadata(line: String): Boolean =
        line.startsWith("Repository: ") ||
            line.startsWith("Index: ") ||
            line.startsWith("diff --git ") ||
            line.startsWith("--- ") ||
            line.startsWith("+++ ") ||
            line.startsWith("new file mode ") ||
            line.startsWith("deleted file mode ") ||
            line.startsWith("old mode ") ||
            line.startsWith("new mode ") ||
            line.startsWith("rename from ") ||
            line.startsWith("rename to ") ||
            line.contains("Binary files ")

    private fun clipByFile(diff: String, maxCharacters: Int): String {
        if (diff.length <= maxCharacters) return diff

        val marker = if (Regex("(?m)^Index: ").containsMatchIn(diff)) {
            Regex("(?m)(?=^Index: )")
        } else {
            Regex("(?m)(?=^diff --git )")
        }
        val sections = marker.split(diff).filter(String::isNotBlank)
        if (sections.size <= 1) return clip(diff, maxCharacters, "\n[Diff truncated]")

        val repositoryPrefix = sections.firstOrNull()
            ?.takeIf { it.startsWith("Repository: ") && !it.contains("Index: ") && !it.contains("diff --git ") }
            .orEmpty()
        val fileSections = if (repositoryPrefix.isNotEmpty()) sections.drop(1) else sections
        if (fileSections.isEmpty()) return clip(diff, maxCharacters, "\n[Diff truncated]")

        val separatorsLength = (fileSections.size - 1).coerceAtLeast(0)
        val available = (maxCharacters - repositoryPrefix.length - separatorsLength)
            .coerceAtLeast(fileSections.size * 40)
        val perFileBudget = (available / fileSections.size).coerceAtLeast(40)
        val clippedFiles = fileSections.map { section ->
            if (section.length <= perFileBudget) section.trimEnd()
            else clip(section, perFileBudget, TRUNCATION_MARKER).trimEnd()
        }

        return buildString {
            if (repositoryPrefix.isNotEmpty()) append(repositoryPrefix.trimEnd()).append('\n')
            append(clippedFiles.joinToString("\n"))
        }.take(maxCharacters).trimEnd()
    }

    private fun clip(value: String, budget: Int, marker: String): String {
        if (value.length <= budget) return value
        val contentBudget = (budget - marker.length).coerceAtLeast(1)
        val prefix = value.take(contentBudget).substringBeforeLast('\n', value.take(contentBudget))
        return prefix + marker
    }
}
