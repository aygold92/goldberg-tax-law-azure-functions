package com.goldberg.law.agent

/** One memory in a store, as listed without its content. [path] is store-relative with a leading slash. */
data class StoredMemory(val path: String, val sizeBytes: Int, val updatedAt: Long)

/**
 * A memory store's files, grouped the way the agents lay them out: one folder per bank id, holding the
 * consolidated `main.md` and the session files not yet folded into it.
 */
data class MemoryStoreListing(
    val memory: MemoryConsolidation,
    val folders: List<MemoryFolder>,
    /** Files directly under the store's root, outside any bank folder. No agent writes these on purpose. */
    val looseFiles: List<MemoryFile>,
) {
    fun folder(bankId: String): MemoryFolder? = folders.firstOrNull { it.bankId == bankId }

    companion object {
        fun of(memory: MemoryConsolidation, memories: List<StoredMemory>): MemoryStoreListing {
            val (nested, loose) = memories.map { it to it.path.trimStart('/') }.partition { (_, relative) -> '/' in relative }
            val folders = nested
                .groupBy({ (_, relative) -> relative.substringBefore('/') }) { (memory, relative) ->
                    MemoryFile(relative.substringAfter('/'), memory.sizeBytes, memory.updatedAt)
                }
                .map { (bankId, files) -> MemoryFolder.of(bankId, files.sortedBy { it.name }) }
                .sortedBy { it.bankId }
            return MemoryStoreListing(
                memory,
                folders,
                loose.map { (memory, relative) -> MemoryFile(relative, memory.sizeBytes, memory.updatedAt) }.sortedBy { it.name },
            )
        }
    }
}

data class MemoryFolder(
    val bankId: String,
    val files: List<MemoryFile>,
    val hasMain: Boolean,
    /** Session files waiting to be consolidated into `main.md`. */
    val sessionFileCount: Int,
) {
    companion object {
        fun of(bankId: String, files: List<MemoryFile>) = MemoryFolder(
            bankId,
            files,
            hasMain = files.any { it.name == MAIN_FILE },
            sessionFileCount = files.count { isSessionFile(it.name) },
        )

        const val MAIN_FILE = "main.md"

        /** An agent's per-session file: named for its session id, which the platform prefixes `sesn_`. */
        fun isSessionFile(name: String): Boolean = name.startsWith("sesn_") && name.endsWith(".md") && '/' !in name
    }
}

/** [updatedAt] is epoch millis, like the app's other timestamps. */
data class MemoryFile(val name: String, val sizeBytes: Int, val updatedAt: Long)
