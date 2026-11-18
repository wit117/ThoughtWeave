package io.wit117.thoughtweave.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.wit117.thoughtweave.data.ai.model.GEMMA_MODEL_SPEC
import io.wit117.thoughtweave.data.ai.model.InstallMarkerStore
import io.wit117.thoughtweave.data.ai.model.ModelFileProvider
import io.wit117.thoughtweave.data.ai.model.ModelSpec
import io.wit117.thoughtweave.data.local.dao.MemoDao
import io.wit117.thoughtweave.data.local.dao.ScheduleDao
import io.wit117.thoughtweave.data.local.database.ThoughtWeaveDatabase
import io.wit117.thoughtweave.data.repository.AgreementRepositoryImpl
import io.wit117.thoughtweave.data.repository.AiModelRepositoryImpl
import io.wit117.thoughtweave.data.repository.MemoRepositoryImpl
import io.wit117.thoughtweave.data.repository.MemoSynthesisRepositoryImpl
import io.wit117.thoughtweave.data.repository.ScheduleExtractionRepositoryImpl
import io.wit117.thoughtweave.data.repository.UiLanguageRepositoryImpl
import io.wit117.thoughtweave.data.repository.LanguageSettingsRepositoryImpl
import io.wit117.thoughtweave.data.ai.language.AiLanguageCatalogImpl
import io.wit117.thoughtweave.data.ai.parse.TextFactReaderImpl
import io.wit117.thoughtweave.domain.usecase.TextFactReader
import io.wit117.thoughtweave.domain.repository.AiLanguageCatalog
import io.wit117.thoughtweave.domain.repository.LanguageSettingsRepository
import io.wit117.thoughtweave.data.repository.ScheduleRepositoryImpl
import io.wit117.thoughtweave.domain.repository.AgreementRepository
import io.wit117.thoughtweave.domain.repository.AiModelRepository
import io.wit117.thoughtweave.domain.repository.MemoRepository
import io.wit117.thoughtweave.domain.repository.MemoSynthesisRepository
import io.wit117.thoughtweave.domain.repository.ScheduleExtractionRepository
import io.wit117.thoughtweave.domain.repository.UiLanguageRepository
import io.wit117.thoughtweave.domain.repository.ScheduleRepository
import java.io.File
import java.time.Clock
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Lives as long as the app, for work that must outlast a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Under `noBackupFilesDir`, because a marker is only valid for files on this device. */
fun modelMarkerFile(context: Context): File = File(context.noBackupFilesDir, "model_install_marker")

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAiModelRepository(impl: AiModelRepositoryImpl): AiModelRepository

    @Binds
    abstract fun bindModelFileProvider(impl: AiModelRepositoryImpl): ModelFileProvider

    @Binds
    abstract fun bindMemoSynthesisRepository(impl: MemoSynthesisRepositoryImpl): MemoSynthesisRepository

    @Binds
    abstract fun bindScheduleExtractionRepository(
        impl: ScheduleExtractionRepositoryImpl,
    ): ScheduleExtractionRepository

    @Binds
    abstract fun bindUiLanguageRepository(impl: UiLanguageRepositoryImpl): UiLanguageRepository

    @Binds
    abstract fun bindLanguageSettingsRepository(impl: LanguageSettingsRepositoryImpl): LanguageSettingsRepository

    @Binds
    abstract fun bindAiLanguageCatalog(impl: AiLanguageCatalogImpl): AiLanguageCatalog

    @Binds
    abstract fun bindTextFactReader(impl: TextFactReaderImpl): TextFactReader

    @Binds
    abstract fun bindAgreementRepository(impl: AgreementRepositoryImpl): AgreementRepository

    @Binds
    abstract fun bindMemoRepository(impl: MemoRepositoryImpl): MemoRepository

    @Binds
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): ThoughtWeaveDatabase =
            Room.databaseBuilder(context, ThoughtWeaveDatabase::class.java, "thoughtweave.db")
                .build()

        @Provides
        fun provideMemoDao(database: ThoughtWeaveDatabase): MemoDao = database.memoDao()

        @Provides
        fun provideScheduleDao(database: ThoughtWeaveDatabase): ScheduleDao = database.scheduleDao()

        @Provides
        fun provideClock(): Clock = Clock.systemUTC()

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        @Provides
        fun provideModelSpec(): ModelSpec = GEMMA_MODEL_SPEC

        @Provides
        fun provideInstallMarkerStore(@ApplicationContext context: Context): InstallMarkerStore =
            InstallMarkerStore(modelMarkerFile(context))

        /** Not a singleton: the device time zone can change while the app is running. */
        @Provides
        fun provideZoneId(): ZoneId = ZoneId.systemDefault()
    }
}
