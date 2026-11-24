package io.wit117.thoughtweave.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.wit117.thoughtweave.data.ai.delivery.ImportedModelDelivery
import io.wit117.thoughtweave.data.ai.delivery.ModelDelivery
import io.wit117.thoughtweave.data.ai.engine.GemmaInferenceEngine
import io.wit117.thoughtweave.data.ai.engine.InferenceEngine
import io.wit117.thoughtweave.data.ai.engine.InferencePrompt
import io.wit117.thoughtweave.data.ai.model.DeviceCompatibility
import io.wit117.thoughtweave.data.ai.model.DeviceCompatibilityChecker
import kotlinx.coroutines.flow.Flow

/** Lets instrumented tests supply replies. Unset in the running app, and absent from release. */
object InferenceEngineTestOverride {
    @Volatile
    var engine: InferenceEngine? = null
}

/**
 * Lets instrumented tests run the AI flows on an emulator below the RAM minimum (the AVD has 2 GB). Unset in the
 * running app, and absent from release, where only the real check applies.
 */
object DeviceCompatibilityTestOverride {
    @Volatile
    var supported: Boolean? = null
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BuildTypeAiModule {
    @Binds
    abstract fun bindModelDelivery(impl: ImportedModelDelivery): ModelDelivery

    companion object {
        @Provides
        fun provideInferenceEngine(real: GemmaInferenceEngine): InferenceEngine = object : InferenceEngine {
            override fun generate(prompt: InferencePrompt): Flow<String> =
                (InferenceEngineTestOverride.engine ?: real).generate(prompt)
        }

        @Provides
        fun provideDeviceCompatibility(real: DeviceCompatibilityChecker): DeviceCompatibility =
            DeviceCompatibility { DeviceCompatibilityTestOverride.supported ?: real.isSupported() }
    }
}
