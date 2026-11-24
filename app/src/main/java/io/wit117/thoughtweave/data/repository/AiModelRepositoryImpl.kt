package io.wit117.thoughtweave.data.repository

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import io.wit117.thoughtweave.data.ai.delivery.ModelDelivery
import io.wit117.thoughtweave.data.ai.delivery.ModelDeliveryState
import io.wit117.thoughtweave.data.ai.model.DeviceCompatibility
import io.wit117.thoughtweave.data.ai.model.ModelFileProvider
import io.wit117.thoughtweave.data.ai.model.ModelInstaller
import io.wit117.thoughtweave.data.di.ApplicationScope
import io.wit117.thoughtweave.domain.model.AiModelStatus
import io.wit117.thoughtweave.domain.repository.AiModelRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

@Singleton
class AiModelRepositoryImpl internal constructor(
    private val delivery: ModelDelivery,
    private val installer: ModelInstaller,
    scope: CoroutineScope,
    isDeviceSupported: Boolean,
) : AiModelRepository, ModelFileProvider {

    @Inject
    constructor(
        delivery: ModelDelivery,
        installer: ModelInstaller,
        compatibility: DeviceCompatibility,
        @ApplicationScope scope: CoroutineScope,
    ) : this(delivery, installer, scope, compatibility.isSupported())

    private val engineFailure = MutableStateFlow<AiModelStatus?>(null)

    @Volatile
    private var verifiedFile: File? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override val status: StateFlow<AiModelStatus> = if (isDeviceSupported) {
        val delivered = delivery.state.transformLatest { state ->
            verifiedFile = null
            when (state) {
                is ModelDeliveryState.InProgress -> emit(state.status)
                is ModelDeliveryState.Delivered -> {
                    emit(AiModelStatus.Verifying)
                    emit(verify(state.file))
                }
            }
        }
        combine(delivered, engineFailure) { deliveryStatus, failure ->
            if (deliveryStatus == AiModelStatus.Ready && failure != null) failure else deliveryStatus
        }.stateIn(scope, SharingStarted.Eagerly, AiModelStatus.Checking)
    } else {
        MutableStateFlow(AiModelStatus.DeviceUnsupported)
    }

    private suspend fun verify(file: File): AiModelStatus = try {
        if (installer.verify(file)) {
            verifiedFile = file
            AiModelStatus.Ready
        } else {
            AiModelStatus.Corrupted
        }
    } catch (_: IOException) {
        AiModelStatus.Failed
    }

    override fun verifiedModelFile(): File? = verifiedFile

    override suspend fun onEngineInitFailed() {
        val file = verifiedFile ?: return
        installer.clearMarker()
        val intact = try {
            installer.verify(file)
        } catch (_: IOException) {
            false
        }
        engineFailure.value = if (intact) AiModelStatus.DeviceUnsupported else AiModelStatus.Corrupted
    }

    override fun refresh() = delivery.refresh()

    override fun retry() = delivery.retry()

    override fun redownload() {
        engineFailure.value = null
        installer.clearMarker()
        delivery.discard()
    }

    override fun showConfirmation(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean =
        delivery.showConfirmation(launcher)
}
