package dev.keeps.core

data class ScoredMoment(val id: String, val window: TimeWindow, val score: Float)

object MomentRanking {
    /** Keep separate candidate intervals; overlapping duplicates must not fill the result list. */
    fun select(candidates: List<ScoredMoment>, limit: Int = 5): List<ScoredMoment> {
        require(limit in 1..20)
        val selected = mutableListOf<ScoredMoment>()
        for (candidate in candidates.filter { it.score.isFinite() && it.score > 0 && it.window.endMs > it.window.startMs }
            .sortedWith(compareByDescending<ScoredMoment> { it.score }.thenBy { it.window.startMs })) {
            if (selected.none { existing ->
                val overlap = (minOf(existing.window.endMs, candidate.window.endMs) - maxOf(existing.window.startMs, candidate.window.startMs)).coerceAtLeast(0)
                val union = maxOf(existing.window.endMs, candidate.window.endMs) - minOf(existing.window.startMs, candidate.window.startMs)
                existing.id == candidate.id || overlap.toDouble() / union >= 0.5
            }) selected += candidate
            if (selected.size == limit) break
        }
        return selected
    }
}
