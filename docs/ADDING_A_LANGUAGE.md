# Adding a language

A language can be supported at three levels. Each level needs the ones before it.

| Level | What the user gets | Needs |
|---|---|---|
| UI | The app in that language | Steps 1–3 |
| AI input | Its memos' dates are read, and it can be chosen as the AI input language | Steps 4–5 |
| AI output | Summaries and schedule titles in that language | Steps 6–7 |

`LanguageContractTest` (unit tests) fails, naming the language and the missing piece, until each level you
add is complete. Run `testDebugUnitTest` after every step.

In the examples below the new language is Japanese, tag `ja`. Use the BCP 47 language tag throughout.

## UI language

1. **Register it.** Add it to `AppLanguages` in `domain/model/AppLanguage.kt`:
   ```kotlin
   val JAPANESE = AppLanguage("ja", "日本語", speechTag = "ja-JP")   // the name in its own language
   val all = listOf(ENGLISH, KOREAN, JAPANESE)
   ```
   The S06 language picker and Android 13+ system settings (`generateLocaleConfig`) pick it up from here and from
   the resource folder in step 2. `speechTag` is the locale given to the on-device speech recognizer for dictation;
   it needs a region (`LanguageContractTest` checks this). Try dictation in the new language on a real phone.
2. **Translate the strings.** Create `res/values-ja/strings.xml` with every string in `res/values/strings.xml`,
   except those marked `translatable="false"`. Lint (`MissingTranslation`) and the contract test both check this.
   Use phrase line breaking for languages that need it, as for Korean (see `CLAUDE.md`).
3. **Translate the agreement.** Add `assets/legal/agreement_ja.txt`, and in `values-ja/strings.xml` set
   `<string name="agreement_asset">legal/agreement_ja.txt</string>`.
   - Keep the `STATUS: DRAFT - REQUIRES LEGAL REVIEW` line until **that translation** has had its own legal review.
     `bundleRelease` checks every `agreement_*.txt`, so the draft guard covers the new file automatically.
   - The Gemma Terms of Use and Prohibited Use Policy stay in their official English.

## AI input language

4. **Write the date rules.** Create `data/ai/language/JapaneseProfile.kt` based on `KoreanProfile.kt`:
   - `isScriptChar` and `usesLatinScript`, for Auto detection. Latin-script languages cannot be told apart by
     script, so Auto picks the app language for Latin text.
   - `dateRules`: written-out dates (`dates`, read in every language), numeric dates in this language's order
     (`numericDates`, used only when it is the input language), weekdays, times, range words, and how a year is
     attached to a date (`writtenYear`, used to remove years the model invents).
   - Leave `prompts = null` for now.
   - Register it in `LanguageProfiles.all`.
   - `quantityRules`: amounts and counts, used to check that a summary keeps them (see `KeyFactReader.kt`).
     `normalizeNumbers` turns number words into digits only where they are numbers; `amounts` are numbers with
     a currency word (symbols such as "$" are shared); `counts` are numbers with a unit or counter. Percentages,
     dates, times and long numbers such as phone numbers are shared.
5. **Add date and quantity samples.** Add at least five cases to `DATE_SAMPLES` in
   `test/.../data/ai/language/DateSamples.kt`: a written-out date, a relative day, a weekday, a time and a range.
   Add at least five to `QUANTITY_SAMPLES` in `QuantitySamples.kt`: an amount with a currency word, a large amount,
   a count with a unit, a count written as a word, and a percentage. For bugs or tricky forms, add a dedicated
   test class too, like `CommaDateFormatsTest` or `KeyFactReaderTest`.

## AI output language

6. **Write the prompts.** Fill in `prompts` in the profile:
   - `summarySystem`: translate the existing instruction. Keep its meaning exactly: condense, never answer
     questions, never add facts or years, no headings or markdown.
   - `summaryExample`: **the same team-lunch example as the other languages**, in two memos labelled
     `"<memoLabel> 1:"` and `"<memoLabel> 2:"`. Translation requests pair one language's example input with
     another's output, so the content must match.
   - `memoLabel`, `titleInstruction`, `memoHeading`, `writeOnlyInThisLanguage`.
7. **Measure on the real model.**
   1. Add sample memos for the language to `SAMPLES` in `androidTest/.../RealModelLanguageCheck.kt`, with the
      same facts as the other languages.
   2. Push the model: `adb push <model file> /data/local/tmp/`.
   3. Run only that class:
      `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.wit117.thoughtweave.RealModelLanguageCheck`
   4. Read the log: `adb logcat -d -s LANGUAGE_CHECK`.
   5. Delete the model from `/data/local/tmp`.

   **Pass criteria for each summary:** every fact, name, number and date kept and unchanged; questions kept open,
   not answered; no invented years or facts; written in the output language; plain text. Titles must be short,
   in the output language, and without dates.

   - **Same-language result (ja→ja) fails:** don't add `prompts`. The language stays UI and input only.
   - **Translation pair passes** (for example ko→ja): add `"ko" to "ja"` to `LanguageProfiles.verifiedTranslations`.
     S06 shows the AI output language setting once at least one pair is listed. Record the result there, as for
     en↔ko.

## Finishing

8. Run every check from `CLAUDE.md` (builds, lint, unit tests, instrumented tests, launch), then check the app in
   the new language on the emulator. Switch with S06 and look at S01–S06.
9. Record the language and its measured results in `CLAUDE.md`.
