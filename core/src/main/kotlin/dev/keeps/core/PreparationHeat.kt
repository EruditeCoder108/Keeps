package dev.keeps.core

enum class PreparationPace(val label: String) {
    GENTLE("Gentle"), BALANCED("Balanced")
}

/** Conservative scheduling heuristics, not device-specific safe-temperature guarantees. */
object PreparationHeat {
    fun headroomBands(light: Double?, moderate: Double?): Pair<Double, Double> =
        if (light != null && moderate != null && light.isFinite() && moderate.isFinite() && light >= 0 && moderate > light && moderate <= 1.5)
            minOf((light + moderate) / 2, 1.0) to minOf(light, 0.95)
        else 0.9 to 0.8

    fun needsCooling(status: Int?, headroom: Double?, batteryC: Double?, cooling: Boolean,
                     stopHeadroom: Double = 0.9, resumeHeadroom: Double = 0.8): Boolean {
        val head = headroom?.takeIf { it.isFinite() && it >= 0 }
        val battery = batteryC?.takeIf { it.isFinite() && it in 1.0..65.0 }
        return if (cooling) (status ?: 0) >= 2 || head != null && head > resumeHeadroom || battery != null && battery > 38.0
        else (status ?: 0) >= 2 || head != null && head >= stopHeadroom || battery != null && battery >= 40.0
    }

    fun warm(status: Int?, headroom: Double?, batteryC: Double?, stopHeadroom: Double): Boolean =
        (status ?: 0) >= 1 || headroom?.takeIf { it.isFinite() && it >= 0 }?.let { it >= stopHeadroom * 0.85 } == true ||
            batteryC?.takeIf { it.isFinite() && it in 1.0..65.0 }?.let { it >= 38.0 } == true

    // Cooling is a separate sensor gate. Do not add minute-long burst rests to every minute of useful work.
    fun restMillis(workMillis: Long, pace: PreparationPace, warm: Boolean = false): Long {
        val work = workMillis.coerceIn(0, 300_000)
        return when {
            pace == PreparationPace.GENTLE && warm -> (work / 2).coerceAtMost(10_000)
            pace == PreparationPace.GENTLE -> (work / 4).coerceAtMost(5_000)
            warm -> (work / 4).coerceAtMost(5_000)
            else -> 0
        }
    }
}
