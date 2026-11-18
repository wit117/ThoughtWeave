package io.wit117.thoughtweave.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A fact code can read from text without a model, compared by value rather than wording, so "15만원" and
 * "150,000원" or "6 pm" and "18:00" are the same fact.
 */
sealed interface KeyFact {
    data class Date(val date: LocalDate) : KeyFact

    /**
     * A clock time on the 12-hour dial. Memos often omit 오전/오후 or am/pm ("저녁 7시" is read as 7:00), so
     * "7시" and "19:00" are one fact; the price is that a changed half of the day goes unnoticed.
     */
    data class Time(val hourOfHalfDay: Int, val minute: Int) : KeyFact

    /** [currency]: ISO 4217 code ("KRW", "USD", "EUR"). */
    data class Amount(val value: BigDecimal, val currency: String) : KeyFact

    /** A number with its unit: "18명", "3 nights", "23%". [unit] is the language's canonical form ("night"). */
    data class Count(val value: BigDecimal, val unit: String) : KeyFact

    /** A number of three or more digits with no unit, digits only: "LH 713", "555-0142", a tracking number. */
    data class Code(val digits: String) : KeyFact
}

/** A [KeyFact] and how it was written, for showing it to the user. */
data class KeyFactMention(val fact: KeyFact, val wording: String)
