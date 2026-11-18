package io.wit117.thoughtweave.data.ai.parse

import io.wit117.thoughtweave.data.ai.language.EnglishProfile
import io.wit117.thoughtweave.data.ai.language.LanguageProfile
import io.wit117.thoughtweave.data.ai.language.QuantityPattern
import io.wit117.thoughtweave.domain.model.KeyFact
import io.wit117.thoughtweave.domain.model.KeyFactMention
import java.math.BigDecimal
import java.time.LocalDate

/** "1,450", "4900", "2.5". */
internal const val NUMBER = "\\d[\\d,]*(?:\\.\\d+)?"

/** Parses [NUMBER]; commas are thousands separators. */
internal fun decimalOf(text: String): BigDecimal? = text.replace(",", "").toBigDecimalOrNull()?.stripTrailingZeros()

private val SYMBOL_CURRENCIES = mapOf('$' to "USD", '€' to "EUR", '£' to "GBP", '₩' to "KRW", '¥' to "JPY")

/** "$40", "€ 60", "₩15,000": the same in every language. */
private val SYMBOL_AMOUNT = QuantityPattern(Regex("([$€£₩¥])\\s*($NUMBER)")) { m ->
    decimalOf(m.groupValues[2])?.let { KeyFact.Amount(it, SYMBOL_CURRENCIES.getValue(m.groupValues[1][0])) }
}

private val PERCENT = QuantityPattern(
    Regex("(?<![\\d.,])(\\d+(?:\\.\\d+)?)\\s*(?:%|퍼센트|percent\\b)", RegexOption.IGNORE_CASE),
) { m -> decimalOf(m.groupValues[1])?.let { KeyFact.Count(it, "%") } }

/**
 * Three or more digits with no unit ("LH 713", "555-0142", "6123 4455 9087"), compared digit for digit. Not part
 * of a longer number ("600,000", "1.25"); four-digit years are left to the date reader.
 */
private val CODE = Regex("(?<![\\d.,:/])\\d{3,}(?:[ -]\\d{3,})*(?![\\d:/])(?![.,]\\d)")
private val YEAR_ONLY = Regex("(?:19|20)\\d{2}")

private fun <T : Any> Regex.mentions(text: String, read: (MatchResult) -> T?): List<Mention<T>> =
    findAll(text).mapNotNull { m -> read(m)?.let { Mention(m.range, it) } }.toList()

private fun List<QuantityPattern>.mentions(text: String): List<Mention<KeyFact>> =
    flatMap { pattern -> pattern.regex.mentions(text, pattern.read) }

/**
 * Every [KeyFact] written in [text]: dates and times as the schedule reader reads them, then amounts, counts and
 * codes in [input]'s forms. A span is read once, the most specific reading first, so "12월 5일" is a date and not
 * a count of days, and "15만원" is an amount and not a code.
 */
fun readKeyFacts(text: String, today: LocalDate, input: LanguageProfile): List<KeyFactMention> {
    val rules = (input.quantityRules ?: EnglishProfile.quantityRules)!!
    val normalized = rules.normalizeNumbers(text)
    val ordered: List<Mention<KeyFact>> =
        dateMentionsIn(normalized, today, input).map { Mention<KeyFact>(it.range, KeyFact.Date(it.value)) } +
            timeMentionsIn(normalized).map { Mention<KeyFact>(it.range, KeyFact.Time(it.value.hour % 12, it.value.minute)) } +
            (rules.amounts + SYMBOL_AMOUNT).mentions(normalized) +
            (listOf(PERCENT) + rules.counts).mentions(normalized) +
            CODE.mentions<KeyFact>(normalized) { m ->
                m.value.filter(Char::isDigit).takeUnless { YEAR_ONLY.matches(it) }?.let { KeyFact.Code(it) }
            }
    return withoutOverlaps(ordered).map { KeyFactMention(it.value, normalized.substring(it.range).trim()) }
}
