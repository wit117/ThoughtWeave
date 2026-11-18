package io.wit117.thoughtweave.domain.usecase

import io.wit117.thoughtweave.domain.model.AppLanguage
import io.wit117.thoughtweave.domain.model.KeyFact
import io.wit117.thoughtweave.domain.model.KeyFactMention
import io.wit117.thoughtweave.domain.model.Memo
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Reads the dates, times, amounts, counts and long numbers written in a text. */
interface TextFactReader {
    fun factsIn(text: String, language: AppLanguage, today: LocalDate): List<KeyFactMention>
}

/**
 * Facts in the sources that the summary lacks, and facts in the summary that no source has, each listed once
 * in the wording where it was found (the source's for [missing], the summary's for [added]).
 */
data class SummaryFactCheck(val missing: List<KeyFactMention>, val added: List<KeyFactMention>) {
    val hasProblems: Boolean get() = missing.isNotEmpty() || added.isNotEmpty()
}

/** The number in a count or code, so "KE 081 departs" and "KE081 leaves" can be paired by number. */
private fun numberOf(fact: KeyFact): BigDecimal? = when (fact) {
    is KeyFact.Count -> fact.value
    is KeyFact.Code -> fact.digits.toBigDecimal().stripTrailingZeros()
    else -> null
}

/**
 * Facts match exactly first. A count or code left over on one side then matches a leftover count or code with
 * the same number on the other, because the word read as a unit may be reworded ("departs" / "leaves"). Only
 * leftovers pair up, so "시안 3개" dropped while "3층" stays is still reported. Dates, times and amounts must
 * match exactly: a changed currency ("600,000 won" → "$600,000") is a change.
 */
fun compareKeyFacts(source: List<KeyFactMention>, summary: List<KeyFactMention>): SummaryFactCheck {
    val sourceFacts = source.map { it.fact }.toSet()
    val summaryFacts = summary.map { it.fact }.toSet()
    val leftSource = source.filter { it.fact !in summaryFacts }.distinctBy { it.fact }
    val leftSummary = summary.filter { it.fact !in sourceFacts }.distinctBy { it.fact }
    fun pairedIn(others: List<KeyFactMention>, m: KeyFactMention): Boolean {
        val n = numberOf(m.fact) ?: return false
        return others.any { numberOf(it.fact) == n }
    }
    return SummaryFactCheck(
        missing = leftSource.filterNot { pairedIn(leftSummary, it) },
        added = leftSummary.filterNot { pairedIn(leftSource, it) },
    )
}

/**
 * The model drops or rewrites dates, times and numbers ("12.2" becomes "12일 후", "오후 3시" disappears, "15만원"
 * is left out; CLAUDE.md, Inference), so the summary's key facts are compared with the sources' by value:
 * "12.2" and "12월 2일", or "15만원" and "150,000원", match.
 */
class CheckSummaryFactsUseCase @Inject constructor(
    private val reader: TextFactReader,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    operator fun invoke(sources: List<Memo>, summary: String, language: AppLanguage): SummaryFactCheck {
        val today = LocalDate.now(clock.withZone(zone))
        val sourceText = sources.joinToString("\n") { it.title + "\n" + it.content }
        return compareKeyFacts(reader.factsIn(sourceText, language, today), reader.factsIn(summary, language, today))
    }
}
