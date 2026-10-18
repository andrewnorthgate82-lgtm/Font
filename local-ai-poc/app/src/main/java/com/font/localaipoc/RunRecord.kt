package com.font.localaipoc

import java.util.Locale

data class RunRecord(
    val timestamp: String,
    val device: String,
    val androidVersion: String,
    val osReportedRamMb: Long,
    val model: String,
    val backend: String,
    val testId: String,
    val wallTimeMs: Long,
    val engineLoadMs: Long,
    val timeToFirstTokenSec: Double,
    val prefillTokens: Int,
    val prefillTokensPerSec: Double,
    val decodeTokens: Int,
    val decodeTokensPerSec: Double,
    val processPssBeforeKb: Long,
    val processPssPeakKb: Long,
    val processPssAfterKb: Long,
    val thermalBefore: String,
    val thermalAfter: String,
    val batteryBeforePercent: Int?,
    val batteryAfterPercent: Int?,
    val zwnjCheckPassed: Boolean?,
    val output: String,
) {
    fun toCsvRow(): String = listOf(
        timestamp,
        device,
        androidVersion,
        osReportedRamMb.toString(),
        model,
        backend,
        testId,
        wallTimeMs.toString(),
        engineLoadMs.toString(),
        timeToFirstTokenSec.csvNumber(),
        prefillTokens.toString(),
        prefillTokensPerSec.csvNumber(),
        decodeTokens.toString(),
        decodeTokensPerSec.csvNumber(),
        processPssBeforeKb.toString(),
        processPssPeakKb.toString(),
        processPssAfterKb.toString(),
        thermalBefore,
        thermalAfter,
        batteryBeforePercent?.toString().orEmpty(),
        batteryAfterPercent?.toString().orEmpty(),
        zwnjCheckPassed?.toString().orEmpty(),
        output,
    ).joinToString(",") { it.csvEscape() }

    companion object {
        val csvHeader = listOf(
            "timestamp_local",
            "device",
            "android_version",
            "os_reported_ram_mb",
            "model",
            "backend",
            "test_id",
            "wall_time_ms",
            "engine_load_ms",
            "time_to_first_token_sec",
            "prefill_tokens",
            "prefill_tokens_per_sec",
            "decode_tokens",
            "decode_tokens_per_sec",
            "process_pss_before_kb",
            "process_pss_peak_kb",
            "process_pss_after_kb",
            "thermal_before",
            "thermal_after",
            "battery_before_percent",
            "battery_after_percent",
            "zwnj_check_passed",
            "output",
        ).joinToString(",") { it.csvEscape() }
    }
}

private fun String.csvEscape(): String = "\"${replace("\"", "\"\"").replace("\r", " ").replace("\n", "\\n")}\""

internal fun Double.csvNumber(): String = String.format(Locale.US, "%.3f", this)
