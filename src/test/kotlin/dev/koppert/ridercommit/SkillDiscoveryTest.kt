package dev.koppert.ridercommit

import dev.koppert.ridercommit.settings.SkillDiscovery
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals

class SkillDiscoveryTest {
    @Test
    fun `finds skill directories and legacy Claude commands`() {
        val root = Files.createTempDirectory("rider-commit-skills-")
        try {
            val skills = root.resolve("skills").createDirectories()
            val skillFile = skills.resolve("nested/commit-message").createDirectories().resolve("SKILL.md").createFile()
            Files.writeString(skillFile, "---\nname: custom-commit\ndescription: Test\n---\n")
            skills.resolve("not-a-skill").createDirectories()
            val commands = root.resolve("commands").createDirectories()
            commands.resolve("release-notes.md").createFile()

            assertEquals(
                listOf("custom-commit", "release-notes"),
                SkillDiscovery.discoverFromRoots(listOf(skills), listOf(commands)),
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
