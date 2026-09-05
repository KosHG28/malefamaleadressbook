package com.koshg.interlude.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koshg.interlude.R
import com.koshg.interlude.backup.BackupStatus
import com.koshg.interlude.diagnostics.CrashLog
import com.koshg.interlude.haptics.HapticEvent
import com.koshg.interlude.haptics.LocalHaptics
import com.koshg.interlude.security.AppLockPreferences
import com.koshg.interlude.settings.PhaseFillStyle
import com.koshg.interlude.ui.theme.LocalThemeMode
import com.koshg.interlude.ui.theme.MarkerKind
import com.koshg.interlude.ui.theme.MarkerPreset
import com.koshg.interlude.ui.theme.MarkerPresets
import com.koshg.interlude.ui.theme.Palette
import com.koshg.interlude.ui.theme.ThemeMode
import com.koshg.interlude.ui.theme.appColors
import com.koshg.interlude.ui.theme.presetFor
import com.koshg.interlude.ui.theme.previewAccent
import com.koshg.interlude.ui.theme.resolveDark
import com.koshg.interlude.util.DEFAULT_LUTEAL_PHASE_DAYS
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Sensible bounds for a user-supplied luteal-phase length -- outside this the ovulation estimate stops being meaningful. */
private val LUTEAL_PHASE_DAYS_RANGE = 8..20

@Composable
fun SettingsScreen(
    lutealPhaseDays: Int,
    onLutealPhaseDaysChange: (Int) -> Unit,
    adaptiveTheme: Boolean,
    onAdaptiveThemeChange: (Boolean) -> Unit,
    phaseFillStyle: PhaseFillStyle,
    onPhaseFillStyleChange: (PhaseFillStyle) -> Unit,
    palette: Palette,
    onPaletteChange: (Palette) -> Unit,
    suggestionsEnabled: Boolean,
    onSuggestionsEnabledChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    remindersEnabled: Boolean,
    onRemindersEnabledChange: (Boolean) -> Unit,
    markerPresets: MarkerPresets,
    onMarkerPresetChange: (MarkerKind, MarkerPreset) -> Unit,
    onResetMarkerPresets: () -> Unit,
    legendVisibility: LegendVisibility,
    onShowPhaseLegendChange: (Boolean) -> Unit,
    onShowMarkerLegendChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val appColors = appColors()
    val gradient = Brush.verticalGradient(listOf(appColors.gradientTop, appColors.gradientBottom))

    // Shown as a plain in-place overlay, not a Dialog/Popup window, so it needs its own back
    // interception -- see HistoryScreen's BackHandler for why.
    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = appColors.textPrimary)
                }
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item { SecuritySection() }
                item { PaletteSection(palette, onPaletteChange) }
                item {
                    AppearanceSection(
                        adaptiveTheme = adaptiveTheme,
                        onAdaptiveThemeChange = onAdaptiveThemeChange,
                        phaseFillStyle = phaseFillStyle,
                        onPhaseFillStyleChange = onPhaseFillStyleChange,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange
                    )
                }
                item {
                    MarkerColorSection(
                        markerPresets = markerPresets,
                        onMarkerPresetChange = onMarkerPresetChange,
                        onResetMarkerPresets = onResetMarkerPresets
                    )
                }
                item {
                    LegendSection(
                        legendVisibility = legendVisibility,
                        onShowPhaseLegendChange = onShowPhaseLegendChange,
                        onShowMarkerLegendChange = onShowMarkerLegendChange
                    )
                }
                item { CycleModelSection(lutealPhaseDays, onLutealPhaseDaysChange) }
                item { SuggestionsSection(suggestionsEnabled, onSuggestionsEnabledChange) }
                item { RemindersSection(remindersEnabled, onRemindersEnabledChange) }
                item { DataSection(onExportData, onImportData) }
                item { AboutSection() }
                item { Spacer(Modifier.height(24.dp)) }
            }
            }
        }
    }
}

@Composable
private fun SecuritySection() {
    val appColors = appColors()
    val context = LocalContext.current
    val prefs = remember { AppLockPreferences(context) }
    var enabled by remember { mutableStateOf(prefs.isEnabled) }

    SectionCard(title = stringResource(R.string.settings_section_security)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_app_lock),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary
                )
                Text(
                    stringResource(R.string.settings_app_lock_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    prefs.isEnabled = it
                }
            )
        }
    }
}

/** The color of each day-cell marker ring, one swatch row per kind. Offered as a fixed set of
 *  presets rather than a free color wheel on purpose: a preset carries a light and a dark value,
 *  both picked to clear all four phase fills, so no choice can leave a ring invisible or land as
 *  an ink blot in the other theme. Collapsed by default -- five swatch rows is a lot of section
 *  to scroll past for something most people set once, so the header carries a compact preview of
 *  the current five instead. */
