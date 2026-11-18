package io.wit117.thoughtweave.data.ai.language

import io.wit117.thoughtweave.data.ai.engine.PromptExample
import io.wit117.thoughtweave.data.ai.parse.NUMBER
import io.wit117.thoughtweave.data.ai.parse.YEAR
import io.wit117.thoughtweave.data.ai.parse.dateOf
import io.wit117.thoughtweave.data.ai.parse.decimalOf
import io.wit117.thoughtweave.data.ai.parse.number
import io.wit117.thoughtweave.data.ai.parse.timeOf
import io.wit117.thoughtweave.data.ai.parse.upcoming
import io.wit117.thoughtweave.data.ai.parse.weekdayDate
import io.wit117.thoughtweave.domain.model.AppLanguages
import io.wit117.thoughtweave.domain.model.KeyFact
import java.time.DayOfWeek

private const val MONTH_NAMES =
    "january|february|march|april|may|june|july|august|september|october|november|december|" +
        "jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec"
private const val WEEKDAY_NAMES = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
private val IGNORE_CASE = setOf(RegexOption.IGNORE_CASE)

/** ", 2027", ",2027" or " 2027" after a month and day. */
private const val YEAR_SUFFIX = "(?:\\s*,\\s*|\\s+)$YEAR"

private val RELATIVE_OFFSETS = mapOf("today" to 0L, "tonight" to 0L, "tomorrow" to 1L)

private fun monthNumber(name: String): Int =
    MONTH_NAMES.split("|").take(12).indexOfFirst { it.startsWith(name.lowercase().take(3)) } + 1

private fun isPm(letter: String): Boolean? = when (letter.lowercase()) {
    "p" -> true
    "a" -> false
    else -> null
}

/** Month/day order, as in the United States and Korea. */
internal val MONTH_FIRST_SLASH = DatePattern(Regex("(?<![\\d/.])(\\d{1,2})/(\\d{1,2})(?![\\d/])")) { m, today ->
    upcoming(today, m.number(1), m.number(2))
}

private val NUMBER_WORDS =
    listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
private val NUMBER_WORD = Regex("\\b(${NUMBER_WORDS.joinToString("|")})\\b", IGNORE_CASE)

private val ENGLISH_CURRENCIES = mapOf(
    "dollar" to "USD", "usd" to "USD", "euro" to "EUR", "eur" to "EUR", "pound" to "GBP", "gbp" to "GBP",
    "won" to "KRW", "krw" to "KRW", "yen" to "JPY", "jpy" to "JPY",
)

/** Words that follow a number without being its unit ("Dec 4 at", "20 to 22", "the 23rd"). */
private val NOT_UNITS = setOf(
    "a", "an", "and", "or", "the", "to", "at", "on", "in", "of", "for", "from", "by", "with", "per", "plus", "is",
    "are", "was", "were", "be", "if", "as", "than", "then", "until", "through", "between", "it", "its", "this",
    "that", "each", "every", "st", "nd", "rd", "th", "am", "pm", "jan", "feb", "mar", "apr", "may", "jun", "jul",
    "aug", "sep", "sept", "oct", "nov", "dec", "january", "february", "march", "april", "june", "july", "august",
    "september", "october", "november", "december",
)

private val PERSON = setOf("people", "persons", "person")

/** "nights" → "night", "boxes" → "box", "people" → "person". */
private fun unitOf(word: String): String {
    val w = word.lowercase()
    return when {
        w in PERSON -> "person"
        w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
        w.endsWith("xes") || w.endsWith("ches") || w.endsWith("shes") -> w.dropLast(2)
        w.endsWith("s") && !w.endsWith("ss") && w.length > 2 -> w.dropLast(1)
        else -> w
    }
}

