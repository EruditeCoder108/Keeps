package dev.keeps.library.indexing

import android.content.Context
import androidx.work.*
import dev.keeps.library.KeepsApplication
import java.util.concurrent.TimeUnit
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.keeps.library.catalog.LibraryRepository
import kotlinx.coroutines.*

/** Persistent planning is metadata-only until the explicitly approved scope is queued. */
object AutomaticPreparation {
    const val PERIODIC = "keeps-auto-discovery-v1"
    const val ONCE = "keeps-auto-review-v1"
    fun schedule(context: Context, soon: Boolean = false, delaySeconds: Long = 30) {
        val prefs = context.getSharedPreferences("preparation", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("automaticEnabled", false) || prefs.getBoolean("paused", false)) return
        val constraints = Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutomaticPlanningWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).addTag("automatic-planning").build())
        if (soon) manager.enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutomaticPlanningWorker>().setInitialDelay(delaySeconds.coerceIn(0, 30), TimeUnit.SECONDS).setConstraints(constraints).addTag("automatic-planning").build())
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelAllWorkByTag("automatic-planning").result.get() }
    fun observeOpportunities(context: Context, repository: LibraryRepository) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val controls = PreparationController.snapshot(context)
                if (!controls.automaticEnabled || controls.paused) return
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try { repository.wakeAutomatic() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { PreparationController(context).publish(controls.revision, dev.keeps.core.PreparationPhase.WAITING, "Could not schedule preparation. Open Search library to review.") }
                    finally { pending.finish() }
                }
            }
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_POWER_CONNECTED) }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}

class AutomaticPlanningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as KeepsApplication).graph
        val controls = graph.preparation.controls.value
        if (!controls.automaticEnabled || IndexWorker.isPaused(applicationContext)) return Result.success()
        graph.preparation.blockedReason(automatic = true)?.let {
            if (controls.status.phase !in listOf(dev.keeps.core.PreparationPhase.COMPLETE, dev.keeps.core.PreparationPhase.NEEDS_REVIEW))
                graph.preparation.publish(controls.revision, dev.keeps.core.PreparationPhase.WAITING, it)
            return Result.success()
        }
        return try {
            if (controls.automaticIds == null) {
                graph.media.scan()
                graph.dao.locations().filter { !it.uri.startsWith("file:") }.forEach { location ->
                    try { graph.importer.scan(location.uri) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Individual disconnected locations remain unavailable. */ }
                }
            }
            graph.repository.queueSelected(controls.automaticIds, controls.automaticKinds, automatic = true)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            graph.preparation.publish(controls.revision, dev.keeps.core.PreparationPhase.WAITING, "Automatic preparation needs access or storage. Open Search library to review.")
            Result.retry()
        }
    }
}
