package dev.keeps.core

enum class PreparationPhase { IDLE, SCHEDULED, PROCESSING, RESTING, WAITING, PAUSING, PAUSED, COMPLETE, NEEDS_REVIEW }

/** One projection for the in-app controls and notification; a wait is never called active processing. */
data class PreparationStatus(val phase: PreparationPhase, val detail: String = "") {
    val title get() = when (phase) {
        PreparationPhase.IDLE -> "Search preparation"
        PreparationPhase.SCHEDULED -> "Preparation scheduled"
        PreparationPhase.PROCESSING -> "Preparing search library"
        PreparationPhase.RESTING -> "Taking a short break"
        PreparationPhase.WAITING -> "Preparation waiting"
        PreparationPhase.PAUSING -> "Pausing preparation"
        PreparationPhase.PAUSED -> "Preparation paused"
        PreparationPhase.COMPLETE -> "Preparation up to date"
        PreparationPhase.NEEDS_REVIEW -> "Preparation needs review"
    }
    val canPause get() = phase in setOf(PreparationPhase.SCHEDULED, PreparationPhase.PROCESSING, PreparationPhase.RESTING, PreparationPhase.WAITING)
    val canResume get() = phase == PreparationPhase.PAUSED
    val ongoing get() = canPause || phase == PreparationPhase.PAUSING
}

/** Scheduling boundaries, independent of Android so long sequences can be verified on the laptop. */
object PreparationScheduling {
    const val PASS_MILLIS = 6 * 60_000L
    const val PASS_FILES = 48
    const val RESOURCE_RECHECK_MILLIS = 30_000L
    const val INTERACTIVE_RECHECK_MILLIS = 5 * 60_000L
    fun withinPass(files: Int, elapsedMillis: Long) = files < PASS_FILES && elapsedMillis < PASS_MILLIS
}
