package com.egg.codeeditor.terminal

import java.io.File

data class PathMatch(
    val start: Int = 0,
    val end: Int = 0,
    val file: File,
    val line: Int? = null,
    val column: Int? = null,
)

object FilePathUtils {
    private val quotedRegex = Regex("""(?<!\w)(["'])(.+?)\1""")
    private val pathRegex = Regex("""(?:file://)?[^\s"'()\[\]{}<>,;:]+(?::\d+){0,2}""")
    private val lineColRegex = Regex(""":(\d+)(?::(\d+))?$""")

    fun detectFilePaths(text: String, projectRoot: File? = null): List<PathMatch> {
        if (text.isBlank()) return emptyList()
        val matches = mutableListOf<PathMatch>()

        val found = mutableListOf<MatchResult>()
        quotedRegex.findAll(text).forEach { found.add(it) }
        pathRegex.findAll(text).forEach { found.add(it) }
        found.sortBy { it.range.first }

        for (match in found) {
            val quoted = if (match.groups.size > 2) match.groups[2] else null
            val rawCandidate = quoted?.value ?: match.value
            val matchStart = quoted?.range?.first ?: match.range.first
            val lineCol = lineColRegex.find(rawCandidate)
            val cleaned = rawCandidate.replace(lineColRegex, "")

            if (cleaned.length < 2) continue

            val file = resolveFile(cleaned, projectRoot)
            if (file != null) {
                val matchEnd = matchStart + rawCandidate.length

                if (matches.none { matchStart < it.end && matchEnd > it.start }) {
                    val line = lineCol?.groupValues?.get(1)?.toIntOrNull()
                    val column = lineCol?.groupValues?.get(2)?.toIntOrNull()

                    matches.add(
                        PathMatch(
                            start = matchStart,
                            end = matchEnd,
                            file = file,
                            line = line,
                            column = column,
                        )
                    )
                }
            }
        }
        return matches
    }

    private fun startsOrEquals(value: String, prefixes: List<String>): Boolean {
        for (prefix in prefixes) {
            if (value == prefix) return true
            if (value.startsWith(prefix)) return true
        }
        return false
    }

    fun resolveFile(path: String, projectRoot: File?): File? {
        val normalized = normalizeUriToPath(path)
        val isAbsolute = startsOrEquals(normalized, listOf("/", "~", "~/"))

        if (!isAbsolute && projectRoot != null) {
            val projectFile = File(projectRoot, normalized)
            if (projectFile.exists()) return projectFile
        } else {
            val hostFile = terminalToHostFile(normalized)
            if (hostFile.exists()) return hostFile
        }

        return null
    }
}