package dev.keeps.core

object FileNames {
    fun validate(value: String): String {
        val name = value.trim()
        require(name.isNotEmpty() && name !in listOf(".", "..") && name.toByteArray(Charsets.UTF_8).size <= 240 &&
            name.none { it == '/' || it == '\\' || it.code < 32 }) { "Choose a name without slashes or control characters (up to 240 UTF-8 bytes)" }
        return name
    }
    fun available(requested: String, existing: Collection<String>): String {
        val name = validate(requested)
        val used = existing.map { it.lowercase(java.util.Locale.ROOT) }.toSet()
        if (name.lowercase(java.util.Locale.ROOT) !in used) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val stem = name.substring(0, dot)
        val extension = name.substring(dot)
        for (i in 2..10_000) {
            val candidate = validate("$stem ($i)$extension")
            if (candidate.lowercase(java.util.Locale.ROOT) !in used) return candidate
        }
        error("Too many files with this name; choose another name")
    }
}
