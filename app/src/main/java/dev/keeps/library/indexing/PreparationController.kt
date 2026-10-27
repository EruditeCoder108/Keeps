package dev.keeps.library.indexing

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import dev.keeps.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PreparationControls(val pace: PreparationPace = PreparationPace.GENTLE, val batchSize: Int = 25, val chargingOnly: Boolean = false, val waiting: String = "",
    val automaticEnabled: Boolean = false, val automaticKinds: Set<AssetKind> = emptySet(), val paused: Boolean = false, val automaticIds: Set<String>? = null,
    val sessionStartedAt: Long = 0, val sessionInitialReady: Int = 0, val lastProgressAt: Long = 0, val sessionWorkMs: Long = 0,
    val status: PreparationStatus = PreparationStatus(PreparationPhase.IDLE), val revision: Long = 0, val sessionRestMs: Long = 0, val sessionEndedAt: Long = 0) {
    fun elapsedMs(now: Long) = if (sessionStartedAt <= 0) 0 else ((sessionEndedAt.takeIf { it > 0 } ?: now) - sessionStartedAt).coerceAtLeast(0)
    fun otherMs(now: Long) = (elapsedMs(now) - sessionWorkMs - sessionRestMs).coerceAtLeast(0)
}
data class PreparationSensors(val status: Int? = null, val headroom: Double? = null, val batteryC: Double? = null, val charging: Boolean = false,
    val lightHeadroom: Double? = null, val moderateHeadroom: Double? = null, val interactive: Boolean = true, val lowMemory: Boolean = false)

