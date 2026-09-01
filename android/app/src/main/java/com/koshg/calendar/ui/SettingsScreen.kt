package com.koshg.calendar.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koshg.calendar.security.AppLockPreferences
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.util.DEFAULT_LUTEAL_PHASE_DAYS

/** Sensible bounds for a user-supplied luteal-phase length -- outside this the ovulation estimate stops being meaningful. */
private val LUTEAL_PHASE_DAYS_RANGE = 8..20

@Composable
fun SettingsScreen(
    lutealPhaseDays: Int,
    onLutealPhaseDaysChange: (Int) -> Unit,
    adaptiveTheme: Boolean,
    onAdaptiveThemeChange: (Boolean) -> Unit,
    gradientDayFill: Boolean,
    onGradientDayFillChange: (Boolean) -> Unit,
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
                item {
                    AppearanceSection(
                        adaptiveTheme = adaptiveTheme,
                        onAdaptiveThemeChange = onAdaptiveThemeChange,
                        gradientDayFill = gradientDayFill,
                        onGradientDayFillChange = onGradientDayFillChange
                    )
                }
                item { CycleModelSection(lutealPhaseDays, onLutealPhaseDaysChange) }
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

@Composable
private fun AppearanceSection(
    adaptiveTheme: Boolean,
    onAdaptiveThemeChange: (Boolean) -> Unit,
    gradientDayFill: Boolean,
    onGradientDayFillChange: (Boolean) -> Unit
) {
    val appColors = appColors()
    SectionCard(title = "Оформление") {
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Градиентная заливка дней",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary
                )
                Text(
                    "Внутри одной фазы цвет дня слегка меняется от начала к концу периода",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary
                )
            }
            Switch(checked = gradientDayFill, onCheckedChange = onGradientDayFillChange)
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
