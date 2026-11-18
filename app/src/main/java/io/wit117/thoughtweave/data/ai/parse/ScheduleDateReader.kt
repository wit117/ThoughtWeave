package io.wit117.thoughtweave.data.ai.parse

import io.wit117.thoughtweave.data.ai.language.EnglishProfile
import io.wit117.thoughtweave.data.ai.language.LanguageProfile
import io.wit117.thoughtweave.data.ai.language.LanguageProfiles
import io.wit117.thoughtweave.data.ai.language.DatePattern
import io.wit117.thoughtweave.data.ai.language.TimePattern
import java.time.LocalDate
import java.time.LocalTime

/** Dates and times written in a memo. A null time means none was written. */
data class ScheduleDates(
    val deadlineDate: LocalDate,
    val deadlineTime: LocalTime? = null,
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
)

/** Year first is unambiguous in every language; commas are allowed only because the year has four digits. */
private val FULL_DATE = DatePattern(
    Regex(
        "(?<!\\d)((?:19|20)\\d{2})\\s*(?:년\\s*,?|[./,-])\\s*(\\d{1,2})\\s*(?:월|[./,-])\\s*(\\d{1,2})(?!\\d)\\s*일?",
    ),
) { m, _ -> safeDate(m.number(1), m.number(2), m.number(3)) }

/** 24-hour "15:00", after every language's own time forms. */
private val PLAIN_COLON_TIME = TimePattern(Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})")) { m ->
    timeOf(null, m.number(1), m.number(2))
}

private val SHARED_RANGE_CUE = Regex("~|–|-")

/** Only commas, spaces or brackets between a weekday and its date, as in "목요일, 12월 3일". */
private val ATTACHED = Regex("[\\s,()]*")

/**
 * The end of a range that writes the month once: "January 20-22", "Jan 3 to 5", "12월 1일~7일". The day must
 * follow the date directly and not be a count ("Dec 4 - 3 people", "12월 5일 - 3명").
 */
private val RANGE_END = Regex(
    "\\s*(?:~|–|-|\\bto\\b|\\bthrough\\b)\\s*(\\d{1,2})" +
        "(?:\\s*일|(?:st|nd|rd|th)?(?![\\d/:])(?![.,]\\d)(?!\\s*(?:[가-힣]|%|(?:people|persons?|nights?|days?|hours?|am|pm)\\b)))",
    RegexOption.IGNORE_CASE,
)

private fun rangeEnds(text: String, explicit: List<Mention<LocalDate>>): List<Mention<LocalDate>> =
    explicit.mapNotNull { start ->
        val m = RANGE_END.find(text, start.range.last + 1)?.takeIf { it.range.first == start.range.last + 1 }
            ?: return@mapNotNull null
        val day = m.groupValues[1].toInt()
        if (day <= start.value.dayOfMonth) return@mapNotNull null
        // The mention is the day alone, so the "-" or "to" before it reads as the range cue.
        safeDate(start.value.year, start.value.monthValue, day)?.let { Mention(m.groups[1]!!.range, it) }
    }

private fun profilesWithRules() = LanguageProfiles.all.mapNotNull { it.dateRules }

private fun dateMentions(text: String, today: LocalDate, input: LanguageProfile): List<Mention<LocalDate>> {
    fun find(patterns: List<DatePattern>) = patterns.flatMap { pattern ->
        pattern.regex.findAll(text).mapNotNull { match ->
            pattern.read(match, today)?.let { Mention(match.range, it) }
        }.toList()
    }

    val rules = profilesWithRules()
    val numeric = (input.dateRules ?: EnglishProfile.dateRules)?.numericDates.orEmpty()
    // Most specific first, so "2026/12/3" is not also read as 12/3.
    val written = withoutOverlaps(find(listOf(FULL_DATE) + rules.flatMap { it.dates } + numeric))
    val explicit = withoutOverlaps(written + rangeEnds(text, written))
    val weekdays = find(rules.flatMap { it.weekdays })
    // A weekday written next to a date describes that date, and the explicit date wins even if they disagree.
    val standaloneWeekdays = weekdays.filter { weekday -> explicit.none { isAttached(text, weekday, it) } }
    return withoutOverlaps(explicit + standaloneWeekdays)
}

private fun <T> isAttached(text: String, a: Mention<T>, b: Mention<T>): Boolean {
    val (first, second) = if (a.range.first < b.range.first) a to b else b to a
    if (first.range.last >= second.range.first) return true
    return ATTACHED.matches(text.substring(first.range.last + 1, second.range.first))
}

private fun timeMentions(text: String): List<Mention<LocalTime>> {
    val patterns = profilesWithRules().flatMap { it.times } + PLAIN_COLON_TIME
    return withoutOverlaps(
        patterns.flatMap { pattern ->
            pattern.regex.findAll(text).mapNotNull { match ->
                pattern.read(match)?.let { Mention(match.range, it) }
            }.toList()
        },
    )
}

private fun <T> isRange(text: String, first: Mention<T>, second: Mention<T>): Boolean {
    if (first.range.last >= second.range.first) return false
    val between = text.substring(first.range.last + 1, second.range.first)
    return SHARED_RANGE_CUE.containsMatchIn(between) ||
        profilesWithRules().any { it.rangeCue.containsMatchIn(between) }
}

/**
 * Reads the schedule's dates from a memo. Timestamps come only from what is written, never from the
 * model, and a memo with no readable date yields null. Written-out forms of every language are read;
 * numeric order ("12/3") follows the [input] language.
 */
fun readScheduleDates(text: String, today: LocalDate, input: LanguageProfile = EnglishProfile): ScheduleDates? {
    val dates = dateMentions(text, today, input)
    val times = timeMentions(text)
    val first = dates.firstOrNull() ?: return null

    val dateRange = dates.size >= 2 && isRange(text, dates[0], dates[1]) && dates[1].value >= dates[0].value
    val timeRange = times.size >= 2 && isRange(text, times[0], times[1])
    val hasStartTime = timeRange && (dateRange || times[1].value >= times[0].value)

    return ScheduleDates(
        deadlineDate = if (dateRange) dates[1].value else first.value,
        deadlineTime = (if (hasStartTime) times[1] else times.firstOrNull())?.value,
        startDate = if (dateRange || hasStartTime) first.value else null,
        startTime = if (hasStartTime) times[0].value else null,
    )
}

/** Where each date is written in [text], for reading other facts around them. */
internal fun dateMentionsIn(text: String, today: LocalDate, input: LanguageProfile): List<Mention<LocalDate>> =
    dateMentions(text, today, input)

/** Where each time is written in [text], in every language's forms. */
internal fun timeMentionsIn(text: String): List<Mention<LocalTime>> = timeMentions(text)

/** Every date written in [text], including weekdays and relative days resolved against [today]. */
fun readAllDates(text: String, today: LocalDate, input: LanguageProfile = EnglishProfile): Set<LocalDate> =
    dateMentions(text, today, input).map { it.value }.toSet()

/** Cleans the model's title reply; null if nothing usable remains. */
fun cleanTitle(reply: String): String? = reply.lineSequence()
    .map { it.trim().trim('*', '#', '"', '\'', '`', ' ') }
    .firstOrNull { it.isNotEmpty() }
    ?.take(MAX_TITLE_LENGTH)

private const val MAX_TITLE_LENGTH = 60
