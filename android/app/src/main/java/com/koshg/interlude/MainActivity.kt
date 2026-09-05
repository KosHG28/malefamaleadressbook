package com.koshg.interlude

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.koshg.interlude.data.AppDatabase
import com.koshg.interlude.data.CycleRepository
import com.koshg.interlude.data.EventRepository
import com.koshg.interlude.data.IntimacyRepository
import com.koshg.interlude.diagnostics.CrashLog
import com.koshg.interlude.haptics.ProvideHaptics
import com.koshg.interlude.security.AppLockGate
import com.koshg.interlude.settings.CyclePreferences
import com.koshg.interlude.ui.CalendarScreen
import com.koshg.interlude.ui.CalendarViewModel
import com.koshg.interlude.ui.CycleViewModel
import com.koshg.interlude.ui.IntimacyViewModel
import com.koshg.interlude.ui.theme.CalendarAppTheme
import com.koshg.interlude.ui.theme.resolveDark

private const val SPLASH_EXIT_ANIMATION_MS = 260L

/**
 * A [FragmentActivity] (not just [androidx.activity.ComponentActivity]) because
 * [androidx.biometric.BiometricPrompt] needs a fragment host to survive configuration changes.
 */
class MainActivity : FragmentActivity() {

    private val cyclePreferences by lazy { CyclePreferences(applicationContext) }

    private val calendarViewModel: CalendarViewModel by viewModels {
        val repository = EventRepository(AppDatabase.getInstance(applicationContext).eventDao())
        CalendarViewModel.factory(repository)
    }

    private val cycleViewModel: CycleViewModel by viewModels {
        val repository = CycleRepository(AppDatabase.getInstance(applicationContext).periodDao())
        CycleViewModel.factory(repository, cyclePreferences)
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

        // Installed first thing, so a crash anywhere after this point leaves a trace behind --
        // there is no Play Console behind this build to collect one otherwise.
        CrashLog.install(this)

        // A genuine new session, not a configuration-change recreation (savedInstanceState is
        // non-null exactly when the system is restoring a recreated activity, e.g. after
        // rotation) -- recorded before cycleViewModel is first touched below, so its own
        // one-shot showExtendedFabLabel already reflects this open.
        if (savedInstanceState == null) {
            cyclePreferences.recordAppOpen()
        }

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
            val cycleState by cycleViewModel.uiState.collectAsState()
            val darkTheme = cycleState.themeMode.resolveDark(isSystemInDarkTheme())
            CalendarAppTheme(darkTheme = darkTheme) {
                ProvideHaptics {
                    AppLockGate {
                        CalendarScreen(calendarViewModel, cycleViewModel, intimacyViewModel)
                    }
                }
            }
        }
    }

    /**
     * Picking a color scheme in Settings only stores it; the matching launcher alias is switched
     * here, once the app is off screen. Doing it at pick time disabled the very alias this task
     * was launched through (MainActivity itself isn't exported), and the system tears such a task
     * down -- from the user's side, the app simply vanished mid-use.
     *
     * Skipped while merely changing configuration (rotation, folding, theme switch), since the
     * activity is coming straight back and is still, effectively, on screen.
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            cyclePreferences.syncLauncherIcon()
        }
    }
}
