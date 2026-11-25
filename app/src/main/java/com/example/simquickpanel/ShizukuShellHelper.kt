package com.example.simquickpanel

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuShellHelper {

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(requestCode)
        }
    }

    /**
     * Executes a shell command with ADB/Shell privileges via Shizuku.
     * Uses reflection to access Shizuku.newProcess in Shizuku API 13+.
     */
    fun runShellCommand(command: String): String {
        if (!isShizukuReady()) return "SHIZUKU_NOT_READY"
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as ShizukuRemoteProcess

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * 1. Toggle Secondary SIM (Slot Index 1) On/Off
     * Note: SubId may vary per device (usually 1 or 2). Adjust subId if needed via `cmd phone list-subscriptions`.
     */
    fun setSecondarySimEnabled(enable: Boolean, sim2SubId: Int = 2) {
        val state = if (enable) "true" else "false"
        // Works on AOSP / HyperOS Android 14-16 via Telephony shell command
        runShellCommand("cmd phone set-sim-power-state-for-slot 1 ${if (enable) 1 else 0}")
        runShellCommand("cmd phone set-subscription-enabled $sim2SubId $state")
    }

    /**
     * 2. Swap Primary SIM (SubId 1) Network Mode: 3G / 4G / 5G
     * Bitmasks:
     * 5G (NR/LTE/WCDMA/GSM) = 524288 | 4096 | 32768 | ... -> standard value: 8486911 or network_mode 26
     * 4G (LTE/WCDMA/GSM)    = 4096 -> network_mode 9
     * 3G (WCDMA only/pref)  = 32768 -> network_mode 0
     */
    fun setPrimarySimNetworkMode(mode: NetworkMode, primarySubId: Int = 1) {
        val networkTypeBitmask = when (mode) {
            NetworkMode.MODE_5G -> "524288" // NR_NSA + NR_SA fallback handled by modem
            NetworkMode.MODE_4G -> "4096"   // LTE
            NetworkMode.MODE_3G -> "32768"  // WCDMA
        }
        val legacyModeInt = when (mode) {
            NetworkMode.MODE_5G -> 26 // NETWORK_MODE_NR_LTE_GSM_WCDMA
            NetworkMode.MODE_4G -> 9  // NETWORK_MODE_LTE_GSM_WCDMA
            NetworkMode.MODE_3G -> 0  // NETWORK_MODE_WCDMA_PREF
        }

        // Set both modern Android 16 bitmask & HyperOS settings database keys
        runShellCommand("cmd phone set-allowed-network-types-for-users -s $primarySubId $networkTypeBitmask")
        runShellCommand("settings put global preferred_network_mode$primarySubId $legacyModeInt")
    }

    /**
     * 3. Switch Primary SIM VoLTE On/Off
     */
    fun setPrimarySimVoLte(enable: Boolean, primarySubId: Int = 1) {
        val value = if (enable) 1 else 0
        // Updates IMS/VoLTE flags recognized by HyperOS / Qualcomm modems
        runShellCommand("settings put global volte_vt_enabled $value")
        runShellCommand("settings put global volte_vt_enabled$primarySubId $value")
        runShellCommand("cmd phone ims set-ims-service-enabled -s $primarySubId ${if (enable) "true" else "false"}")
    }
}

enum class NetworkMode(val label: String) {
    MODE_3G("3G"),
    MODE_4G("4G"),
    MODE_5G("5G")
}
