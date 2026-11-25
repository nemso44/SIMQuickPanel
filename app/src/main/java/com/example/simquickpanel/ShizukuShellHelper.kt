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
                arrayOf("sh", "-c", "$command 2>&1"),
                null,
                null
            ) as ShizukuRemoteProcess

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            "ERROR: ${e.cause?.message ?: e.message}"
        }
    }

    /**
     * Dynamically detects active subscription IDs mapped to Slot 0 (SIM 1) and Slot 1 (SIM 2)
     */
    fun getSlotSubIds(): Pair<Int, Int> {
        val output = runShellCommand("cmd phone list-subscriptions")
        var sim1SubId = 1
        var sim2SubId = 2

        // Parse lines like: "subId=3, simSlotIndex=0, ..."
        output.lines().forEach { line ->
            val subMatch = Regex("subId=(\\d+)").find(line)
            val slotMatch = Regex("simSlotIndex=(\\d+)").find(line)
            if (subMatch != null && slotMatch != null) {
                val subId = subMatch.groupValues[1].toInt()
                val slot = slotMatch.groupValues[1].toInt()
                if (slot == 0) sim1SubId = subId
                if (slot == 1) sim2SubId = subId
            }
        }
        return Pair(sim1SubId, sim2SubId)
    }

    /**
     * 1. Toggle Secondary SIM (Slot Index 1) On/Off
     */
    fun setSecondarySimEnabled(enable: Boolean): String {
        val (_, sim2SubId) = getSlotSubIds()
        val state = if (enable) "true" else "false"
        val powerState = if (enable) 1 else 0

        val out1 = runShellCommand("cmd phone set-sim-power-state-for-slot 1 $powerState")
        val out2 = runShellCommand("cmd phone set-subscription-enabled $sim2SubId $state")
        return "SIM2(sub=$sim2SubId): $out1 | $out2".trim()
    }

    /**
     * 2. Swap Primary SIM Network Mode: 3G / 4G / 5G
     */
    fun setPrimarySimNetworkMode(mode: NetworkMode): String {
        val (primarySubId, _) = getSlotSubIds()
        val networkTypeBitmask = when (mode) {
            NetworkMode.MODE_5G -> "524288" // NR
            NetworkMode.MODE_4G -> "4096"   // LTE
            NetworkMode.MODE_3G -> "32768"  // WCDMA
        }
        val legacyModeInt = when (mode) {
            NetworkMode.MODE_5G -> 26 // NR_LTE_GSM_WCDMA
            NetworkMode.MODE_4G -> 9  // LTE_GSM_WCDMA
            NetworkMode.MODE_3G -> 0  // WCDMA_PREF
        }

        val out1 = runShellCommand("cmd phone set-allowed-network-types-for-users -s $primarySubId $networkTypeBitmask")
        val out2 = runShellCommand("settings put global preferred_network_mode$primarySubId $legacyModeInt")
        // HyperOS also checks comma-separated preferred_network_mode
        val out3 = runShellCommand("settings put global preferred_network_mode $legacyModeInt,$legacyModeInt")
        return "Net(sub=$primarySubId): $out1 $out2 $out3".trim()
    }

    /**
     * 3. Switch Primary SIM VoLTE On/Off
     */
    fun setPrimarySimVoLte(enable: Boolean): String {
        val (primarySubId, _) = getSlotSubIds()
        val value = if (enable) 1 else 0
        val out1 = runShellCommand("settings put global volte_vt_enabled $value")
        val out2 = runShellCommand("settings put global volte_vt_enabled$primarySubId $value")
        val out3 = runShellCommand("cmd phone ims set-ims-service-enabled -s $primarySubId ${if (enable) "true" else "false"}")
        return "VoLTE(sub=$primarySubId): $out1 $out2 $out3".trim()
    }
}

enum class NetworkMode(val label: String) {
    MODE_3G("3G"),
    MODE_4G("4G"),
    MODE_5G("5G")
}
