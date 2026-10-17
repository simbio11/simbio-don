package com.simbio.don.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.toRule
import com.simbio.don.app.ui.AppState
import com.simbio.don.app.ui.DonColors
import com.simbio.don.app.ui.DonTheme
import com.simbio.don.app.ui.kit.AddFab
import com.simbio.don.app.ui.kit.TabBar
import com.simbio.don.app.ui.kit.TabItem
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.EntryEditScreen
import com.simbio.don.app.ui.HistoryScreen
import com.simbio.don.app.ui.HomeScreen
import com.simbio.don.app.ui.HouseScreen
import com.simbio.don.app.ui.MockShopScreen
import com.simbio.don.app.ui.MainViewModel
import com.simbio.don.app.ui.OnboardingScreen
import com.simbio.don.app.ui.ReviewScreen
import com.simbio.don.app.ui.RuleEditScreen
import com.simbio.don.app.ui.SalaryScreen
import com.simbio.don.app.ui.SettingsScreen
import com.simbio.don.app.ui.SettlementScreen
import com.simbio.don.app.ui.StatsScreen
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.game.SharedProduct
import com.simbio.don.game.SharedTextParser
import java.time.DayOfWeek
import java.time.LocalDate

private enum class Tab(val label: String, val icon: UiIcon, val selectedIcon: UiIcon) {
    Home("홈", UiIcon.HOME, UiIcon.HOME_ON),
    History("내역", UiIcon.LIST, UiIcon.LIST_ON),
    House("내 집", UiIcon.HOUSE, UiIcon.HOUSE_ON),
    Stats("통계", UiIcon.CHART, UiIcon.CHART_ON),
    Settings("설정", UiIcon.GEAR, UiIcon.GEAR_ON),
}

/** 탭 위에 덮어 여는 화면 */
private sealed interface Overlay {
    data class Edit(val existing: EntryEntity?) : Overlay
    data object Review : Overlay
    data object Settle : Overlay
    data object Salary : Overlay
    data class EditRule(val packageName: String) : Overlay
    data class Shop(val prefill: SharedProduct?) : Overlay
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var listenerEnabled by mutableStateOf(false)

    /** 쇼핑 앱에서 "공유"로 받은 글 — 모의 쇼핑 화면을 연다 */
    private var shared by mutableStateOf<SharedProduct?>(null)

