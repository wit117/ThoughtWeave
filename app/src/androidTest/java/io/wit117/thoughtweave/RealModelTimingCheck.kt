package io.wit117.thoughtweave

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.wit117.thoughtweave.data.ai.engine.GemmaInferenceEngine
import io.wit117.thoughtweave.data.ai.model.ModelFileProvider
import io.wit117.thoughtweave.data.repository.MemoSynthesisRepositoryImpl
import io.wit117.thoughtweave.data.repository.ScheduleExtractionRepositoryImpl
import io.wit117.thoughtweave.domain.model.AiLanguagePlan
import io.wit117.thoughtweave.domain.model.AppLanguages
import io.wit117.thoughtweave.domain.model.Memo
import io.wit117.thoughtweave.domain.model.MemoOrigin
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMING_MODEL_PATH = "/data/local/tmp/gemma3-1b-it-int4.litertlm"
private const val TIMING_SETS_PATH = "/data/local/tmp/summary-bench/sets.json"

/** Benchmark sets with two or three memos (PER-04 is about 2–3 memos), Korean and English. */
private val SUMMARY_SETS = listOf("ko-4", "ko-8", "ko-11", "ko-1", "ko-2", "ko-3", "en-2", "en-6", "en-1")

/** Memos a user would extract a schedule from (PER-05): Korean and English, with and without a time. */
private val EXTRACTION_MEMOS = listOf(
    Triple("치과", "12월 9일(수) 오후 3시 치과 예약. 스케일링하고 사랑니 상담.", "ko"),
    Triple("디자인 리뷰", "디자인 리뷰는 12/16 수요일 오전 10시 30분, 3층 회의실. 최유진이 시안 3개를 준비한다.", "ko"),
    Triple("건강검진", "건강검진 12.10 오전 8시. 전날 밤 9시 이후 금식.", "ko"),
    Triple("Vet", "Vet appointment for Luna on Friday, Dec 4 at 10:15 am. Bring her vaccination card.", "en"),
    Triple("Demo", "Demo to the client on January 15 at 4 pm in room 5B.", "en"),
)

/**
 * PER-04 / PER-05 timings on a real device (requirements document), through the app's own repositories.
 * Cold: the first AI request after launch, so it includes loading the engine. Warm: later requests, each repeated
 * [WARM_REPEATS] times. The engine logs the backend it loaded ("Loaded on GPU"); read it with
 * `adb logcat -d -s GemmaInferenceEngine`. Skipped unless the model and the benchmark sets are in /data/local/tmp.
 * Writes `<external files>/timing/timing.json`.
 */
@RunWith(AndroidJUnit4::class)
class RealModelTimingCheck {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.of(2026, 11, 25)

    private fun newEngine() = GemmaInferenceEngine(
        context,
        object : ModelFileProvider {
            override fun verifiedModelFile() = File(TIMING_MODEL_PATH)
            override suspend fun onEngineInitFailed() = Unit
        },
        CoroutineScope(Dispatchers.Default),
    )

    private fun memo(id: Long, title: String, content: String) =
        Memo(id, title, content, Instant.EPOCH, Instant.EPOCH, false, MemoOrigin.MANUAL)

    private val rows = JSONArray()

    private fun record(kind: String, case: String, cold: Boolean, firstOutputMs: Long?, totalMs: Long, note: String) {
        Log.i("TIMING", "$kind $case cold=$cold first=${firstOutputMs}ms total=${totalMs}ms $note")
        rows.put(
            JSONObject().put("kind", kind).put("case", case).put("cold", cold)
                .put("firstOutputMs", firstOutputMs ?: JSONObject.NULL).put("totalMs", totalMs).put("note", note),
        )
    }

    /** Times the app's summary path: until the first text appears in S02-C, and until it is complete. */
    private suspend fun timeSummary(repo: MemoSynthesisRepositoryImpl, id: String, memos: List<Memo>, tag: String, cold: Boolean) {
        val language = checkNotNull(AppLanguages.forTag(tag))
        val start = SystemClock.elapsedRealtime()
        var first: Long? = null
        var text = ""
        repo.summarize(memos, AiLanguagePlan(language, language)).collect { chunk ->
            if (first == null && chunk.isNotBlank()) first = SystemClock.elapsedRealtime() - start
            text += chunk
        }
        val total = SystemClock.elapsedRealtime() - start
        record("summary", id, cold, first, total, "${memos.size} memos, ${text.length} chars")
    }

    private suspend fun timeExtraction(repo: ScheduleExtractionRepositoryImpl, title: String, content: String, tag: String, cold: Boolean) {
        val language = checkNotNull(AppLanguages.forTag(tag))
        val start = SystemClock.elapsedRealtime()
        val result = repo.extract(title, content, today, AiLanguagePlan(language, language))
        record("extraction", title, cold, null, SystemClock.elapsedRealtime() - start, "\"${result.title}\"")
    }

    @Test
    fun measure(): Unit = runBlocking {
        assumeTrue("Push the model to $TIMING_MODEL_PATH", File(TIMING_MODEL_PATH).isFile)
        assumeTrue("Push tools/summary-bench/sets.json to $TIMING_SETS_PATH", File(TIMING_SETS_PATH).isFile)
        val sets = JSONObject(File(TIMING_SETS_PATH).readText()).getJSONArray("sets")
        val byId = (0 until sets.length()).map { sets.getJSONObject(it) }.associateBy { it.getString("id") }
        fun memosOf(id: String): List<Memo> {
            val json = byId.getValue(id).getJSONArray("memos")
            return (0 until json.length()).map { i ->
                json.getJSONObject(i).let { memo(i + 1L, it.getString("title"), it.getString("content")) }
            }
        }

        // 1. Cold summary, then warm summaries and warm extractions on the same engine.
        val first = newEngine()
        val summaries = MemoSynthesisRepositoryImpl(first)
        val extractions = ScheduleExtractionRepositoryImpl(first)
        timeSummary(summaries, SUMMARY_SETS.first(), memosOf(SUMMARY_SETS.first()), "ko", cold = true)
        repeat(WARM_REPEATS) {
            for (id in SUMMARY_SETS) {
                timeSummary(summaries, id, memosOf(id), byId.getValue(id).getString("language"), cold = false)
            }
            for ((title, content, tag) in EXTRACTION_MEMOS) timeExtraction(extractions, title, content, tag, cold = false)
        }

        // 2. Let the first engine free itself (PER-07: 60 s idle), then a cold extraction on a fresh engine.
        delay(IDLE_WAIT_MILLIS)
        val (title, content, tag) = EXTRACTION_MEMOS.first()
        timeExtraction(ScheduleExtractionRepositoryImpl(newEngine()), title, content, tag, cold = true)

        val out = File(context.getExternalFilesDir(null), "timing/timing.json")
        out.parentFile!!.mkdirs()
        out.writeText(
            JSONObject().put("device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
                .put("rows", rows).toString(2),
        )
        Log.i("TIMING", "wrote ${out.absolutePath}")
    }

    private companion object {
        const val WARM_REPEATS = 3
        const val IDLE_WAIT_MILLIS = 65_000L
    }
}
