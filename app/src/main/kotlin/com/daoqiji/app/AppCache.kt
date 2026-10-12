package com.daoqiji.app

import android.content.Context
import java.io.File

internal data class CacheClearResult(val clearedBytes: Long, val failedFiles: Int)

/** Only temporary cache directories; preferences, item images and user backups are excluded. */
internal fun clearAppCache(context: Context): CacheClearResult {
    val roots = (listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull()).distinctBy { it.absolutePath }
    var cleared = 0L
    var failed = 0
    roots.forEach { root ->
        val boundary = root.canonicalFile.toPath()
        fun removeEntry(entry: File) {
            try {
                if (!entry.canonicalFile.toPath().startsWith(boundary)) {
                    // Never traverse a link that leads out of the cache directory.
                    failed++
                    return
                }
                if (entry.isDirectory) {
                    val children = entry.listFiles()
                    if (children == null) { failed++; return }
                    children.forEach(::removeEntry)
                }
                val bytes = if (entry.isFile) entry.length() else 0L
                if (entry.delete()) cleared += bytes else if (entry.exists()) failed++
            } catch (_: Exception) { failed++ }
        }
        if (root.exists()) {
            val entries = root.listFiles()
            if (entries == null) failed++ else entries.forEach(::removeEntry)
        }
    }
    return CacheClearResult(cleared, failed)
}
