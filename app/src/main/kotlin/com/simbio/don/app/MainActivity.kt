package com.simbio.don.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbio.don.app.data.toRule
import com.simbio.don.app.ui.AddEntryScreen
import com.simbio.don.app.ui.AppState
import com.simbio.don.app.ui.DonTheme
import com.simbio.don.app.ui.EntriesScreen
import com.simbio.don.app.ui.HomeScreen
import com.simbio.don.app.ui.MainViewModel
import com.simbio.don.app.ui.OnboardingScreen
import com.simbio.don.app.ui.ReviewScreen
import com.simbio.don.app.ui.RuleEditScreen
import com.simbio.don.app.ui.SalaryScreen
import com.simbio.don.app.ui.SettingsScreen
import com.simbio.don.app.ui.SettlementScreen
import java.time.LocalDate

private sealed interface Screen {
    data object Home : Screen
    data object AddEntry : Screen
    data object Entries : Screen
    data object Review : Screen
    data object Settle : Screen
    data object Salary : Screen
    data object Settings : Screen
    data class EditRule(val packageName: String) : Screen
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var listenerEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DonTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) { App() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        vm.refreshClock()
    }

    private fun openListenerSettings() = startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    private fun openBatterySettings() = startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    private fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Composable
    private fun App() {
        val state by vm.state.collectAsStateWithLifecycle()
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        val back = { screen = Screen.Home }
        BackHandler(enabled = screen != Screen.Home) {
            screen = if (screen is Screen.EditRule) Screen.Settings else Screen.Home
        }

        when (val s = state) {
            AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AppState.NeedsOnboarding -> OnboardingScreen(LocalDate.now()) { salary, payDay, fixed, savings, day, first ->
                vm.completeOnboarding(salary, payDay, fixed, savings, day, first)
            }
            is AppState.Ready -> {
                val home = s.home
                when (val sc = screen) {
                    Screen.Home -> HomeScreen(
                        home = home,
                        listenerEnabled = listenerEnabled,
                        onOpenListenerSettings = ::openListenerSettings,
                        onAddEntry = { screen = Screen.AddEntry },
                        onEntries = { screen = Screen.Entries },
                        onReview = { screen = Screen.Review },
                        onSettle = { screen = Screen.Settle },
                        onSalary = { screen = Screen.Salary },
                        onSettings = { screen = Screen.Settings },
                    )
                    Screen.AddEntry -> AddEntryScreen(home, onSave = { d, a, l, t -> vm.addManual(d, a, l, t) }, onBack = back)
                    Screen.Entries -> EntriesScreen(remember(home.cycle) { vm.entries(home) }, vm::deleteEntry, back)
                    Screen.Review -> {
                        val items by vm.reviewItems.collectAsStateWithLifecycle()
                        ReviewScreen(items, home.ledger.canAddException, { i, r -> vm.resolve(i, r) }, back)
                    }
                    Screen.Settle -> {
                        val week = home.settleable.firstOrNull()
                        if (week == null) LaunchedEffect(Unit) { back() } else SettlementScreen(week, onDone = { vm.settle(week) }, onBack = back)
                    }
                    Screen.Salary -> SalaryScreen(home, { vm.suggestedSalary(home) }, { a, d -> vm.confirmSalary(a, d) }, back)
                    Screen.Settings -> {
                        val settings by vm.settings.collectAsStateWithLifecycle()
                        val fixed by vm.fixedExpenses.collectAsStateWithLifecycle()
                        val rules by vm.appRules.collectAsStateWithLifecycle()
                        val seen by vm.seenApps.collectAsStateWithLifecycle()
                        settings?.let {
                            SettingsScreen(
                                settings = it,
                                fixed = fixed,
                                rules = rules,
                                seenApps = seen,
                                listenerEnabled = listenerEnabled,
                                appLabel = ::appLabel,
                                onOpenListenerSettings = ::openListenerSettings,
                                onOpenBatterySettings = ::openBatterySettings,
                                onSaveBudget = { f, sv, d, now -> vm.updateSettings(f, sv, d, now) },
                                onEditRule = { pkg -> screen = Screen.EditRule(pkg) },
                                onSaveRule = { r -> vm.saveRule(r) },
                                onBack = back,
                            )
                        }
                    }
                    is Screen.EditRule -> {
                        val rules by vm.appRules.collectAsStateWithLifecycle()
                        val toSettings = { screen = Screen.Settings }
                        rules.firstOrNull { it.packageName == sc.packageName }?.let { r ->
                            RuleEditScreen(r.toRule(), vm::saveRule, { vm.deleteRule(r.packageName) }, toSettings)
                        }
                    }
                }
            }
        }
    }
}