    private fun readShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT)).joinToString("\n")
            if (text.isNotBlank()) shared = SharedTextParser.parse(text)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShare(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readShare(intent)
        setContent {
            DonTheme {
                Surface(Modifier.fillMaxSize(), color = DonColors.Bg) {
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
        when (val s = state) {
            AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AppState.NeedsOnboarding -> OnboardingScreen(LocalDate.now()) { salary, payDay, fixed, savings, day, first ->
                vm.completeOnboarding(salary, payDay, fixed, savings, day, first)
            }
            is AppState.Ready -> Main(s.home)
        }
    }

    @Composable
    private fun Main(home: HomeState) {
        var tab by remember { mutableStateOf(Tab.Home) }
        var overlay by remember { mutableStateOf<Overlay?>(null) }
        var historyDate by remember { mutableStateOf<LocalDate?>(null) }
        var historyCategory by remember { mutableStateOf<Category?>(null) }
        val close = { overlay = null }
        LaunchedEffect(shared) {
            shared?.let { overlay = Overlay.Shop(it); shared = null }
        }

        BackHandler(enabled = overlay != null || tab != Tab.Home) {
            when {
                overlay is Overlay.EditRule -> overlay = null
                overlay != null -> overlay = null
                else -> tab = Tab.Home
            }
        }

        overlay?.let { o ->
            Overlay(o, home, close, open = { overlay = it }, goHouse = { overlay = null; tab = Tab.House })
            return
        }

        Scaffold(
            containerColor = DonColors.Bg,
            bottomBar = {
                TabBar(
                    items = Tab.entries.map { TabItem(it.label, it.icon, it.selectedIcon) },
                    selected = tab.ordinal,
                ) { i ->
                    val t = Tab.entries[i]
                    if (t == Tab.History || t == Tab.Stats) {
                        if (tab != Tab.History && tab != Tab.Stats) vm.resetMonth()
                    }
                    tab = t
                }
            },
            floatingActionButton = {
                if (tab == Tab.Home || tab == Tab.History) {
                    AddFab(onClick = { overlay = Overlay.Edit(null) }, contentDescription = "지출 입력")
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        home = home,
                        listenerEnabled = listenerEnabled,
                        onOpenListenerSettings = ::openListenerSettings,
                        onReview = { overlay = Overlay.Review },
                        onSettle = { overlay = Overlay.Settle },
                        onSalary = { overlay = Overlay.Salary },
                        onOpenEntry = { overlay = Overlay.Edit(it) },
                        onAllEntries = { vm.resetMonth(); historyDate = null; historyCategory = null; tab = Tab.History },
                        onStats = { vm.resetMonth(); tab = Tab.Stats },
                        onHouse = { tab = Tab.House },
                        onShop = { overlay = Overlay.Shop(null) },
                    )
                    Tab.House -> HouseScreen(
                        home = home,
                        onShop = { overlay = Overlay.Shop(null) },
                        onRecycle = { vm.recycle() },
                        onDiscard = { vm.discard(it) },
                    )
                    Tab.History, Tab.Stats -> {
                        val view by vm.month.collectAsStateWithLifecycle()
                        val cycles by vm.cycles.collectAsStateWithLifecycle()
                        val v = view ?: return@Box
                        val i = cycles.indexOfFirst { it.nominal == v.nominal }
                        val hasPrev = i in 0 until cycles.lastIndex
                        val hasNext = i > 0
                        val weekStart = cycles.getOrNull(i)?.let { DayOfWeek.of(it.settlementDay) } ?: DayOfWeek.MONDAY
                        if (tab == Tab.History) {
                            HistoryScreen(
                                view = v,
                                weekStart = weekStart,
                                hasPrev = hasPrev,
                                hasNext = hasNext,
                                onPrev = { vm.moveMonth(-1); historyDate = null },
                                onNext = { vm.moveMonth(1); historyDate = null },
                                selectedDate = historyDate,
                                onSelectDate = { historyDate = it },
                                category = historyCategory,
                                onCategory = { historyCategory = it },
                                onOpen = { overlay = Overlay.Edit(it) },
                            )
                        } else {
                            StatsScreen(
                                view = v,
                                hasPrev = hasPrev,
                                hasNext = hasNext,
                                onPrev = { vm.moveMonth(-1) },
                                onNext = { vm.moveMonth(1) },
                                onCategory = { c -> historyCategory = c; historyDate = null; tab = Tab.History },
                            )
                        }
                    }
                    Tab.Settings -> SettingsTab { overlay = it }
                }
            }
        }
    }

    @Composable
    private fun SettingsTab(open: (Overlay) -> Unit) {
        val settings by vm.settings.collectAsStateWithLifecycle()
        val fixed by vm.fixedExpenses.collectAsStateWithLifecycle()
        val rules by vm.appRules.collectAsStateWithLifecycle()
        val seen by vm.seenApps.collectAsStateWithLifecycle()
        val learned by vm.learned.collectAsStateWithLifecycle()
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                vm.exportCsv(uri)
                Toast.makeText(this, "내보냈어요", Toast.LENGTH_SHORT).show()
            }
        }
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
                onEditRule = { pkg -> open(Overlay.EditRule(pkg)) },
                onSaveRule = { r -> vm.saveRule(r) },
                learnedCount = learned,
                onResetLearned = { vm.resetLearned() },
                onExport = { export.launch("돈아끼는게임_내역_${LocalDate.now()}.csv") },
            )
        }
    }

    @Composable
    private fun Overlay(o: Overlay, home: HomeState, close: () -> Unit, open: (Overlay) -> Unit, goHouse: () -> Unit) {
        when (o) {
            is Overlay.Edit -> {
                val learned by produceState<Map<String, Category>?>(null) { value = vm.learnedMap() }
                val month by vm.month.collectAsStateWithLifecycle()
                // 지난달 기록을 고칠 때는 그 달 기준으로
                val existingDay = o.existing?.epochDay?.let { LocalDate.ofEpochDay(it) }
                val m = month?.takeIf { existingDay != null && existingDay in it.cycle }
                val cycle = m?.cycle ?: home.cycle
                val exceptions = m?.entries?.count { it.type == EntryType.EXCEPTION } ?: home.ledger.exceptionCount
                learned?.let { l ->
                    EntryEditScreen(
                        cycle = cycle,
                        today = home.today,
                        exceptionCount = exceptions,
                        canAddException = exceptions < 2,
                        learned = l,
                        existing = o.existing,
                        onSave = { d -> vm.save(d, o.existing) },
                        onDelete = o.existing?.let { e -> { vm.deleteEntry(e) } },
                        onBack = close,
                    )
                }
            }
            Overlay.Review -> {
                val items by vm.reviewItems.collectAsStateWithLifecycle()
                ReviewScreen(items, home.ledger.canAddException, { i, r -> vm.resolve(i, r) }, close)
            }
            Overlay.Settle -> {
                val week = home.settleable.firstOrNull()
                if (week == null) {
                    LaunchedEffect(Unit) { close() }
                } else {
                    SettlementScreen(week, home.mockItems, onDone = { ids -> vm.settle(week, ids) }, onBack = close)
                }
            }
            Overlay.Salary -> SalaryScreen(home, { vm.suggestedSalary(home) }, { a, d -> vm.confirmSalary(a, d) }, close)
            is Overlay.Shop -> MockShopScreen(
                wallet = home.wallet,
                prefill = o.prefill,
                onBuy = { n, p, k, st -> vm.buyMock(n, p, k, st) },
                onHouse = goHouse,
                onBack = close,
            )
            is Overlay.EditRule -> {
                val rules by vm.appRules.collectAsStateWithLifecycle()
                rules.firstOrNull { it.packageName == o.packageName }?.let { r ->
                    RuleEditScreen(r.toRule(), vm::saveRule, { vm.deleteRule(r.packageName) }, close)
                }
            }
        }
    }
}