@Composable
private fun MarkerColorSection(
    markerPresets: MarkerPresets,
    onMarkerPresetChange: (MarkerKind, MarkerPreset) -> Unit,
    onResetMarkerPresets: () -> Unit
) {
    val appColors = appColors()
    val haptics = LocalHaptics.current
    val dark = LocalThemeMode.current.resolveDark(isSystemInDarkTheme())
    var expanded by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.settings_section_marker_colors)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.perform(HapticEvent.Tap)
                    expanded = !expanded
                }
        ) {
            Text(
                stringResource(R.string.settings_marker_colors_hint),
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            if (!expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MarkerKind.entries.forEach { kind ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(markerPresets.presetFor(kind).color(dark))
                        )
                    }
                }
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                tint = appColors.textSecondary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                MarkerKind.entries.forEach { kind ->
                    val selected = markerPresets.presetFor(kind)
                    Text(
                        stringResource(kind.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors.textPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MarkerPreset.entries.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(preset.color(dark))
                                    .then(
                                        if (preset == selected) {
                                            Modifier.border(2.5.dp, appColors.textPrimary, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        haptics.perform(HapticEvent.Select)
                                        onMarkerPresetChange(kind, preset)
                                    }
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                OutlinedButton(
                    onClick = {
                        haptics.perform(HapticEvent.Tap)
                        onResetMarkerPresets()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary)
                ) {
                    Text(stringResource(R.string.settings_reset_defaults))
                }
            }
        }
    }
}

/** Which of the two calendar legends are shown under the month grid. Both on by default -- a new
 *  user has no other way to learn what the colors mean -- and switchable off once they have. */
@Composable
private fun LegendSection(
    legendVisibility: LegendVisibility,
    onShowPhaseLegendChange: (Boolean) -> Unit,
    onShowMarkerLegendChange: (Boolean) -> Unit
) {
    val appColors = appColors()
    SectionCard(title = stringResource(R.string.settings_section_legends)) {
        LegendToggleRow(
            label = stringResource(R.string.settings_phase_legend),
            description = stringResource(R.string.settings_phase_legend_hint),
            checked = legendVisibility.phases,
            onCheckedChange = onShowPhaseLegendChange
        )
        Spacer(Modifier.height(10.dp))
        LegendToggleRow(
            label = stringResource(R.string.settings_marker_legend),
            description = stringResource(R.string.settings_marker_legend_hint),
            checked = legendVisibility.markers,
            onCheckedChange = onShowMarkerLegendChange
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_legends_note),
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary
        )
    }
}

@Composable
private fun LegendToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val appColors = appColors()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = appColors.textPrimary)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textSecondary
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A row of tappable color swatches -- one per [Palette] -- picking the app's overall accent
 *  and background scheme. Phase colors (menstrual/ovulation/etc.) stay the same regardless of
 *  the chosen palette, since they carry meaning, not just decoration. */
