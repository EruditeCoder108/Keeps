package dev.keeps.library.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.core.*
import dev.keeps.library.KeepsApplication
import dev.keeps.library.indexing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Two-stage opt-in physical background probe; prepares only its explicit UUID-owned IDs. */
class AutomaticBackgroundProbe {
    private fun fingerprint(chunks: List<ChunkEntity>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chunks.sortedBy { it.id }.forEach { digest.update(it.id.toByteArray()); digest.update(it.text.toByteArray()); it.vector?.let(digest::update); it.visualVector?.let(digest::update) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    @Test fun armOrCheckScopedBackgroundPreparation() = runBlocking {
        val testBegan = System.currentTimeMillis()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val mode = InstrumentationRegistry.getArguments().getString("keepsAutomaticProbe")
        assumeTrue(mode == "arm" || mode == "check")
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as KeepsApplication).graph
        val stateFile = File(context.filesDir, "automatic-probe-state.json")
        val prefs = context.getSharedPreferences("preparation", Context.MODE_PRIVATE)
        val report = File(context.filesDir, "automatic-probe-report.json")
        if (mode == "arm") {
            check(!stateFile.exists()) { "Finish the existing probe first" }
            check(!graph.preparation.controls.value.automaticEnabled && !graph.dao.hasPendingPreparation()) { "Do not interrupt a personal preparation session" }
            check(graph.embeddings.state.value.installed && Environment.isExternalStorageManager())
            val before = graph.dao.chunks(graph.embeddings.namespace)
            val backup = JSONObject()
            prefs.all.forEach { (key, value) -> backup.put(key, JSONObject().put("type", when (value) { is Long -> "long"; is Int -> "int"; is Boolean -> "bool"; is Float -> "float"; is Set<*> -> "set"; else -> "string" }).put("value", if (value is Set<*>) JSONArray(value.toList()) else value)) }
            val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".KeepsAutomaticProbe-${UUID.randomUUID()}").apply { mkdirs() }
            val image = File(root, "automatic-probe.png")
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val text = File(root, "automatic-probe.txt").apply { writeText("Keeps synthetic overnight probe: a bicycle repair appointment on Saturday.") }
            val storage = dev.keeps.library.sources.StorageAccess(context)
            listOf(image, text).forEach { file -> graph.importer.catalogObserved(storage.info(Uri.fromFile(file)), null, null, null) }
            val owned = graph.dao.assets().filter { it.uri in listOf(Uri.fromFile(image).toString(), Uri.fromFile(text).toString()) }
            check(owned.size == 2)
            val state = JSONObject().put("root", root.absolutePath).put("ids", JSONArray(owned.map { it.id })).put("preferences", backup)
                .put("fingerprint", fingerprint(before)).put("beforeChunks", before.size).put("armedAt", testBegan)
            stateFile.writeText(state.toString())
            graph.repository.automatic(true, setOf(AssetKind.IMAGE, AssetKind.TEXT), owned.map { it.id }.toSet())
            assertEquals(owned.map { it.id }.toSet(), graph.preparation.controls.value.automaticIds)
        } else {
            check(stateFile.isFile)
            val state = JSONObject(stateFile.readText())
            val ids = state.getJSONArray("ids").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
            try {
                val owned = ids.mapNotNull { graph.dao.asset(it) }
                val lastSaved = graph.preparation.controls.value.lastProgressAt
                assertEquals(2, owned.size)
                assertTrue("Both fixtures must finish before the check starts", owned.all { it.status == IndexStatus.AI_READY })
                assertTrue("Progress must predate check instrumentation", lastSaved > state.getLong("armedAt") && lastSaved < testBegan)
                assertEquals(state.getString("fingerprint"), fingerprint(graph.dao.chunks(graph.embeddings.namespace).filter { it.assetId !in ids }))
                context.sendBroadcast(android.content.Intent(context, PausePreparationReceiver::class.java))
                withTimeout(10_000) { while (!IndexWorker.isPaused(context)) delay(50) }
                assertTrue(graph.dao.assetChunks(owned.first().id).isNotEmpty())
                report.writeText(JSONObject().put("passed", true).put("filesReady", 2).put("savedBeforeCheck", true)
                    .put("elapsedMs", lastSaved - state.getLong("armedAt")).put("originalChunksUnchanged", true).put("pauseReceiverPersisted", true).toString(2))
            } finally {
                graph.repository.automatic(false)
                graph.repository.pause()
                graph.preparationGate.withLock { ids.forEach { graph.dao.remove(it) } }
                graph.embeddings.unload()
                val root = File(state.getString("root"))
                check(root.parentFile?.canonicalFile == Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile && root.name.startsWith(".KeepsAutomaticProbe-"))
                root.deleteRecursively()
                val restored = state.getJSONObject("preferences")
                val edit = prefs.edit().clear()
                restored.keys().forEach { key -> val entry = restored.getJSONObject(key); when (entry.getString("type")) {
                    "bool" -> edit.putBoolean(key, entry.getBoolean("value"))
                    "int" -> edit.putInt(key, entry.getInt("value"))
                    "long" -> edit.putLong(key, entry.getLong("value"))
                    "float" -> edit.putFloat(key, entry.getDouble("value").toFloat())
                    "set" -> { val value = entry.getJSONArray("value"); edit.putStringSet(key, (0 until value.length()).map { value.getString(it) }.toSet()) }
                    else -> edit.putString(key, entry.getString("value"))
                } }
                check(edit.commit())
                stateFile.delete()
            }
        }
    }
}
