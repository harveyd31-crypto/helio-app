package app.helio.strap

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.time.Instant
import java.util.zip.GZIPInputStream

/**
 * Imports the Zepp cloud history file made on the computer by tools/zepp-history (`export`):
 * per-minute heart rate, steps and stress, sleep with stages, and day totals. It only fills gaps:
 * any minute, night or day the strap delivered itself is kept. Afterwards every derived number is
 * worked out again from the longer history.
 */
object ZeppImport {
    data class Result(val samples: Int, val nights: Int, val days: Int, val first: String?, val last: String?)

    suspend fun run(ctx: Context, uri: Uri, onProgress: (String) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        val store = StrapManager.store(ctx)
        var samples = 0; var nights = 0; var totals = 0
        var first: String? = null; var last: String? = null
        val raw = BufferedInputStream(ctx.contentResolver.openInputStream(uri) ?: error("Couldn't open the file."))
        raw.mark(2)
        val gz = raw.read() == 0x1f && raw.read() == 0x8b
        raw.reset()
        val input = if (gz) GZIPInputStream(raw, 1 shl 16) else raw
        JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
            r.beginObject()
            var format: String? = null
            while (r.hasNext()) {
                when (r.nextName()) {
                    "format" -> format = r.nextString()
                    "first" -> first = r.nextString()
                    "last" -> last = r.nextString()
                    "samples" -> {
                        r.beginArray()
                        val batch = ArrayList<Triple<String, Long, Double>>(5000)
                        while (r.hasNext()) {
                            r.beginArray()
                            val metric = r.nextString(); val ts = r.nextLong(); val v = r.nextDouble()
                            r.endArray()
                            if (metric == "hr" || metric == "steps" || metric == "stress") batch += Triple(metric, ts, v)
                            if (batch.size >= 5000) {
                                store.restoreSamples(batch, replace = false); samples += batch.size; batch.clear()
                                onProgress(String.format("Heart rate, steps and stress: %,d", samples))
                            }
                        }
                        r.endArray()
                        if (batch.isNotEmpty()) { store.restoreSamples(batch, replace = false); samples += batch.size }
                    }
                    "sleep" -> {
                        r.beginArray()
                        store.batch { while (r.hasNext()) { store.restoreSleep(readObject(r), replace = false); nights++ } }
                        r.endArray()
                        onProgress("$nights nights")
                    }
                    "daily_totals" -> {
                        r.beginArray()
                        store.batch { while (r.hasNext()) { store.restoreTotal(readObject(r)); totals++ } }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            require(format?.startsWith("helio-zepp-history") == true) { "This isn't a Helio history file (make it with tools/zepp-history export)." }
        }
        onProgress("Working every day out again")
        store.clearDerived()
        store.setMetaValue("zepp_imported_at", Instant.now().toEpochMilli().toString())
        Result(samples, nights, totals, first, last)
    }

    private fun readObject(r: JsonReader): JSONObject {
        val o = JSONObject()
        r.beginObject()
        while (r.hasNext()) o.put(r.nextName(), readValue(r))
        r.endObject()
        return o
    }

    private fun readValue(r: JsonReader): Any = when (r.peek()) {
        JsonToken.BEGIN_OBJECT -> readObject(r)
        JsonToken.BEGIN_ARRAY -> JSONArray().also { a -> r.beginArray(); while (r.hasNext()) a.put(readValue(r)); r.endArray() }
        JsonToken.NUMBER -> r.nextString().let { s -> s.toLongOrNull() ?: s.toDouble() }
        JsonToken.STRING -> r.nextString()
        JsonToken.BOOLEAN -> r.nextBoolean()
        else -> { r.nextNull(); JSONObject.NULL }
    }
}
