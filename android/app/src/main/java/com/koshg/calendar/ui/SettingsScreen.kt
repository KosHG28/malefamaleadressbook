package com.koshg.calendar.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koshg.calendar.security.AppLockPreferences
import com.koshg.calendar.settings.PhaseFillStyle
import com.koshg.calendar.ui.theme.Palette
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.ui.theme.previewAccent
import com.koshg.calendar.util.DEFAULT_LUTEAL_PHASE_DAYS

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
    onClose: () -> Unit
) {
    val appColors = appColors()
    val gradient = Brush.verticalGradient(listOf(appColors.gradientTop, appColors.gradientBottom))

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
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = appColors.textPrimary)
                }
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
            }

            LazyColumn(
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
                        onPhaseFillStyleChange = onPhaseFillStyleChange
                    )
                }
                item { CycleModelSection(lutealPhaseDays, onLutealPhaseDaysChange) }
                item { SuggestionsSection(suggestionsEnabled, onSuggestionsEnabledChange) }
                item { AboutSection() }
                item { Spacer(Modifier.height(24.dp)) }
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

    SectionCard(title = "Безопасность") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Блокировка приложения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary
                )
                Text(
                    "Биометрия или PIN/пароль устройства при запуске",
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

/** A row of tappable color swatches -- one per [Palette] -- picking the app's overall accent
 *  and background scheme. Phase colors (menstrual/ovulation/etc.) stay the same regardless of
 *  the chosen palette, since they carry meaning, not just decoration. */
@Composable
private fun PaletteSection(palette: Palette, onPaletteChange: (Palette) -> Unit) {
    val appColors = appColors()
    val dark = isSystemInDarkTheme()
    SectionCard(title = "Цветовая схема") {
        Text(
            "Акцент кнопок и фон календаря — цвета фаз не меняются",
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
                        p.label,
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
        // to see the effect on the actual calendar.
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
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(appColors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "14",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("Предпросмотр", style = MaterialTheme.typography.labelSmall, color = appColors.textSecondary)
            }
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(appColors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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
    onPhaseFillStyleChange: (PhaseFillStyle) -> Unit
) {
    val appColors = appColors()
    SectionCard(title = "Оформление") {
        Column {
            Text(
                "Отображение фаз",
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
                            if (style == PhaseFillStyle.FILLED) "Заливка" else "Пунктир",
                            color = if (isSelected) Color.White else appColors.textPrimary
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Адаптивная тема",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary
                )
                Text(
                    "Акцент кнопки добавления и выбранной даты плавно меняется по фазе цикла",
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
    SectionCard(title = "Подсказки") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Идеи для вечера",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary
                )
                Text(
                    "Ненавязчивая подсказка на календаре при долгом перерыве или частой усталости в отказах",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun CycleModelSection(lutealPhaseDays: Int, onLutealPhaseDaysChange: (Int) -> Unit) {
    val appColors = appColors()
    SectionCard(title = "Модель цикла") {
        CountStepper("Длина лютеиновой фазы (дн.)", lutealPhaseDays) { newValue ->
            onLutealPhaseDaysChange(newValue.coerceIn(LUTEAL_PHASE_DAYS_RANGE))
        }
        Text(
            "От овуляции до начала следующих месячных. По умолчанию $DEFAULT_LUTEAL_PHASE_DAYS дн. — " +
                "измените, только если знаете свою норму по факту.",
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

    SectionCard(title = "О приложении") {
        Text("Календарь", style = MaterialTheme.typography.bodyMedium, color = appColors.textPrimary)
        Text("Версия $versionName", style = MaterialTheme.typography.bodySmall, color = appColors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Все данные (циклы, близость, события) хранятся только на этом устройстве и никуда не отправляются.",
            style = MaterialTheme.typography.bodySmall,
            color = appColors.textSecondary
        )
    }
}
