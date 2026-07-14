package dev.koppert.ridercommit

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object CliCommandResolver {
    fun resolve(configuredPath: String, defaultCommand: String): List<String> {
        val value = configuredPath.trim().ifBlank { defaultCommand }
        val direct = Path.of(value)
        if (direct.isAbsolute || value.contains(File.separatorChar) || value.contains('/') || value.contains('\\')) {
            if (!Files.isRegularFile(direct)) {
                throw IllegalStateException("CLI executable not found: $value")
            }
            return commandFor(direct.toAbsolutePath().normalize())
        }

        val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val extensions = if (isWindows) {
            // Prefer a native executable even when an npm shim appears earlier in PATH.
            listOf(".exe", ".com", ".cmd", ".bat", "")
        } else {
            listOf("")
        }
        val pathDirectories = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
        for (extension in extensions) {
            for (directory in pathDirectories) {
                val candidate = Path.of(directory, value + extension)
                if (Files.isRegularFile(candidate)) {
                    return commandFor(candidate.toAbsolutePath().normalize())
                }
            }
        }
        throw IllegalStateException(
            "CLI executable '$value' was not found in Rider's PATH. Set its absolute path in Settings | Tools | AI Commit.",
        )
    }

    private fun commandFor(path: Path): List<String> {
        return when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
            "cmd", "bat" -> listOf("cmd.exe", "/d", "/c", path.toString())
            "ps1" -> listOf(
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                path.toString(),
            )
            else -> listOf(path.toString())
        }
    }
}
