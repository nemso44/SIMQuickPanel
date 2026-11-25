package com.example.simquickpanel

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuShellHelper {

    fun isBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            isBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (isBinderAlive() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Executes a shell command via Shizuku on an IO thread and captures both stdout and stderr.
     */
    suspend fun runShellCommand(command: String): String = withContext(Dispatchers.IO) {
        if (!isBinderAlive()) return@withContext "ERR: Shizuku service not running"
        if (!hasPermission()) return@withContext "ERR: Shizuku permission denied"

        return@withContext try {
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
            val exitCode = process.waitFor()
            if (output.isEmpty()) "OK (exit=$exitCode)" else output
        } catch (e: Exception) {
            "EX: ${e.cause?.message ?: e.message}"
        }
    }

    /**
     * Parses active subIds for Slot 0 (SIM 1) and Slot 1 (SIM 2) from dumpsys or cmd phone.
     */
    suspend fun getSlotSubIds(): Pair<Int, Int> {
        val output = runShellCommand("cmd phone list-subscriptions")
        var sim1SubId = 1
        var sim2SubId = 2

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
     * 1. Toggle Secondary SIM (Slot 1) On/Off across AOSP + Xiaomi HyperOS
     */
    suspend fun setSecondarySimEnabled(enable: Boolean): String {
        val (_, sim2SubId) = getSlotSubIds()
        val boolStr = if (enable) "true" else "false"
        val intVal = if (enable) 1 else 0

        // Run AOSP subscription toggle + slot power state + MIUI/HyperOS global setting
        val cmd = listOf(
            "cmd phone set-subscription-enabled $sim2SubId $boolStr",
            "cmd phone set-sim-power-state-for-slot 1 $intVal",
            "settings put global sim_enabled_1 $intVal"
        ).joinToString(" ; ")

        val res = runShellCommand(cmd)
        return "SIM2 [sub:$sim2SubId -> $boolStr]: $res"
    }

    /**
     * 2. Swap Primary SIM Network Mode: 3G / 4G / 5G
     */
    suspend fun setPrimarySimNetworkMode(mode: NetworkMode): String {
        val (primarySubId, secondarySubId) = getSlotSubIds()

        // Android 12+ allowed network type bitmasks (combined GSM+WCDMA+LTE+NR so modem doesn't reject single-RAT masks)
        val bitmask = when (mode) {
            NetworkMode.MODE_5G -> "561151" // NR + LTE + WCDMA + GSM
            NetworkMode.MODE_4G -> "36863"  // LTE + WCDMA + GSM (no NR)
            NetworkMode.MODE_3G -> "32767"  // WCDMA + GSM only
        }

        // Legacy & HyperOS preferred_network_mode integers
        val modeInt = when (mode) {
            NetworkMode.MODE_5G -> 26 // NR_LTE_GSM_WCDMA
            NetworkMode.MODE_4G -> 9  // LTE_GSM_WCDMA
            NetworkMode.MODE_3G -> 0  // WCDMA_PREF
        }

        // Read existing SIM 2 mode so we preserve comma-separated "sim1,sim2" format used by Xiaomi
        val currentDual = runShellCommand("settings get global preferred_network_mode")
        val sim2Mode = currentDual.split(",").getOrNull(1)?.trim()?.toIntOrNull() ?: 26

        val cmd = listOf(
            "cmd phone set-allowed-network-types-for-users -s $primarySubId $bitmask",
            "settings put global preferred_network_mode$primarySubId $modeInt",
            "settings put global preferred_network_mode $modeInt,$sim2Mode",
            "settings put global user_preferred_network_mode $modeInt,$sim2Mode"
        ).joinToString(" ; ")

        val res = runShellCommand(cmd)
        return "SIM1 ${mode.label} [sub:$primarySubId]: $res"
    }

    /**
     * 3. Switch Primary SIM VoLTE On/Off
     */
    suspend fun setPrimarySimVoLte(enable: Boolean): String {
        val (primarySubId, _) = getSlotSubIds()
        val intVal = if (enable) 1 else 0
        val boolStr = if (enable) "true" else "false"

        val cmd = listOf(
            "settings put global volte_vt_enabled $intVal",
            "settings put global volte_vt_enabled0 $intVal",
            "settings put global volte_vt_enabled$primarySubId $intVal",
            "cmd phone ims set-ims-service-enabled -s $primarySubId $boolStr"
        ).joinToString(" ; ")

        val res = runShellCommand(cmd)
        return "VoLTE [sub:$primarySubId -> $boolStr]: $res"
    }
}

enum class NetworkMode(val label: String) {
    MODE_3G("3G"),
    MODE_4G("4G"),
    MODE_5G("5G")
}
