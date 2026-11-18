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
import java.math.BigDecimal
import java.time.DayOfWeek

private const val WEEKDAYS = "월화수목금토일"
private val RELATIVE_OFFSETS = mapOf("오늘" to 0L, "내일" to 1L, "모레" to 2L, "글피" to 3L)

private const val LATIN_UNITS = "(?:kg|km|cm|mm|ml|g|m|l)(?![a-zA-Z])"

/**
 * "11.25", "12.2": month.day, common in Korean writing. Not read when it looks like a decimal: part of a
 * longer number or version ("1.2.3", "v1.2"), or followed by a unit. Single-syllable Korean units count
 * only when attached ("3.5배", "4.5점"), because after a space they start ordinary words ("12.2 회의",
 * "3.1 개강"). A bare decimal with no unit (a score such as "4.5") can still be read as a date; the user
 * reviews it in the editor.
 */
private val MONTH_FIRST_DOT = DatePattern(
    Regex(
        "(?<![\\d.vV])(\\d{1,2})\\.(\\d{1,2})(?!\\d)(?!\\.\\d)" +
            "(?!(?:%|퍼센트|배|시간|분|초|원|만|억|천|점|개|명|번|회|도|°|℃|$LATIN_UNITS))" +
            "(?!\\s+(?:%|퍼센트|시간|$LATIN_UNITS))",
    ),
) { m, today -> upcoming(today, m.number(1), m.number(2)) }

/** Native numerals, only where a counter follows ("두 개", "한 판"), so "한우", "세제" and "네" stay. */
private val NATIVE_NUMBERS = listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")
private const val COUNTERS = "개월|개|명|박스|박|판|권|곳|군데|회|층|번|시간|주|인|실|분|살|병|잔|장|대|가지|켤레|벌"
private val NATIVE_COUNT = Regex("(?<![가-힣])(${NATIVE_NUMBERS.joinToString("|")})\\s*(?=(?:$COUNTERS))")

/** "147만 3천", "3억 2천만": digits with 억/만/천/백 multipliers, summed. */
private val KOREAN_MULTIPLIERS = mapOf(
    "억" to BigDecimal("100000000"), "천만" to BigDecimal("10000000"), "백만" to BigDecimal("1000000"),
    "만" to BigDecimal("10000"), "천" to BigDecimal("1000"), "백" to BigDecimal("100"),
)
private const val KOREAN_NUMBER_PART = "$NUMBER\\s*(?:억|천만|백만|만|천|백)?"
private val KOREAN_NUMBER_TOKEN = Regex("($NUMBER)\\s*(억|천만|백만|만|천|백)?")

private fun koreanNumber(text: String): BigDecimal? {
    var sum = BigDecimal.ZERO
    for (m in KOREAN_NUMBER_TOKEN.findAll(text)) {
        val number = decimalOf(m.groupValues[1]) ?: return null
        sum += number * (KOREAN_MULTIPLIERS[m.groupValues[2]] ?: BigDecimal.ONE)
    }
    return sum.stripTrailingZeros()
}

private val KOREAN_CURRENCIES = mapOf("원" to "KRW", "달러" to "USD", "유로" to "EUR", "엔" to "JPY")

private val KOREAN_QUANTITIES = QuantityRules(
    normalizeNumbers = { text ->
        NATIVE_COUNT.replace(text) { m -> (NATIVE_NUMBERS.indexOf(m.groupValues[1]) + 1).toString() }
    },
    amounts = listOf(
        QuantityPattern(
            Regex("(?<![\\d.,])($KOREAN_NUMBER_PART(?:\\s*$KOREAN_NUMBER_PART)*)\\s*(원|달러|유로|엔)"),
        ) { m -> koreanNumber(m.groupValues[1])?.let { KeyFact.Amount(it, KOREAN_CURRENCIES.getValue(m.groupValues[2])) } },
    ),
    counts = listOf(
        // "분기" and "인원" are words, not counts; "일" is left out because a day alone ("26일") is a date fragment.
        QuantityPattern(Regex("(?<![\\d.,])($NUMBER)\\s*($COUNTERS)(?!기|원|터|상|하)")) { m ->
            decimalOf(m.groupValues[1])?.let { KeyFact.Count(it, m.groupValues[2]) }
        },
    ),
)

