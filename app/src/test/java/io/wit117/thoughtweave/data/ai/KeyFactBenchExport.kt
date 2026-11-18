package io.wit117.thoughtweave.data.ai

import io.wit117.thoughtweave.data.ai.language.LanguageProfiles
import io.wit117.thoughtweave.domain.usecase.compareKeyFacts
import io.wit117.thoughtweave.data.ai.parse.readKeyFacts
import io.wit117.thoughtweave.domain.model.AppLanguages
import java.io.File
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the app's key-fact reader on the summary benchmark (tools/summary-bench) and writes
 * `app/build/summary-bench/keyfacts.tsv` for `keyfacts_report.py`. Skipped when the benchmark is absent.
 * Rows: result file, set, kind (source, missing, added), fact type, wording.
 */
class KeyFactBenchExport {
    /** Unit tests run with the app module as the working directory. */
    private val bench = File("../tools/summary-bench")
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun export() {
        val setsFile = File(bench, "sets.json")
        assumeTrue("summary benchmark not found", setsFile.isFile)
        @Suppress("UNCHECKED_CAST")
        val sets = (MiniJson.parse(setsFile.readText()) as Map<String, Any?>)["sets"] as List<Map<String, Any?>>
        val byId = sets.associateBy { it["id"] as String }
        val rows = mutableListOf("result\tset\tkind\ttype\twording")

        fun sourceOf(set: Map<String, Any?>): String {
            @Suppress("UNCHECKED_CAST")
            val memos = set["memos"] as List<Map<String, Any?>>
            return memos.joinToString("\n") { "${it["title"]}\n${it["content"]}" }
        }

        fun profileOf(set: Map<String, Any?>) =
            LanguageProfiles.forLanguage(checkNotNull(AppLanguages.forTag(set["language"] as String)))

        for (set in sets) {
            for (mention in readKeyFacts(sourceOf(set), today, profileOf(set))) {
                rows += listOf("-", set["id"], "source", mention.fact::class.simpleName, mention.wording).joinToString("\t")
            }
        }
        val results = File(bench, "results").listFiles { f -> f.name.startsWith("A-gemma") && f.extension == "json" }.orEmpty()
        for (file in results.sortedBy { it.name }) {
            @Suppress("UNCHECKED_CAST")
            val runs = (MiniJson.parse(file.readText()) as Map<String, Any?>)["runs"] as List<Map<String, Any?>>
            for (run in runs.distinctBy { it["set"] }) {
                val set = byId[run["set"] as String] ?: continue
                val profile = profileOf(set)
                val diff = compareKeyFacts(
                    readKeyFacts(sourceOf(set), today, profile),
                    readKeyFacts(run["output"] as String, today, profile),
                )
                for ((kind, mentions) in listOf("missing" to diff.missing, "added" to diff.added)) {
                    for (m in mentions) {
                        rows += listOf(file.name, set["id"], kind, m.fact::class.simpleName, m.wording).joinToString("\t")
                    }
                }
            }
        }
        val out = File("build/summary-bench/keyfacts.tsv")
        out.parentFile.mkdirs()
        out.writeText(rows.joinToString("\n", postfix = "\n"))
    }
}

/** Just enough JSON for the benchmark files, so the test needs no JSON library. */
private object MiniJson {
    fun parse(text: String): Any? = Parser(text).run { value().also { skip(); check(i == s.length) { "trailing data" } } }

    private class Parser(val s: String) {
        var i = 0

        fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skip()
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        fun literal(word: String, v: Any?): Any? {
            check(s.startsWith(word, i)) { "bad literal at $i" }
            i += word.length
            return v
        }

        fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }

        fun str(): String {
            val b = StringBuilder()
            i++
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val c = s[i]) {
                        'n' -> b.append('\n')
                        't' -> b.append('\t')
                        'r' -> b.append('\r')
                        'b' -> b.append('\b')
                        'f' -> b.append('\u000C')
                        'u' -> {
                            b.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> b.append(c)
                    }
                } else {
                    b.append(s[i])
                }
                i++
            }
            i++
            return b.toString()
        }

        fun arr(): List<Any?> {
            val out = mutableListOf<Any?>()
            i++
            skip()
            if (s[i] == ']') return out.also { i++ }
            while (true) {
                out += value()
                skip()
                if (s[i++] == ']') return out
            }
        }

        fun obj(): Map<String, Any?> {
            val out = linkedMapOf<String, Any?>()
            i++
            skip()
            if (s[i] == '}') return out.also { i++ }
            while (true) {
                skip()
                val key = str()
                skip()
                i++ // ':'
                out[key] = value()
                skip()
                if (s[i++] == '}') return out
            }
        }
    }
}
