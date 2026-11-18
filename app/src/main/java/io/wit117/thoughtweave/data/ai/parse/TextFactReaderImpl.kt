package io.wit117.thoughtweave.data.ai.parse

import io.wit117.thoughtweave.data.ai.language.LanguageProfiles
import io.wit117.thoughtweave.domain.model.AppLanguage
import io.wit117.thoughtweave.domain.model.KeyFactMention
import io.wit117.thoughtweave.domain.usecase.TextFactReader
import java.time.LocalDate
import javax.inject.Inject

class TextFactReaderImpl @Inject constructor() : TextFactReader {
    override fun factsIn(text: String, language: AppLanguage, today: LocalDate): List<KeyFactMention> =
        readKeyFacts(text, today, LanguageProfiles.forLanguage(language))
}
