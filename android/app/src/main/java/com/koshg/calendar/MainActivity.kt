package com.koshg.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.koshg.calendar.data.AppDatabase
import com.koshg.calendar.data.EventRepository
import com.koshg.calendar.ui.CalendarScreen
import com.koshg.calendar.ui.CalendarViewModel
import com.koshg.calendar.ui.theme.CalendarAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CalendarViewModel by viewModels {
        val repository = EventRepository(AppDatabase.getInstance(applicationContext).eventDao())
        CalendarViewModel.factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalendarAppTheme {
                CalendarScreen(viewModel)
            }
        }
    }
}
