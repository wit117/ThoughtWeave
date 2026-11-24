package io.wit117.thoughtweave.feature.aimodel

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.wit117.thoughtweave.data.ai.delivery.importedModelFile
import io.wit117.thoughtweave.data.ai.model.GEMMA_MODEL_SPEC
import io.wit117.thoughtweave.data.ai.model.InstallMarkerStore
import io.wit117.thoughtweave.data.ai.engine.InferenceEngine
import io.wit117.thoughtweave.data.ai.engine.InferencePrompt
import io.wit117.thoughtweave.data.di.DeviceCompatibilityTestOverride
import io.wit117.thoughtweave.data.di.InferenceEngineTestOverride
import io.wit117.thoughtweave.data.di.modelMarkerFile
import io.wit117.thoughtweave.data.repository.AGREEMENT_PREFS_NAME
import io.wit117.thoughtweave.data.repository.KEY_ACCEPTED_VERSION
import io.wit117.thoughtweave.domain.model.CURRENT_AGREEMENT_VERSION
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.rules.ExternalResource

/** Installs a sparse file of the model's size plus a marker, or removes both. Runs before launch. */
class ModelStateRule(
    private val installed: Boolean = true,
    private val extractionReply: String = "Extracted schedule",
) : ExternalResource() {
    /** Scripted replies: instrumented tests run without the real model. */
    private val engine = object : InferenceEngine {
        override fun generate(prompt: InferencePrompt): Flow<String> =
            if ("short name" in prompt.text) flowOf(extractionReply) else flowOf("DEBUG FAKE", " summary")
    }

    override fun after() {
        InferenceEngineTestOverride.engine = null
        DeviceCompatibilityTestOverride.supported = null
    }

    override fun before() {
        InferenceEngineTestOverride.engine = engine
        // The emulator has 2 GB of RAM, below the AI minimum; these tests cover the flows, not the device check.
        DeviceCompatibilityTestOverride.supported = true
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = importedModelFile(context, GEMMA_MODEL_SPEC)
        val marker = InstallMarkerStore(modelMarkerFile(context))
        if (installed) {
            model.parentFile?.mkdirs()
            RandomAccessFile(model, "rw").use { it.setLength(GEMMA_MODEL_SPEC.sizeBytes) }
            marker.write(model)
        } else {
            model.delete()
            marker.clear()
        }
    }
}

/** Consent is stored per install, so each test states the consent it needs. */
internal fun setAgreementAccepted(accepted: Boolean) {
    val prefs = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences(AGREEMENT_PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    if (accepted) {
        editor.putInt(KEY_ACCEPTED_VERSION, CURRENT_AGREEMENT_VERSION)
    } else {
        editor.remove(KEY_ACCEPTED_VERSION)
    }
    editor.commit()
}
