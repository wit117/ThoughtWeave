package io.wit117.thoughtweave.data.ai.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether this device can run the on-device model. Bound per build type, so tests can supply an answer. */
fun interface DeviceCompatibility {
    fun isSupported(): Boolean
}

/**
 * Total RAM Android must report for AI features: 5 GiB, which admits phones sold as 6 GB. Android reports less than
 * the installed RAM (a 12 GB Galaxy S23 Ultra reports 10.8 GiB, about 90%), so a 6 GB phone reports about 5.4 GiB.
 * Measured need (CLAUDE.md, Inference): 2.4 GB while the engine is loaded, 1.58 GB of it GPU memory.
 */
internal const val MIN_TOTAL_RAM_BYTES = 5L * 1024 * 1024 * 1024

/** 64-bit, not a low-RAM device, and at least [MIN_TOTAL_RAM_BYTES] of RAM (user decision, 2026-11-25: 6 GB phones). */
internal fun meetsAiRequirements(has64BitAbi: Boolean, isLowRamDevice: Boolean, totalRamBytes: Long): Boolean =
    has64BitAbi && !isLowRamDevice && totalRamBytes >= MIN_TOTAL_RAM_BYTES

class DeviceCompatibilityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceCompatibility {
    override fun isSupported(): Boolean {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        return meetsAiRequirements(
            has64BitAbi = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            isLowRamDevice = activityManager.isLowRamDevice,
            totalRamBytes = memory.totalMem,
        )
    }
}
