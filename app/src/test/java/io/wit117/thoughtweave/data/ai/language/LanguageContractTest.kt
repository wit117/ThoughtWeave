package io.wit117.thoughtweave.data.ai.language

import io.wit117.thoughtweave.data.ai.parse.readKeyFacts
import io.wit117.thoughtweave.data.ai.parse.readScheduleDates
import io.wit117.thoughtweave.domain.model.AppLanguages
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that every registered language has all the pieces it needs (docs/ADDING_A_LANGUAGE.md).
 * Fails with a message naming the language and the missing piece.
 */
class LanguageContractTest {
    /** Unit tests run with the app module as the working directory. */
    private val res = File("src/main/res")
    private val assets = File("src/main/assets")

    private val defaultTag = File(res, "resources.properties").readLines()
        .first { it.startsWith("unqualifiedResLocale=") }.substringAfter('=').substringBefore('-')

    private fun stringsFile(tag: String) =
        File(res, if (tag == defaultTag) "values/strings.xml" else "values-$tag/strings.xml")

    private fun stringNames(file: File): Set<String> =
        Regex("<(?:string|plurals) name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toSet()

    private fun translatableNames(file: File): Set<String> =
        Regex("<(?:string|plurals) name=\"([^\"]+)\"(?![^>]*translatable=\"false\")")
            .findAll(file.readText()).map { it.groupValues[1] }.toSet()

    @Test
    fun everyLanguageHasCompleteUiStrings() {
        val required = translatableNames(stringsFile(defaultTag))
        for (language in AppLanguages.all) {
            val file = stringsFile(language.tag)
            assertTrue("${language.tag}: missing ${file.path}", file.isFile)
            val missing = required - stringNames(file)
            assertTrue("${language.tag}: untranslated strings $missing", missing.isEmpty())
        }
    }

    @Test
    fun everyLanguageHasItsOwnAgreement() {
        for (language in AppLanguages.all) {
            val asset = "legal/agreement_${language.tag}.txt"
            assertTrue("${language.tag}: missing assets/$asset", File(assets, asset).isFile)
            val pointer = Regex("name=\"agreement_asset\"[^>]*>([^<]+)<").find(stringsFile(language.tag).readText())
            assertEquals("${language.tag}: agreement_asset must point to $asset", asset, pointer?.groupValues?.get(1))
        }
    }

    @Test
    fun profilesBelongToRegisteredLanguages() {
        for (profile in LanguageProfiles.all) {
            assertTrue("${profile.language.tag}: not in AppLanguages", profile.language in AppLanguages.all)
        }
        val tags = LanguageProfiles.all.map { it.language.tag }
        assertEquals("duplicate profiles", tags.size, tags.toSet().size)
    }

    /** The on-device recognizer expects a language with a region, such as "ko-KR". */
    @Test
    fun everyLanguageHasASpeechLocale() {
        for (language in AppLanguages.all) {
            val speech = java.util.Locale.forLanguageTag(language.speechTag)
            val app = java.util.Locale.forLanguageTag(language.tag)
            assertEquals("${language.tag}: speechTag must be the same language", app.language, speech.language)
            assertTrue("${language.tag}: speechTag needs a region, like \"ko-KR\"", speech.country.isNotEmpty())
        }
    }

    @Test
    fun everyInputLanguageReadsItsDateSamples() {
        for (profile in LanguageProfiles.all.filter { it.dateRules != null }) {
            val tag = profile.language.tag
            val samples = DATE_SAMPLES[tag].orEmpty()
            assertTrue("$tag: add at least 5 cases to DATE_SAMPLES", samples.size >= 5)
            for ((text, expected) in samples) {
                assertEquals("$tag: \"$text\"", expected, readScheduleDates(text, SAMPLE_TODAY, profile))
            }
        }
    }

    @Test
    fun everyInputLanguageReadsItsQuantitySamples() {
        for (profile in LanguageProfiles.all.filter { it.dateRules != null }) {
            val tag = profile.language.tag
            assertTrue("$tag: an input language needs quantityRules", profile.quantityRules != null)
            val samples = QUANTITY_SAMPLES[tag].orEmpty()
            assertTrue("$tag: add at least 5 cases to QUANTITY_SAMPLES", samples.size >= 5)
            for ((text, expected) in samples) {
                assertEquals("$tag: \"$text\"", expected, readKeyFacts(text, SAMPLE_TODAY, profile).map { it.fact }.toSet())
            }
        }
    }

    @Test
    fun everyOutputLanguageHasCompletePrompts() {
        val examples = LanguageProfiles.all.mapNotNull { profile -> profile.prompts?.let { profile to it } }
        for ((profile, pack) in examples) {
            val tag = profile.language.tag
            listOf(
                pack.summarySystem, pack.memoLabel, pack.titleInstruction, pack.memoHeading,
                pack.writeOnlyInThisLanguage, pack.sourceItemsHeading,
            )
                .forEach { assertTrue("$tag: a prompt text is blank", it.isNotBlank()) }
            // Translation pairs one language's example input with another's output, so examples must match in shape.
            val labels = pack.summaryExample.input.lines().filter { it.startsWith("${pack.memoLabel} ") }
            assertEquals("$tag: the example must have two memos labelled \"${pack.memoLabel} n:\"", 2, labels.size)
            assertTrue("$tag: example output is blank", pack.summaryExample.output.isNotBlank())
        }
    }

    @Test
    fun verifiedTranslationsUseRegisteredLanguages() {
        val inputs = LanguageProfiles.all.filter { it.dateRules != null }.map { it.language.tag }
        val outputs = LanguageProfiles.all.filter { it.prompts != null }.map { it.language.tag }
        for ((input, output) in LanguageProfiles.verifiedTranslations) {
            assertTrue("$input→$output: $input is not an input language", input in inputs)
            assertTrue("$input→$output: $output is not an output language", output in outputs)
            assertTrue("$input→$output: not a translation", input != output)
        }
    }
}
