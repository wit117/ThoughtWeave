package io.wit117.thoughtweave.data.ai.language

import io.wit117.thoughtweave.data.ai.engine.PromptExample
import io.wit117.thoughtweave.domain.model.AppLanguage
import io.wit117.thoughtweave.domain.model.KeyFact
import java.time.LocalDate
import java.time.LocalTime

/** One way of writing a date, and how to read a match relative to today. */
class DatePattern(val regex: Regex, val read: (match: MatchResult, today: LocalDate) -> LocalDate?)

class TimePattern(val regex: Regex, val read: (match: MatchResult) -> LocalTime?)

/** How a language writes dates and times. Needed for the language to be an AI input language. */
class DateRules(
    /** Written-out forms ("12월 3일", "December 3"), read whatever the input language is. */
    val dates: List<DatePattern>,
    /** Numeric forms whose order varies by locale ("12/3"), used only when this is the input language. */
    val numericDates: List<DatePattern>,
    val weekdays: List<DatePattern>,
    val times: List<TimePattern>,
    /** Words that join two dates or times into a range ("부터", "until"). */
    val rangeCue: Regex,
    /** How a year is attached to a date; group 1 is the year. Used to remove invented years. */
    val writtenYear: Regex,
)

/** A way of writing an amount or a count, and its value. */
class QuantityPattern(val regex: Regex, val read: (match: MatchResult) -> KeyFact?)

/**
 * How a language writes amounts and counts, for checking that a summary keeps them. Needed for the language to
 * be an AI input language. Patterns are tried in order; earlier ones win where matches overlap.
 */
class QuantityRules(
    /**
     * Rewrites number words as digits before reading ("three nights" → "3 nights", "두 개" → "2 개"), because
     * the model often switches between them. Only where the word is a number: "한우마을" stays as it is.
     */
    val normalizeNumbers: (String) -> String,
    /** Money with a currency: "15만원", "147만 3천원", "1,450 dollars". Symbols ("$40", "€60") are shared. */
    val amounts: List<QuantityPattern>,
    /** A number with a unit: "18명", "시안 3개", "3 nights". Percentages are shared. */
    val counts: List<QuantityPattern>,
)

/**
 * The prompts for one output language. [summaryExample] must have the same content in every
 * language, because a translation request pairs one language's example input with another's output.
 */
class PromptPack(
    val summarySystem: String,
    val summaryExample: PromptExample,
    /** Label before each memo, "Memo" / "메모". */
    val memoLabel: String,
    val titleInstruction: String,
    /** Heading before the memo in the title prompt, "Memo:" / "메모:". */
    val memoHeading: String,
    /** Added to the instruction when translating: "write only in this language". */
    val writeOnlyInThisLanguage: String,
    /** Heading of the code-built list of every item from the memos, after the AI text. */
    val sourceItemsHeading: String,
)

/**
 * Everything the AI features need for one language. To add a language: add it to `AppLanguages`,
 * create a profile like [EnglishProfile], register it in [LanguageProfiles], and follow
 * docs/ADDING_A_LANGUAGE.md.
 */
class LanguageProfile(
    val language: AppLanguage,
    /** Whether a character belongs to this language's script, for Auto detection. */
    val isScriptChar: (Char) -> Boolean,
    /** Latin-script languages cannot be told apart by script alone. */
    val usesLatinScript: Boolean,
    /** Null: not offered as an AI input language. */
    val dateRules: DateRules?,
    /** Required with [dateRules]: amounts and counts, for the summary check. */
    val quantityRules: QuantityRules?,
    /** Null: not offered as an AI output language. */
    val prompts: PromptPack?,
)
