package com.koshg.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics

private enum class AppTab(val label: String) {
    Calendar("Календарь"),
    Cycle("Цикл"),
    Intimacy("Близость")
}

@Composable
fun AppRoot(
    calendarViewModel: CalendarViewModel,
    cycleViewModel: CycleViewModel,
    intimacyViewModel: IntimacyViewModel
) {
    var tab by remember { mutableStateOf(AppTab.Calendar) }
    val haptics = LocalHaptics.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = {
                            if (tab != entry) {
                                haptics.perform(HapticEvent.Tap)
                                tab = entry
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (entry) {
                                    AppTab.Calendar -> Icons.Filled.Today
                                    AppTab.Cycle -> Icons.Filled.DateRange
                                    AppTab.Intimacy -> Icons.Filled.Favorite
                                },
                                contentDescription = entry.label
                            )
                        },
                        label = { Text(entry.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                AppTab.Calendar -> CalendarScreen(calendarViewModel, cycleViewModel, intimacyViewModel)
                AppTab.Cycle -> CycleScreen(cycleViewModel)
                AppTab.Intimacy -> IntimacyScreen(intimacyViewModel)
            }
        }
    }
}
