package dev.keeps.library.inference

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.library.KeepsApplication
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Checks real decoder preparation and playback progress, without making noise. */
class DevicePlaybackTest {
    @Test fun syntheticAudioAndVideoPlaybackAdvance(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("keepsDeviceFixtureTests") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as KeepsApplication).graph
        for (name in listOf("fixture-tone.wav", "fixture-clip.mp4")) {
            val asset = graph.dao.assets().singleOrNull { it.name == name && it.uri.contains("KeepsTest") }
            assumeTrue("Import synthetic fixtures through SAF first", asset != null)
            val ready = CountDownLatch(1)
            val error = AtomicReference<PlaybackException?>()
            lateinit var player: ExoPlayer
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context).build()
                player.volume = 0f
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) ready.countDown()
                    }
                    override fun onPlayerError(failure: PlaybackException) {
                        error.set(failure)
                        ready.countDown()
                    }
                })
                player.setMediaItem(MediaItem.fromUri(asset!!.uri))
                player.playWhenReady = true
                player.prepare()
            }
            try {
                assertTrue("$name did not prepare", ready.await(15, TimeUnit.SECONDS))
                assertNull("$name failed to decode", error.get())
                Thread.sleep(1_500)
                var position = 0L
                var duration = 0L
                instrumentation.runOnMainSync {
                    position = player.currentPosition
                    duration = player.duration
                }
                assertNull(error.get())
                assertTrue("$name did not advance", position >= 500)
                assertTrue("$name duration was invalid", duration in 2_900L..4_100L)
            } finally {
                instrumentation.runOnMainSync { player.release() }
            }
        }
    }
}
