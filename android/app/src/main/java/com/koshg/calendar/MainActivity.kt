package com.koshg.calendar

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.koshg.calendar.data.AppDatabase
import com.koshg.calendar.data.CycleRepository
import com.koshg.calendar.data.EventRepository
import com.koshg.calendar.data.IntimacyRepository
import com.koshg.calendar.haptics.ProvideHaptics
import com.koshg.calendar.security.AppLockGate
import com.koshg.calendar.settings.CyclePreferences
import com.koshg.calendar.ui.CalendarScreen
import com.koshg.calendar.ui.CalendarViewModel
import com.koshg.calendar.ui.CycleViewModel
import com.koshg.calendar.ui.IntimacyViewModel
import com.koshg.calendar.ui.theme.CalendarAppTheme

private const val SPLASH_EXIT_ANIMATION_MS = 260L

/**
 * A [FragmentActivity] (not just [androidx.activity.ComponentActivity]) because
 * [androidx.biometric.BiometricPrompt] needs a fragment host to survive configuration changes.
 */
class MainActivity : FragmentActivity() {

    private val calendarViewModel: CalendarViewModel by viewModels {
        val repository = EventRepository(AppDatabase.getInstance(applicationContext).eventDao())
        CalendarViewModel.factory(repository)
    }

    private val cycleViewModel: CycleViewModel by viewModels {
        val repository = CycleRepository(AppDatabase.getInstance(applicationContext).periodDao())
        CycleViewModel.factory(repository, CyclePreferences(applicationContext))
    }

    private val intimacyViewModel: IntimacyViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        val repository = IntimacyRepository(db.sexDao(), db.proposalDao(), db.masturbationDao())
        IntimacyViewModel.factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() so the system splash screen actually attaches.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // A brief zoom-and-fade out of the app icon on exit, rather than the platform's abrupt
        // default cut, so cold start reads as one continuous "loading" motion into the app.
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOut = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)
            val scaleX = ObjectAnimator.ofFloat(splashScreenView.iconView, View.SCALE_X, 1f, 1.15f)
            val scaleY = ObjectAnimator.ofFloat(splashScreenView.iconView, View.SCALE_Y, 1f, 1.15f)
            AnimatorSet().apply {
                playTogether(fadeOut, scaleX, scaleY)
                duration = SPLASH_EXIT_ANIMATION_MS
                interpolator = AccelerateInterpolator()
                doOnEnd { splashScreenView.remove() }
                start()
            }
        }

        setContent {
            CalendarAppTheme {
                ProvideHaptics {
                    AppLockGate {
                        CalendarScreen(calendarViewModel, cycleViewModel, intimacyViewModel)
                    }
                }
            }
        }
    }
}
