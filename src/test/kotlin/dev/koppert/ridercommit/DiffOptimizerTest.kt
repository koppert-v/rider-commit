package dev.koppert.ridercommit

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffOptimizerTest {
    @Test
    fun `keeps changes and nearby context while removing distant lines`() {
        val diff = """
            Repository: D:/project
            Index: src/App.kt
            ===================================================================
            --- src/App.kt
            +++ src/App.kt
            @@ -1,8 +1,8 @@
             distant before
             also distant
             nearby before two
             nearby before one
            -old value
            +new value
             nearby after one
             nearby after two
             distant after
        """.trimIndent()

        val compacted = DiffOptimizer.compact(diff)

        assertContains(compacted, "Index: src/App.kt")
        assertContains(compacted, "-old value")
        assertContains(compacted, "+new value")
        assertContains(compacted, "nearby before two")
        assertContains(compacted, "nearby after two")
        assertFalse(compacted.contains("distant before"))
        assertFalse(compacted.contains("distant after"))
    }

    @Test
    fun `shares a limited budget across files`() {
        val first = "Index: first.txt\n--- first.txt\n+++ first.txt\n@@ -1 +1 @@\n" + "+a\n".repeat(1_000)
        val second = "Index: second.txt\n--- second.txt\n+++ second.txt\n@@ -1 +1 @@\n+important\n"

        val prepared = DiffOptimizer.prepare(first + second, 1_000, false)

        assertTrue(prepared.length <= 1_000)
        assertContains(prepared, "Index: first.txt")
        assertContains(prepared, "Index: second.txt")
        assertContains(prepared, "+important")
    }
}
