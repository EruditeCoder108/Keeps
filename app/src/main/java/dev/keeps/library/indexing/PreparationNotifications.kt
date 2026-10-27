package dev.keeps.library.indexing

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.keeps.library.KeepsApplication
import dev.keeps.library.MainActivity
import dev.keeps.library.R
import kotlinx.coroutines.*
import dev.keeps.core.PreparationPhase

object PreparationNotifications {
    private const val CHANNEL = "content_preparation"
    internal const val ID = 710
    fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun refresh(context: Context) = synchronized(PreparationController.stateLock) {
        if (!allowed(context)) return@synchronized
        val controls = PreparationController.snapshot(context)
        val status = controls.status
        val manager = context.getSystemService(NotificationManager::class.java)
        if (status.phase == PreparationPhase.IDLE) { manager.cancel(ID); return@synchronized }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Search preparation", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 710, Intent(context, MainActivity::class.java).putExtra("keepsScreen", "search")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_preparation)
            .setContentTitle(status.title).setContentText(status.detail).setStyle(NotificationCompat.BigTextStyle().bigText(status.detail))
            .setContentIntent(open).setOnlyAlertOnce(true).setSilent(true).setOngoing(status.ongoing)
        val action = if (status.canPause) PausePreparationReceiver.PAUSE else if (status.canResume) PausePreparationReceiver.RESUME else null
        if (action != null) {
            val intent = Intent(context, PausePreparationReceiver::class.java).setAction(action)
                .setData(android.net.Uri.parse("keeps://preparation/$action/${controls.revision}"))
                .putExtra("revision", controls.revision)
            val pending = PendingIntent.getBroadcast(context, 711, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notification.addAction(R.drawable.ic_preparation, if (status.canPause) "Pause" else "Resume", pending)
        }
        try { NotificationManagerCompat.from(context).notify(ID, notification.build()) } catch (_: SecurityException) { }
    }
}

class PausePreparationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val graph = (context.applicationContext as KeepsApplication).graph
                val revision = if (intent.hasExtra("revision")) intent.getLongExtra("revision", -1) else null
                if (intent.action == RESUME) graph.repository.resumePreparation(expectedRevision = revision)
                else graph.repository.pause(revision)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                PreparationController(context).requestPause(reason = "Could not change preparation. Your saved data is kept; open Search library to review.")
            } finally { pending.finish() }
        }
    }
    companion object { const val PAUSE = "dev.keeps.library.PAUSE_PREPARATION"; const val RESUME = "dev.keeps.library.RESUME_PREPARATION" }
}
