package dev.koppert.ridercommit.settings

enum class CommitContextMode(
    val id: String,
    private val displayName: String,
) {
    FILE_LIST("file-list", "Selected files — AI inspects them"),
    DIFF("diff", "Selected diff — precise"),
    ;

    override fun toString(): String = displayName

    companion object {
        fun fromId(id: String): CommitContextMode = entries.firstOrNull { it.id == id } ?: FILE_LIST
    }
}
