package io.wit117.thoughtweave.data.ai.language

import io.wit117.thoughtweave.domain.model.KeyFact
import java.math.BigDecimal

private fun amount(value: String, currency: String) = KeyFact.Amount(BigDecimal(value).stripTrailingZeros(), currency)

private fun count(value: String, unit: String) = KeyFact.Count(BigDecimal(value).stripTrailingZeros(), unit)

/**
 * Amounts and counts each AI input language must read (docs/ADDING_A_LANGUAGE.md, step 5): at least an amount
 * with a currency word, a large amount, a count with a unit, a count written as a word, and a percentage.
 */
val QUANTITY_SAMPLES: Map<String, List<Pair<String, Set<KeyFact>>>> = mapOf(
    "en" to listOf(
        "Rent of 1,450 dollars" to setOf(amount("1450", "USD")),
        "Budget 600,000 won" to setOf(amount("600000", "KRW")),
        "Hotel for 4 nights" to setOf(count("4", "night")),
        "Bring two adapters" to setOf(count("2", "adapter")),
        "Costs rose 23%" to setOf(count("23", "%")),
    ),
    "ko" to listOf(
        "점심 비용 20만원" to setOf(amount("200000", "KRW")),
        "매출 목표 3억 2천만원" to setOf(amount("320000000", "KRW")),
        "참석 인원 18명" to setOf(count("18", "명")),
        "우유 두 개" to setOf(count("2", "개")),
        "서버 비용 23% 증가" to setOf(count("23", "%")),
    ),
)
