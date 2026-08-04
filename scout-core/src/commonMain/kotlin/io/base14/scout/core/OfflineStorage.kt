package io.base14.scout.core

import okio.FileSystem
import okio.Path

internal fun pruneDirToCap(fs: FileSystem, dir: Path, capBytes: Long) {
    if (capBytes <= 0L) return
    runCatching {
        if (!fs.exists(dir)) return@runCatching
        val entries = fs.list(dir).mapNotNull { p ->
            fs.metadataOrNull(p)?.takeIf { !it.isDirectory }
                ?.let { Triple(p, it.size ?: 0L, it.lastModifiedAtMillis ?: 0L) }
        }
        var total = entries.sumOf { it.second }
        if (total > capBytes) {
            for ((path, size, _) in entries.sortedBy { it.third }) {
                if (total <= capBytes) break
                if (runCatching { fs.delete(path) }.isSuccess) total -= size
            }
        }
    }
}
