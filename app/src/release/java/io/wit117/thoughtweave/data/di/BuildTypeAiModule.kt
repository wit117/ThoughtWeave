package io.wit117.thoughtweave.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.wit117.thoughtweave.data.ai.delivery.ModelDelivery
import io.wit117.thoughtweave.data.ai.delivery.PlayFastFollowDelivery
import io.wit117.thoughtweave.data.ai.engine.GemmaInferenceEngine
import io.wit117.thoughtweave.data.ai.engine.InferenceEngine
import io.wit117.thoughtweave.data.ai.model.DeviceCompatibility
import io.wit117.thoughtweave.data.ai.model.DeviceCompatibilityChecker

@Module
@InstallIn(SingletonComponent::class)
abstract class BuildTypeAiModule {
    @Binds
    abstract fun bindModelDelivery(impl: PlayFastFollowDelivery): ModelDelivery

    @Binds
    abstract fun bindInferenceEngine(impl: GemmaInferenceEngine): InferenceEngine

    @Binds
    abstract fun bindDeviceCompatibility(impl: DeviceCompatibilityChecker): DeviceCompatibility
}