/** Commands and worker publications share a lock; superseded workers cannot overwrite newer commands. */
class PreparationController(private val context: Context,
    private val sensorSource: ((Boolean) -> PreparationSensors)? = null,
    private val elapsedMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallMillis: () -> Long = { System.currentTimeMillis() }) {
    private val preferences = context.getSharedPreferences("preparation", Context.MODE_PRIVATE)
    private val power = context.getSystemService(PowerManager::class.java)
    private var lastHeadroomMs = Long.MIN_VALUE
    private var cachedHeadroom: Double? = null
    private var warm = false
    private val mutableControls = MutableStateFlow(snapshot(context))
    val controls = mutableControls.asStateFlow()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutableControls.value = snapshot(context) }
    init {
        synchronized(stateLock) {
            val edit = preferences.edit()
            if (preferences.getInt("pacingPolicyVersion", 0) != 3) edit.remove("restUntil").remove("burstWorkMs").putInt("pacingPolicyVersion", 3)
            if (preferences.getString("workerProcess", "") != process && preferences.contains("workerOwner")) {
                edit.remove("workerOwner").remove("workerProcess")
                    .putString("phase", if (preferences.getBoolean("paused", false)) PreparationPhase.PAUSED.name else PreparationPhase.SCHEDULED.name)
                    .putString("waiting", if (preferences.getBoolean("paused", false)) "Your queue and completed search data are kept. Resume when ready." else "Waiting for Android to continue saved preparation")
                if (preferences.getBoolean("endRequested", false)) edit.remove("endRequested").putBoolean("paused", false)
                    .putString("phase", PreparationPhase.IDLE.name).putString("waiting", "").putLong("sessionEndedAt", wallMillis())
            }
            check(edit.commit())
            mutableControls.value = snapshot(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }
    private fun change(block: android.content.SharedPreferences.Editor.() -> Unit) = synchronized(stateLock) {
        check(preferences.edit().apply(block).commit())
        mutableControls.value = snapshot(context)
        PreparationNotifications.refresh(context)
    }
    fun pace(value: PreparationPace) = change { putString("pace", value.name) }
    fun batchSize(value: Int) { require(value in listOf(0, 25, 100)); change { putInt("batchSize", value) } }
    fun chargingOnly(value: Boolean) = change { putBoolean("chargingOnly", value) }
    fun automatic(enabled: Boolean, kinds: Set<AssetKind> = controls.value.automaticKinds, ids: Set<String>? = controls.value.automaticIds) {
        require(!enabled || kinds.isNotEmpty())
        change {
            putBoolean("automaticEnabled", enabled).putStringSet("automaticKinds", kinds.map { it.name }.toSet())
            if (ids == null) remove("automaticIds") else putStringSet("automaticIds", ids)
        }
    }
    fun beginSession(ready: Int) = change {
        putLong("sessionStartedAt", wallMillis()).putInt("sessionInitialReady", ready).putLong("sessionWorkMs", 0)
            .putLong("sessionRestMs", 0).putLong("sessionEndedAt", 0).putLong("lastProgressAt", 0)
    }
    fun current(revision: Long) = synchronized(stateLock) { preferences.getLong("revision", 0) == revision && !preferences.getBoolean("paused", false) }
    fun commandResume(expectedRevision: Long? = null): Long = synchronized(stateLock) {
        if (expectedRevision != null && preferences.getLong("revision", 0) != expectedRevision) return -1
        val revision = preferences.getLong("revision", 0) + 1
        change { putLong("revision", revision).putBoolean("paused", false).remove("restUntil").putLong("sessionEndedAt", 0)
            .putString("phase", PreparationPhase.SCHEDULED.name).putString("waiting", "Waiting for Android and the selected preparation conditions") }
        revision
    }
    fun requestPause(expectedRevision: Long? = null, reason: String? = null): Boolean = synchronized(stateLock) {
        if (expectedRevision != null && preferences.getLong("revision", 0) != expectedRevision || preferences.getBoolean("paused", false)) return false
        val owner = preferences.contains("workerOwner")
        change { putLong("revision", preferences.getLong("revision", 0) + 1).putBoolean("paused", true).remove("restUntil")
            if (reason == null) remove("pauseReason") else putString("pauseReason", reason)
            .putString("phase", if (owner) PreparationPhase.PAUSING.name else PreparationPhase.PAUSED.name)
            .putString("waiting", if (owner) "Finishing the current step. No new file will start." else reason ?: "Your queue and completed search data are kept.") }
        true
    }
    fun publish(revision: Long, phase: PreparationPhase, detail: String = ""): Boolean = synchronized(stateLock) {
        if (!current(revision)) return false
        change {
            putString("phase", phase.name).putString("waiting", detail)
            if (phase in listOf(PreparationPhase.SCHEDULED, PreparationPhase.PROCESSING)) putLong("sessionEndedAt", 0)
            if (phase in listOf(PreparationPhase.COMPLETE, PreparationPhase.NEEDS_REVIEW)) putLong("sessionEndedAt", wallMillis())
        }
        true
    }
    fun waiting(value: String) { publish(controls.value.revision, PreparationPhase.WAITING, value) }
    fun workerStarted(owner: String, revision: Long): Boolean = synchronized(stateLock) {
        if (!current(revision)) return false
        change { putString("workerOwner", owner).putString("workerProcess", process) }
        true
    }
    fun workerFinished(owner: String) = synchronized(stateLock) {
        if (preferences.getString("workerOwner", null) != owner) return@synchronized
        change {
            remove("workerOwner").remove("workerProcess")
            if (preferences.getBoolean("paused", false)) putString("phase", PreparationPhase.PAUSED.name)
                .putString("waiting", preferences.getString("pauseReason", "Your queue and completed search data are kept."))
        }
        if (preferences.getBoolean("endRequested", false)) endSession()
    }
    fun progressSaved() = change { putLong("lastProgressAt", wallMillis()) }
    fun restRemainingMs(): Long = synchronized(stateLock) {
        val remaining = preferences.getLong("restUntil", 0) - wallMillis()
        if (remaining > 10_000) { preferences.edit().remove("restUntil").commit(); 0 } else remaining.coerceAtLeast(0)
    }
    fun recordWork(workMs: Long, session: Long = controls.value.sessionStartedAt, revision: Long = controls.value.revision) = synchronized(stateLock) {
        if (session != preferences.getLong("sessionStartedAt", 0)) return@synchronized
        change {
            putLong("sessionWorkMs", preferences.getLong("sessionWorkMs", 0) + workMs.coerceAtLeast(0))
            if (current(revision)) putLong("restUntil", wallMillis() + PreparationHeat.restMillis(workMs, controls.value.pace, warm))
        }
    }
    fun recordRest(ms: Long, session: Long) = synchronized(stateLock) {
        if (session == preferences.getLong("sessionStartedAt", 0)) change { putLong("sessionRestMs", preferences.getLong("sessionRestMs", 0) + ms.coerceAtLeast(0)) }
    }
    fun endSession() = change { putLong("revision", preferences.getLong("revision", 0) + 1).putBoolean("paused", false)
        .putString("phase", PreparationPhase.IDLE.name).putString("waiting", "").putLong("sessionEndedAt", wallMillis()).remove("restUntil").remove("endRequested") }
    fun endWhenStopped() = synchronized(stateLock) {
        if (preferences.contains("workerOwner")) change { putBoolean("endRequested", true) } else endSession()
    }

    /** Thermal headroom polling respects Android's ten-second interval. Heat gates remain mandatory. */
    fun blockedReason(automatic: Boolean = false): String? = synchronized(stateLock) {
        val now = elapsedMillis()
        val refreshHeadroom = lastHeadroomMs == Long.MIN_VALUE || now - lastHeadroomMs >= 10_000
        val readings = sensorSource?.invoke(refreshHeadroom) ?: readSensors(refreshHeadroom)
        if (refreshHeadroom) { cachedHeadroom = readings.headroom; lastHeadroomMs = now }
        val (stop, resume) = PreparationHeat.headroomBands(readings.lightHeadroom, readings.moderateHeadroom)
        warm = PreparationHeat.warm(readings.status, cachedHeadroom, readings.batteryC, stop)
        if ((automatic || controls.value.chargingOnly) && !readings.charging) return "Waiting for charging · completed work is saved"
        if (automatic && readings.interactive) return "Waiting for phone to be idle · lock the screen to start; completed work is saved"
        if (readings.lowMemory) return "Waiting for available memory · completed work is saved"
        val cooling = PreparationHeat.needsCooling(readings.status, cachedHeadroom, readings.batteryC, preferences.getBoolean("cooling", false), stop, resume)
        check(preferences.edit().putBoolean("cooling", cooling).commit())
        if (cooling) "Cooling down · completed work is saved; resumes when cooler" else null
    }
    private fun readSensors(refreshHeadroom: Boolean): PreparationSensors {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val thresholds = if (android.os.Build.VERSION.SDK_INT >= 36) runCatching { power?.thermalHeadroomThresholds }.getOrNull() else null
        val memory = android.app.ActivityManager.MemoryInfo().also { context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }
        return PreparationSensors(runCatching { power?.currentThermalStatus }.getOrNull(),
            if (refreshHeadroom) runCatching { power?.getThermalHeadroom(10)?.toDouble() }.getOrNull() else cachedHeadroom,
            battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.div(10.0),
            battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } ?: false,
            thresholds?.get(PowerManager.THERMAL_STATUS_LIGHT)?.toDouble(), thresholds?.get(PowerManager.THERMAL_STATUS_MODERATE)?.toDouble(), power?.isInteractive ?: true, memory.lowMemory)
    }
    companion object {
        internal val stateLock = Any()
        private val process = UUID.randomUUID().toString()
        fun snapshot(context: Context): PreparationControls = synchronized(stateLock) {
            val p = context.getSharedPreferences("preparation", Context.MODE_PRIVATE)
            val paused = p.getBoolean("paused", false)
            val phase = if (paused) {
                if (p.contains("workerOwner") && p.getString("workerProcess", "") == process) PreparationPhase.PAUSING else PreparationPhase.PAUSED
            } else runCatching { PreparationPhase.valueOf(p.getString("phase", "IDLE")!!) }.getOrDefault(PreparationPhase.IDLE)
            PreparationControls(
                PreparationPace.entries.firstOrNull { it.name == p.getString("pace", "") } ?: PreparationPace.GENTLE,
                p.getInt("batchSize", 25).takeIf { it in listOf(0, 25, 100) } ?: 25, p.getBoolean("chargingOnly", false), p.getString("waiting", "").orEmpty(),
                p.getBoolean("automaticEnabled", false), p.getStringSet("automaticKinds", emptySet()).orEmpty().mapNotNull { saved -> AssetKind.entries.firstOrNull { it.name == saved } }.toSet(),
                paused, if (p.contains("automaticIds")) p.getStringSet("automaticIds", emptySet())?.toSet() else null,
                p.getLong("sessionStartedAt", 0), p.getInt("sessionInitialReady", 0), p.getLong("lastProgressAt", 0), p.getLong("sessionWorkMs", 0),
                PreparationStatus(phase, p.getString("waiting", "").orEmpty()), p.getLong("revision", 0), p.getLong("sessionRestMs", 0), p.getLong("sessionEndedAt", 0))
        }
    }
}