@Composable
private fun PaletteSection(palette: Palette, onPaletteChange: (Palette) -> Unit) {
    val appColors = appColors()
    val dark = LocalThemeMode.current.resolveDark(isSystemInDarkTheme())
    SectionCard(title = stringResource(R.string.settings_section_color_scheme)) {
        Text(
            stringResource(R.string.settings_color_scheme_hint),
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Palette.entries.forEach { p ->
                val swatchColor = p.previewAccent(dark)
                val isSelected = p == palette
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.5.dp, appColors.textPrimary, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onPaletteChange(p) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(p.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = appColors.textSecondary
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // A tiny mock of the calendar's own accent/background elements -- appColors() already
        // resolves the just-tapped palette (LocalPalette flows down from the committed setting),
        // so this updates live the instant a swatch above is picked, no need to leave Settings
        // to see the effect on the actual calendar. Kept in sync with the actual shapes used on
        // the calendar (capsule day cell, pill-shaped Add button with its icon+label) rather than
        // generic circles, so it still reads as "this app" and not just "some accent color".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(appColors.gradientTop, appColors.gradientBottom)))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 30.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(appColors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "14",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(stringResource(R.string.settings_preview), style = MaterialTheme.typography.labelSmall, color = appColors.textSecondary)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(appColors.accent)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.action_add),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(
    adaptiveTheme: Boolean,
    onAdaptiveThemeChange: (Boolean) -> Unit,
    phaseFillStyle: PhaseFillStyle,
    onPhaseFillStyleChange: (PhaseFillStyle) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val appColors = appColors()
    SectionCard(title = stringResource(R.string.settings_section_appearance)) {
        Column {
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textPrimary
            )
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    val isSelected = mode == themeMode
                    SegmentedButton(
                        selected = isSelected,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = appColors.accent,
                            activeContentColor = Color.White,
                            activeBorderColor = appColors.accent,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = appColors.textPrimary,
                            inactiveBorderColor = appColors.textSecondary.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(stringResource(mode.labelRes), color = if (isSelected) Color.White else appColors.textPrimary)
                    }
                }
            }
        }
        Column {
            Text(
                stringResource(R.string.settings_phase_display),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textPrimary
            )
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PhaseFillStyle.entries.forEachIndexed { index, style ->
                    val isSelected = style == phaseFillStyle
                    SegmentedButton(
                        selected = isSelected,
                        onClick = { onPhaseFillStyleChange(style) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PhaseFillStyle.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = appColors.accent,
                            activeContentColor = Color.White,
                            activeBorderColor = appColors.accent,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = appColors.textPrimary,
                            inactiveBorderColor = appColors.textSecondary.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            if (style == PhaseFillStyle.FILLED) stringResource(R.string.settings_fill) else stringResource(R.string.settings_dashed),
                            color = if (isSelected) Color.White else appColors.textPrimary
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_adaptive_theme),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary
                )
                Text(
                    stringResource(R.string.settings_adaptive_theme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary
                )
            }
            Switch(checked = adaptiveTheme, onCheckedChange = onAdaptiveThemeChange)
        }
    }
}

@Composable
private fun SuggestionsSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val appColors = appColors()
    SectionCard(title = stringResource(R.string.settings_section_hints)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_suggestions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary
                )
                Text(
                    stringResource(R.string.settings_suggestions_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun RemindersSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val appColors = appColors()
    SectionCard(title = stringResource(R.string.settings_section_notifications)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_reminders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary
                )
                Text(
                    stringResource(R.string.settings_reminders_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun DataSection(onExport: () -> Unit, onImport: () -> Unit) {
    val appColors = appColors()
    SectionCard(title = stringResource(R.string.settings_section_data)) {
        Text(
            stringResource(R.string.settings_data_hint),
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.accent)
            ) { Text(stringResource(R.string.settings_export)) }
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.accent)
            ) { Text(stringResource(R.string.settings_import)) }
        }
        Spacer(Modifier.height(14.dp))
        AutoBackupStatusRow()
    }
}

/** Android's own Auto Backup is invisible from inside the app -- it runs on the system's schedule
 *  and offers no API to ask whether it works -- so "it's broken" and "it hasn't run yet" are
 *  indistinguishable without this. CalendarBackupAgent stamps the times; this just reports them,
 *  plus the conditions, since those are what usually explain a backup that "isn't happening". */
@Composable
private fun AutoBackupStatusRow() {
    val appColors = appColors()
    val context = LocalContext.current
    val status = remember { BackupStatus(context) }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru")) }
    val lastBackup = status.lastBackupAtMillis
    val lastRestore = status.lastRestoreAtMillis

    fun format(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)

    Text(
        stringResource(R.string.settings_system_backup),
        style = MaterialTheme.typography.bodyMedium,
        color = appColors.textPrimary
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = lastBackup?.let { stringResource(R.string.settings_last_backup, format(it)) }
            ?: stringResource(R.string.settings_no_backup_yet),
        style = MaterialTheme.typography.bodySmall,
        color = appColors.textSecondary
    )
    if (lastRestore != null) {
        Text(
            stringResource(R.string.settings_restored_from_backup, format(lastRestore)),
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_backup_conditions),
        style = MaterialTheme.typography.bodySmall,
        color = appColors.textSecondary
    )
}

@Composable
private fun CycleModelSection(lutealPhaseDays: Int, onLutealPhaseDaysChange: (Int) -> Unit) {
    val appColors = appColors()
    SectionCard(title = stringResource(R.string.settings_section_cycle_model)) {
        CountStepper(stringResource(R.string.settings_luteal_length), lutealPhaseDays) { newValue ->
            onLutealPhaseDaysChange(newValue.coerceIn(LUTEAL_PHASE_DAYS_RANGE))
        }
        Text(
            stringResource(R.string.settings_luteal_hint, DEFAULT_LUTEAL_PHASE_DAYS),
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary
        )
    }
}

@Composable
private fun AboutSection() {
    val appColors = appColors()
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        }.getOrNull() ?: "—"
    }

    SectionCard(title = stringResource(R.string.settings_section_about)) {
        Text("Interlude", style = MaterialTheme.typography.bodyMedium, color = appColors.textPrimary)
        Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodySmall, color = appColors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_about_local),
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_about_backup),
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary
        )
        CrashReportRow()
    }
}

/**
 * Shows the last crash's stack trace, and only appears when there is one.
 *
 * This build is installed from a GitHub release rather than from Play, so nothing collects crash
 * reports on its own. Without this, a crash leaves nothing behind but a phone that closed itself,
 * which is not something a fix can be built from.
 */
@Composable
private fun CrashReportRow() {
    val context = LocalContext.current
    val crashLog = remember(context) { CrashLog(context) }
    var trace by remember { mutableStateOf(crashLog.read()) }
    val current = trace ?: return
    val appColors = appColors()

    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(R.string.settings_crash_title),
        style = MaterialTheme.typography.labelLarge,
        color = appColors.warning
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_crash_hint),
        style = MaterialTheme.typography.bodySmall,
        color = appColors.textSecondary
    )
    Spacer(Modifier.height(8.dp))
    SelectionContainer {
        Text(
            current,
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        )
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = {
        crashLog.clear()
        trace = null
    }) {
        Text(stringResource(R.string.settings_crash_clear))
    }
}