private fun isPm(period: String): Boolean? = when (period) {
    "오후" -> true
    "오전" -> false
    else -> null
}

val KoreanProfile = LanguageProfile(
    language = AppLanguages.KOREAN,
    isScriptChar = { it in '가'..'힣' || it in 'ᄀ'..'ᇿ' || it in '㄰'..'㆏' },
    usesLatinScript = false,
    dateRules = DateRules(
        dates = listOf(
            DatePattern(Regex("(?:$YEAR\\s*년\\s*,?\\s*)?(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")) { m, today ->
                dateOf(today, m.groupValues[1], m.number(2), m.number(3))
            },
            DatePattern(Regex("오늘|내일|모레|글피")) { m, today ->
                today.plusDays(RELATIVE_OFFSETS.getValue(m.value))
            },
        ),
        numericDates = listOf(MONTH_FIRST_SLASH, MONTH_FIRST_DOT),
        weekdays = listOf(
            DatePattern(Regex("(다음\\s*주|이번\\s*주)?\\s*([월화수목금토일])요일")) { m, today ->
                val day = DayOfWeek.of(WEEKDAYS.indexOf(m.groupValues[2]) + 1)
                weekdayDate(today, day, m.groupValues[1].startsWith("다음"))
            },
        ),
        times = listOf(
            TimePattern(Regex("(오전|오후)\\s*(?<![\\d:])(\\d{1,2}):(\\d{2})")) { m ->
                timeOf(isPm(m.groupValues[1]), m.number(2), m.number(3))
            },
            // Not "2시간", a duration.
            TimePattern(Regex("(오전|오후)?\\s*(\\d{1,2})\\s*시(?!간)(?:\\s*(\\d{1,2})\\s*분|\\s*(반))?")) { m ->
                val minute = if (m.groupValues[4].isNotEmpty()) 30 else m.groupValues[3].toIntOrNull() ?: 0
                timeOf(isPm(m.groupValues[1]), m.number(2), minute)
            },
        ),
        rangeCue = Regex("부터"),
        writtenYear = Regex("((?:19|20)\\d{2})년\\s*"),
    ),
    quantityRules = KOREAN_QUANTITIES,
    prompts = PromptPack(
        summarySystem = "아래 메모들을 하나의 메모로 합치세요. 비서가 아니므로 질문에 답하거나 조언하지 마세요. " +
            "모든 날짜, 시간, 숫자, 금액, 이름, 장소, 할 일, 질문을 빠짐없이 그대로 유지하세요. 겹치는 내용만 합치고, " +
            "메모에 없는 내용이나 연도는 추가하지 마세요. 제목이나 마크다운 없이 평범한 한국어 문장으로 결과만 출력하세요.",
        // An explicit date: with "다음 주 수요일" here the model copied it over memos' own dates (CLAUDE.md, Inference).
        summaryExample = PromptExample(
            input = "메모 1:\n팀 점심\n3월 4일(수)에 어디로 갈까? 국수집도 괜찮을 듯.\n\n" +
                "메모 2:\n예산\n점심 비용은 20만원 이내로.",
            output = "3월 4일(수) 팀 점심 장소는 아직 정해지지 않았고 국수집이 후보이며, 비용은 20만원 이내로 한다.",
        ),
        memoLabel = "메모",
        titleInstruction = "아래 메모에 있는 일정의 짧은 이름을 한국어로 지으세요.\n날짜와 시간은 넣지 말고 이름만 출력하세요.",
        memoHeading = "메모:",
        writeOnlyInThisLanguage = "메모가 다른 언어로 쓰여 있어도 반드시 한국어로만 작성하세요.",
        sourceItemsHeading = "원문 항목",
    ),
)
