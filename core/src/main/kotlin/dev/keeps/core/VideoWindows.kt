package dev.keeps.core

object VideoWindows {
    const val RECIPE = "video-tav-anchor-v1"
    const val FRAME_COUNT = 4
    fun frameTimes(window: TimeWindow): List<Long> {
        val duration = window.endMs - window.startMs
        require(duration > 0)
        val count = minOf(FRAME_COUNT.toLong(), duration).toInt()
        return List(count) { window.startMs + duration * it / count }
    }
}
