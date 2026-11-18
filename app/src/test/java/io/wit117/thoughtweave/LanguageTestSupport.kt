package io.wit117.thoughtweave

import io.wit117.thoughtweave.data.ai.language.AiLanguageCatalogImpl
import io.wit117.thoughtweave.domain.model.AppLanguage
import io.wit117.thoughtweave.domain.model.InputLanguageSetting
import io.wit117.thoughtweave.domain.model.LanguageSettings
import io.wit117.thoughtweave.domain.model.OutputLanguageSetting
import io.wit117.thoughtweave.domain.repository.LanguageSettingsRepository
import io.wit117.thoughtweave.domain.repository.UiLanguageRepository
import io.wit117.thoughtweave.domain.usecase.ResolveAiLanguageUseCase
import io.wit117.thoughtweave.data.ai.parse.TextFactReaderImpl
import io.wit117.thoughtweave.domain.usecase.CheckSummaryFactsUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class FakeLanguageSettings(var settings: LanguageSettings = LanguageSettings()) : LanguageSettingsRepository {
    override fun current() = settings
    override fun setInput(setting: InputLanguageSetting) {
        settings = settings.copy(input = setting)
    }

    override fun setOutput(setting: OutputLanguageSetting) {
        settings = settings.copy(output = setting)
    }
}

internal class FakeUiLanguage(var language: AppLanguage? = null) : UiLanguageRepository {
    override fun current() = language
    override fun set(language: AppLanguage?) {
        this.language = language
    }
}

/** The real fact reader, with today fixed at Monday 2026-11-09. */
internal fun testFactCheck() = CheckSummaryFactsUseCase(
    TextFactReaderImpl(),
    Clock.fixed(Instant.parse("2026-11-09T03:00:00Z"), ZoneOffset.UTC),
    ZoneOffset.UTC,
)

/** The real catalog of registered profiles, with default settings. */
internal fun testLanguageResolver(
    settings: FakeLanguageSettings = FakeLanguageSettings(),
    ui: FakeUiLanguage = FakeUiLanguage(),
) = ResolveAiLanguageUseCase(settings, AiLanguageCatalogImpl(), ui)
