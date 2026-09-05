package com.koshg.interlude.haptics

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Semantic haptic events — each maps to a distinct, purpose-built vibration pattern. */
enum class HapticEvent {
    /** Light acknowledgement: selecting a day, switching tabs. */
    Tap,

    /** A deliberate choice: picking an initiator, opening a sheet. */
    Select,

    /** Flipping a switch (all-day, accepted). */
    Toggle,

    /** Successfully saving an event. */
    Confirm,

    /** Logging a period / sex / proposal entry — the app's core action, given its own distinct feel. */
    LogEntry,

    /** Deleting something. */
    Delete
}

/**
 * Thin wrapper over [VibrationEffect]'s predefined, device-tuned haptic constants.
 * minSdk is 36, so [VibratorManager] (API 31+) and every predefined effect (API 29+) are
 * always available — no capability checks needed.
 */
class Haptics(context: Context) {
    private val vibrator =
        (context.applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    fun perform(event: HapticEvent) {
        val effectId = when (event) {
            HapticEvent.Tap -> VibrationEffect.EFFECT_TICK
            HapticEvent.Select -> VibrationEffect.EFFECT_CLICK
            HapticEvent.Toggle -> VibrationEffect.EFFECT_CLICK
            HapticEvent.Confirm -> VibrationEffect.EFFECT_DOUBLE_CLICK
            HapticEvent.LogEntry -> VibrationEffect.EFFECT_DOUBLE_CLICK
            HapticEvent.Delete -> VibrationEffect.EFFECT_HEAVY_CLICK
        }
        vibrator.vibrate(VibrationEffect.createPredefined(effectId))
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> {
    error("Haptics not provided — wrap the composition in ProvideHaptics")
}

@Composable
fun ProvideHaptics(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    CompositionLocalProvider(LocalHaptics provides haptics, content = content)
}