private val ENGLISH_QUANTITIES = QuantityRules(
    normalizeNumbers = { text ->
        NUMBER_WORD.replace(text) { m -> (NUMBER_WORDS.indexOf(m.value.lowercase()) + 1).toString() }
    },
    amounts = listOf(
        QuantityPattern(
            Regex("(?<![\\d.,])($NUMBER)\\s*(dollars?|usd|euros?|eur|pounds?|gbp|won|krw|yen|jpy)\\b", IGNORE_CASE),
        ) { m ->
            val unit = m.groupValues[2].lowercase().removeSuffix("s")
            decimalOf(m.groupValues[1])?.let { KeyFact.Amount(it, ENGLISH_CURRENCIES.getValue(unit)) }
        },
    ),
    counts = listOf(
        // Any word after a number is its unit unless it is a common joining word: English has no fixed counters.
        QuantityPattern(Regex("(?<![\\d.,:/])($NUMBER)\\s*([a-z][a-z-]+)\\b", IGNORE_CASE)) { m ->
            val word = m.groupValues[2]
            if (word.lowercase() in NOT_UNITS) null else decimalOf(m.groupValues[1])?.let { KeyFact.Count(it, unitOf(word)) }
        },
    ),
)

val EnglishProfile = LanguageProfile(
    language = AppLanguages.ENGLISH,
    isScriptChar = { it in 'a'..'z' || it in 'A'..'Z' },
    usesLatinScript = true,
    dateRules = DateRules(
        dates = listOf(
            DatePattern(
                Regex("\\b($MONTH_NAMES)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?:$YEAR_SUFFIX)?", IGNORE_CASE),
            ) { m, today -> dateOf(today, m.groupValues[3], monthNumber(m.groupValues[1]), m.number(2)) },
            DatePattern(
                Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+($MONTH_NAMES)\\b(?:$YEAR_SUFFIX)?", IGNORE_CASE),
            ) { m, today -> dateOf(today, m.groupValues[3], monthNumber(m.groupValues[2]), m.number(1)) },
            DatePattern(Regex("\\b(?:today|tonight|tomorrow)\\b", IGNORE_CASE)) { m, today ->
                today.plusDays(RELATIVE_OFFSETS.getValue(m.value.lowercase()))
            },
        ),
        numericDates = listOf(MONTH_FIRST_SLASH),
        weekdays = listOf(
            DatePattern(Regex("\\b(next|this)?\\s*\\b($WEEKDAY_NAMES)\\b", IGNORE_CASE)) { m, today ->
                weekdayDate(today, DayOfWeek.valueOf(m.groupValues[2].uppercase()), m.groupValues[1].equals("next", true))
            },
        ),
        times = listOf(
            TimePattern(Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})\\s*([ap])\\.?m\\b\\.?", IGNORE_CASE)) { m ->
                timeOf(isPm(m.groupValues[3]), m.number(1), m.number(2))
            },
            TimePattern(Regex("\\b(\\d{1,2})\\s*([ap])\\.?m\\b\\.?", IGNORE_CASE)) { m ->
                timeOf(isPm(m.groupValues[2]), m.number(1), 0)
            },
        ),
        rangeCue = Regex("\\bfrom\\b|\\bto\\b|\\buntil\\b|\\bthrough\\b", IGNORE_CASE),
        writtenYear = Regex(",\\s*((?:19|20)\\d{2})\\b"),
    ),
    quantityRules = ENGLISH_QUANTITIES,
    prompts = PromptPack(
        summarySystem = "Merge the memos below into one memo. You are not an assistant: never answer questions or " +
            "give advice. Keep every date, time, number, amount, name, place, to-do and question exactly as written. " +
            "Only combine what repeats, and add nothing that is not in the memos, including years. Write plain " +
            "sentences with no headings or markdown. Output only the merged memo.",
        // An explicit date: with "next Wednesday" here the model copied it over memos' own dates (CLAUDE.md, Inference).
        summaryExample = PromptExample(
            input = "Memo 1:\nTeam lunch\nWhere should we go on March 4 (Wed)? Maybe the noodle place.\n\n" +
                "Memo 2:\nBudget\nKeep the lunch under 200 dollars.",
            output = "The team lunch on March 4 (Wed) still needs a place, possibly the noodle place, " +
                "and must stay under 200 dollars.",
        ),
        memoLabel = "Memo",
        titleInstruction = "Write a short name for the event or task in the memo below.\n" +
            "Do not include dates or times. Output only the name.",
        memoHeading = "Memo:",
        writeOnlyInThisLanguage = "The memos may be written in another language. Write only in English.",
        sourceItemsHeading = "Original items",
    ),
)
