package com.koshg.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.koshg.calendar.data.AppDatabase
import com.koshg.calendar.data.CycleRepository
import com.koshg.calendar.data.EventRepository
import com.koshg.calendar.data.IntimacyRepository
import com.koshg.calendar.haptics.ProvideHaptics
import com.koshg.calendar.ui.CalendarScreen
import com.koshg.calendar.ui.CalendarViewModel
import com.koshg.calendar.ui.CycleViewModel
import com.koshg.calendar.ui.IntimacyViewModel
import com.koshg.calendar.ui.theme.CalendarAppTheme

class MainActivity : ComponentActivity() {

    private val calendarViewModel: CalendarViewModel by viewModels {
        val repository = EventRepository(AppDatabase.getInstance(applicationContext).eventDao())
        CalendarViewModel.factory(repository)
    }

    private val cycleViewModel: CycleViewModel by viewModels {
        val repository = CycleRepository(AppDatabase.getInstance(applicationContext).periodDao())
        CycleViewModel.factory(repository)
    }

    private val intimacyViewModel: IntimacyViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        val repository = IntimacyRepository(db.sexDao(), db.proposalDao(), db.masturbationDao())
        IntimacyViewModel.factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalendarAppTheme {
                ProvideHaptics {
                    CalendarScreen(calendarViewModel, cycleViewModel, intimacyViewModel)
                }
            }
        }
    }
}
