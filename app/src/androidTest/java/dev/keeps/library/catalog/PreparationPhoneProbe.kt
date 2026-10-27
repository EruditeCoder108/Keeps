package dev.keeps.library.catalog

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import dev.keeps.library.KeepsApplication
import dev.keeps.library.indexing.IndexWorker
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.File
import java.security.MessageDigest

/** Read-only counts inside the phone. Never copies the catalog, URIs, filenames, text or vectors. */
class PreparationPhoneProbe {
    @Test fun reportAggregateState() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as KeepsApplication).graph
        check(IndexWorker.isPaused(context)) { "Pause the existing run before this read-only probe" }
        val db = graph.database.openHelper.readableDatabase
        fun count(sql: String): Long = db.query(sql).use { check(it.moveToFirst()); it.getLong(0) }
        fun groups(column: String): JSONArray {
            require(column in listOf("status", "kind"))
            val rows = JSONArray()
            db.query("SELECT $column, COUNT(*) FROM assets GROUP BY $column").use { cursor ->
                while (cursor.moveToNext()) rows.put(JSONObject().put("value", cursor.getString(0)).put("count", cursor.getLong(1)))
            }
            return rows
        }
        val controls = graph.preparation.controls.value
        val model = graph.embeddings.state.value
        val prefs = context.getSharedPreferences("preparation", android.content.Context.MODE_PRIVATE)
        val report = JSONObject().put("catalogRows", count("SELECT COUNT(*) FROM assets"))
            .put("states", groups("status")).put("kinds", groups("kind"))
            .put("chunks", count("SELECT COUNT(*) FROM chunks"))
            .put("modelInvalidationMarkers", count("SELECT COUNT(*) FROM assets WHERE detail = 'Model changed; index again'"))
            .put("sourceChangeMarkers", count("SELECT COUNT(*) FROM assets WHERE detail IN ('Source changed or access restored; prepare again','File changed or access restored; prepare again')"))
            .put("allFilesGranted", android.os.Environment.isExternalStorageManager())
            .put("excluded", count("SELECT COUNT(*) FROM assets WHERE searchExcluded = 1 OR folderSearchExcluded = 1"))
            .put("modelInstalled", model.installed).put("modelBytes", model.size).put("modelDigestPresent", model.digest.isNotBlank())
            .put("automatic", controls.automaticEnabled).put("paused", controls.paused).put("phase", controls.status.phase.name)
            .put("approvedTypes", JSONArray(prefs.getStringSet("automaticKinds", emptySet()).orEmpty().toList()))
            .put("explicitIdScope", prefs.contains("automaticIds"))
            .put("selectedIdCount", prefs.getStringSet("automaticIds", emptySet()).orEmpty().size)
        val mode = InstrumentationRegistry.getArguments().getString("keepsVectorCheck")
        if (mode in listOf("baseline", "verify")) {
            // Content/digest stay inside this app on the phone; the only exported result is counts + equality.
            val digest = MessageDigest.getInstance("SHA-256")
            db.query("SELECT namespace,vector,visualVector FROM chunks ORDER BY id").use { cursor ->
                while (cursor.moveToNext()) {
                    digest.update(cursor.getString(0).toByteArray())
                    if (!cursor.isNull(1)) digest.update(cursor.getBlob(1))
                    if (!cursor.isNull(2)) digest.update(cursor.getBlob(2))
                }
            }
            val fingerprint = digest.digest().joinToString("") { "%02x".format(it) }
            val baseline = File(context.filesDir, "preparation-audit-vector-check.json")
            if (mode == "baseline") {
                check(count("SELECT COUNT(*) FROM chunks") > 0) { "Prepare a result before establishing retention baseline" }
                baseline.writeText(JSONObject().put("fingerprint", fingerprint).put("model", model.digest).toString())
                report.put("vectorBaselineSavedOnPhone", true)
            } else {
                val saved = JSONObject(baseline.readText())
                assertEquals("Prepared vectors must survive the update", saved.getString("fingerprint"), fingerprint)
                assertEquals("Installed model must survive the update", saved.getString("model"), model.digest)
                report.put("preparedVectorsRetained", true).put("modelRetained", true)
                baseline.delete()
            }
        }
        try { report.put("daoRows", graph.dao.assets().size) } catch (e: Exception) { report.put("daoReadError", e.javaClass.simpleName) }
        instrumentation.sendStatus(0, Bundle().apply { putString("keepsAggregate", report.toString()) })
    }
}
