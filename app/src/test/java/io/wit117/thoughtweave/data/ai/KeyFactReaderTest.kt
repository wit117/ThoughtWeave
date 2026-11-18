package io.wit117.thoughtweave.data.ai

import io.wit117.thoughtweave.data.ai.language.EnglishProfile
import io.wit117.thoughtweave.data.ai.language.KoreanProfile
import io.wit117.thoughtweave.data.ai.language.LanguageProfile
import io.wit117.thoughtweave.data.ai.parse.ScheduleDates
import io.wit117.thoughtweave.domain.usecase.compareKeyFacts
import io.wit117.thoughtweave.data.ai.parse.readKeyFacts
import io.wit117.thoughtweave.data.ai.parse.readScheduleDates
import io.wit117.thoughtweave.domain.model.KeyFact
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFactReaderTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun facts(text: String, profile: LanguageProfile) = readKeyFacts(text, today, profile).map { it.fact }.toSet()

    private fun amount(value: String, currency: String) = KeyFact.Amount(BigDecimal(value).stripTrailingZeros(), currency)

    private fun count(value: String, unit: String) = KeyFact.Count(BigDecimal(value).stripTrailingZeros(), unit)

    private fun same(source: String, summary: String, profile: LanguageProfile) =
        compareKeyFacts(readKeyFacts(source, today, profile), readKeyFacts(summary, today, profile))

    @Test
    fun koreanAmounts() {
        assertEquals(setOf(amount("150000", "KRW")), facts("예산은 1인당 15만원", KoreanProfile) - setOf(count("1", "인")))
        assertEquals(setOf(amount("1473000", "KRW")), facts("카드값 147만 3천원", KoreanProfile))
        assertEquals(setOf(amount("320000000", "KRW")), facts("매출 목표는 3억 2천만원", KoreanProfile))
        assertEquals(setOf(amount("4900", "KRW")), facts("월 4,900원", KoreanProfile))
        assertEquals(setOf(amount("40", "USD")), facts("예산 $40", KoreanProfile))
    }

    @Test
    fun koreanCounts() {
        assertEquals(setOf(count("18", "명")), facts("참석 인원은 18명이다.", KoreanProfile))
        assertEquals(setOf(count("2", "개")), facts("우유 두개", KoreanProfile))
        assertEquals(setOf(count("2", "시간")), facts("주차 지원 2시간", KoreanProfile))
        assertEquals(setOf(count("23", "%")), facts("서버 비용 23% 증가", KoreanProfile))
        assertEquals(setOf(count("3", "번")), facts("강남역 3번 출구", KoreanProfile))
    }

    @Test
    fun koreanWordsThatAreNotCounts() {
        assertEquals(emptySet<KeyFact>(), facts("1분기 매출", KoreanProfile))
        assertEquals(emptySet<KeyFact>(), facts("한우마을, 세제, 네 그래요", KoreanProfile))
        assertEquals(emptySet<KeyFact>(), facts("결제일이 26일", KoreanProfile))
    }

    @Test
    fun koreanDatesAndTimesStayDatesAndTimes() {
        assertEquals(
            setOf(KeyFact.Date(LocalDate.of(2026, 12, 5)), KeyFact.Time(3, 0)),
            facts("12월 5일 오후 3시", KoreanProfile),
        )
        assertEquals(setOf(KeyFact.Time(7, 10)), facts("오전 7시 10분", KoreanProfile))
    }

    @Test
    fun rangesThatWriteTheMonthOnce() {
        val jan = { d: Int -> KeyFact.Date(LocalDate.of(2027, 1, d)) }
        assertEquals(setOf(jan(20), jan(22)), facts("DevConf, January 20-22.", EnglishProfile))
        assertEquals(setOf(jan(3), jan(5)), facts("Jan 3 to 5 in Berlin", EnglishProfile))
        val dec = { d: Int -> KeyFact.Date(LocalDate.of(2026, 12, d)) }
        assertEquals(setOf(dec(1), dec(7)), facts("베타는 12월 1일~7일까지", KoreanProfile))
        assertEquals(setOf(dec(4), count("3", "person")), facts("Dec 4 - 3 people", EnglishProfile))
        assertEquals(setOf(dec(5), count("3", "명")), facts("12월 5일 - 3명", KoreanProfile))
    }

    @Test
    fun aRangeEndIsTheScheduleDeadline() {
        assertEquals(
            ScheduleDates(LocalDate.of(2027, 1, 22), startDate = LocalDate.of(2027, 1, 20)),
            readScheduleDates("DevConf January 20-22", today, EnglishProfile),
        )
    }

    @Test
    fun englishAmountsAndCounts() {
        assertEquals(setOf(amount("1450", "USD")), facts("Rent of 1,450 dollars", EnglishProfile))
        assertEquals(setOf(amount("60", "EUR")), facts("Per diem is 60 euros a day", EnglishProfile))
        assertEquals(setOf(count("3", "night")), facts("Hotel for three nights", EnglishProfile))
        assertEquals(setOf(count("6", "egg")), facts("6 eggs", EnglishProfile))
        assertEquals(setOf(count("13", "mm")), facts("a 13 mm wrench", EnglishProfile))
    }

    @Test
    fun englishNumbersThatAreNotCounts() {
        assertEquals(setOf(KeyFact.Date(LocalDate.of(2026, 12, 4))), facts("on Dec 4 at noon", EnglishProfile))
        assertEquals(setOf(KeyFact.Time(6, 0)), facts("dinner at six pm", EnglishProfile))
        assertEquals(setOf(KeyFact.Code("713")), facts("Outbound LH 713 on Jan", EnglishProfile))
    }

    @Test
    fun codesAreComparedDigitForDigit() {
        assertEquals(setOf(KeyFact.Code("612344559087")), facts("송장번호 6123 4455 9087", KoreanProfile))
        assertEquals(setOf(KeyFact.Code("5550142")), facts("Call 555-0142", EnglishProfile))
        assertTrue(facts("2027년 1월 15일", KoreanProfile).none { it is KeyFact.Code })
    }

    /** Written after the rules, from rewordings the model makes; none may raise a warning. */
    @Test
    fun rewordingsAreTheSameFact() {
        val cases = listOf(
            Triple("저녁 6시 반 식사", "식사는 18:30", KoreanProfile),
            Triple("우유 2개, 계란 한 판", "우유 두 개와 계란 1판", KoreanProfile),
            Triple("관리비 18만 7천원", "관리비 187,000원", KoreanProfile),
            Triple("견적 120만원", "견적은 1,200,000원", KoreanProfile),
            Triple("12.10 오전 8시 검진", "12월 10일 8시 검진", KoreanProfile),
            Triple("Quote was 280 dollars", "Quote was $280", EnglishProfile),
            Triple("Hotel booked for 3 nights", "three nights at the hotel", EnglishProfile),
            Triple("Vet on Dec 4 at 10:15 am", "vet visit December 4, 10:15", EnglishProfile),
        )
        for ((source, summary, profile) in cases) {
            val diff = same(source, summary, profile)
            assertTrue("\"$source\" vs \"$summary\": $diff", !diff.hasProblems)
        }
    }

    /**
     * Held out (2026-11-18): written after the rules were tuned on the benchmark and run once, to see whether
     * faithful rewordings raise warnings the benchmark did not show.
     */
    @Test
    fun heldOutRewordingsAreTheSameFact() {
        val cases = listOf(
            Triple(
                "회의는 1월 8일(금) 오후 2시 30분, 참석자 7명. 다과비 3만 5천원.",
                "1월 8일(금) 오후 2시 반 회의에 7명이 참석하며 다과비는 35,000원이다.", KoreanProfile,
            ),
            Triple("택시비 1만 2천원, 지하철 1,450원. 왕복 2번.", "택시비는 12,000원, 지하철은 1450원이고 왕복 두 번이다.", KoreanProfile),
            Triple("프로젝트 기간 3개월, 인원 4명, 진척률 60%.", "프로젝트는 3개월 동안 네 명이 진행하며 진척률은 60퍼센트이다.", KoreanProfile),
            Triple("다음 주 화요일 저녁 7시에 3명 저녁 예약.", "다음 주 화요일 오후 7시, 세 명 저녁 예약.", KoreanProfile),
            Triple(
                "Team offsite on March 3 at 9:30 am. 12 people, budget 2,400 dollars.",
                "The team offsite is on March 3 at 9:30 for twelve people with a $2,400 budget.", EnglishProfile,
            ),
            Triple(
                "Order 3 boxes of paper and 2 toner cartridges by Friday.",
                "Order three boxes of paper and two toner cartridges by Friday.", EnglishProfile,
            ),
            Triple("Flight KE 081 departs 11:50 pm on Dec 30.", "Flight KE081 leaves Dec 30 at 11:50 p.m.", EnglishProfile),
        )
        for ((source, summary, profile) in cases) {
            val diff = same(source, summary, profile)
            assertTrue("\"$source\" vs \"$summary\": $diff", !diff.hasProblems)
        }
    }

    @Test
    fun changesAreReported() {
        val dropped = same("결제일 12월 26일, 예산 15만원", "결제일이 26일", KoreanProfile)
        assertEquals(listOf("12월 26일", "15만원"), dropped.missing.map { it.wording })
        // A dropped count is reported even when the same number survives in another, matched fact.
        val count = same("3층 회의실, 시안 3개 준비", "3층 회의실에서 시안 준비", KoreanProfile)
        assertEquals(listOf("3개"), count.missing.map { it.wording })
        val currency = same("Total budget 600,000 won", "total budget: $600,000", EnglishProfile)
        assertEquals(listOf("600,000 won"), currency.missing.map { it.wording })
        assertEquals(listOf("$600,000"), currency.added.map { it.wording })
    }
}
