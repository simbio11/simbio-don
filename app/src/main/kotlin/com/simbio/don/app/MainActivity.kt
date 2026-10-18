package com.simbio.don.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.QuickPick
import com.simbio.don.app.data.toRule
import com.simbio.don.app.notify.DailyReminder
import com.simbio.don.app.ui.AccountDetailScreen
import com.simbio.don.app.ui.AccountEditScreen
import com.simbio.don.app.ui.AppState
import com.simbio.don.app.ui.AssetsScreen
import com.simbio.don.app.ui.BudgetsScreen
import com.simbio.don.app.ui.DonColors
import com.simbio.don.app.ui.DonTheme
import com.simbio.don.app.ui.DressScreen
import com.simbio.don.app.ui.EntryDraft
import com.simbio.don.app.ui.EntryEditScreen
import com.simbio.don.app.ui.GoalsScreen
import com.simbio.don.app.ui.HomeScreen
import com.simbio.don.app.ui.HouseScreen
import com.simbio.don.app.ui.LedgerMode
import com.simbio.don.app.ui.LedgerScreen
import com.simbio.don.app.ui.MainViewModel
import com.simbio.don.app.ui.MockShopScreen
import com.simbio.don.app.ui.OnboardingScreen
import com.simbio.don.app.ui.ReviewScreen
import com.simbio.don.app.ui.RuleEditScreen
import com.simbio.don.app.ui.SalaryScreen
import com.simbio.don.app.ui.SearchScreen
import com.simbio.don.app.ui.SettingsScreen
import com.simbio.don.app.ui.SettlementScreen
import com.simbio.don.app.ui.StatsScreen
import com.simbio.don.app.ui.kit.AddFab
import com.simbio.don.app.ui.kit.LocalCharacterLook
import com.simbio.don.app.ui.kit.TabBar
import com.simbio.don.app.ui.kit.TabItem
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.weather.WeatherService
import com.simbio.don.app.widget.WeekWidget
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.game.SharedProduct
import com.simbio.don.game.SharedTextParser
import java.time.DayOfWeek
import java.time.LocalDate

private enum class Tab(val label: String, val icon: UiIcon, val selectedIcon: UiIcon) {
    Home("홈", UiIcon.HOME, UiIcon.HOME_ON),
    Ledger("가계부", UiIcon.LIST, UiIcon.LIST_ON),
    Stats("통계", UiIcon.PIE, UiIcon.PIE_ON),
    Assets("자산", UiIcon.BANK, UiIcon.BANK_ON),
    House("내 집", UiIcon.HOUSE, UiIcon.HOUSE_ON),
}

/** 탭 위에 덮어 여는 화면 */
private sealed interface Overlay {
    data class Edit(val existing: EntryEntity?, val initial: EntryDraft? = null) : Overlay
    data object Review : Overlay
    data object Settle : Overlay
    data object Salary : Overlay
    data class EditRule(val packageName: String) : Overlay
    data class Shop(val prefill: SharedProduct?) : Overlay
    data object Budgets : Overlay
    data object Goals : Overlay
    data object Settings : Overlay
    data object Search : Overlay
    data class Account(val id: Long) : Overlay
    data class EditAccount(val id: Long?) : Overlay
    data object Dress : Overlay
}

class MainActivity : ComponentActivity() {
    companion object {
        /** 위젯 + 버튼 → 지출 입력 */
        const val ACTION_ADD = "com.simbio.don.action.ADD"
    }

    private val vm: MainViewModel by viewModels()
    private var listenerEnabled by mutableStateOf(false)

    /** 쇼핑 앱에서 "공유"로 받은 글 — 모의 쇼핑 화면을 연다 */
    private var shared by mutableStateOf<SharedProduct?>(null)

    /** 위젯의 + 버튼 — 지출 입력을 바로 연다 */
    private var addRequested by mutableStateOf(false)

    private fun readShare(intent: Intent?) {
        if (intent?.action == ACTION_ADD) addRequested = true
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
        if (DailyReminder.isOn(this)) DailyReminder.schedule(this)
        setContent {
            DonTheme {
                Surface(Modifier.fillMaxSize(), color = DonColors.Bg) {
                    Box(Modifier.safeDrawingPadding()) { App() }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        WeekWidget.refresh(this)
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
            AppState.NeedsOnboarding -> OnboardingScreen(LocalDate.now(), listenerEnabled, ::openListenerSettings) { vm.completeOnboarding(it) }
            is AppState.Ready -> CompositionLocalProvider(LocalCharacterLook provides s.home.look) { Main(s.home) }
        }
    }

    @Composable
    private fun Main(home: HomeState) {
        var tab by remember { mutableStateOf(Tab.Home) }
        var overlay by remember { mutableStateOf<Overlay?>(null) }
        // 화면 위에서 연 화면(자산 상세 → 이체, 설정 → 월급, 검색 → 기록)을 닫으면 그 화면으로 돌아간다
        var overlayBack by remember { mutableStateOf<Overlay?>(null) }
        var ledgerDate by remember { mutableStateOf<LocalDate?>(null) }
        var ledgerCategory by remember { mutableStateOf<Category?>(null) }
        var ledgerMode by rememberSaveable { mutableStateOf(LedgerMode.DAILY) }
        val close = { overlay = overlayBack; overlayBack = null }
        LaunchedEffect(shared) {
            shared?.let { overlayBack = null; overlay = Overlay.Shop(it); shared = null }
        }
        LaunchedEffect(addRequested) {
            if (addRequested) {
                overlayBack = null
                overlay = Overlay.Edit(null)
                addRequested = false
            }
        }
        val weather by vm.weather.collectAsStateWithLifecycle()
        // 지운 기록 되돌리기
        val snackbar = remember { SnackbarHostState() }
        val deleted by vm.deleted.collectAsStateWithLifecycle()
        LaunchedEffect(deleted) {
            val e = deleted ?: return@LaunchedEffect
            val r = snackbar.showSnackbar("'${e.label.ifBlank { "지출" }}' 기록을 지웠어요", actionLabel = "되돌리기", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearDeleted()
        }

        BackHandler(enabled = overlay != null || tab != Tab.Home) {
            when {
                overlay != null -> close()
                else -> tab = Tab.Home
            }
        }

        overlay?.let { o ->
            Overlay(o, home, close, open = { next -> overlayBack = o.takeIf { it !is Overlay.Edit }; overlay = next }, goHouse = { overlayBack = null; overlay = null; tab = Tab.House })
            return
        }

        val names = home.accounts.associate { it.account.id to it.account.name }
        Scaffold(
            containerColor = DonColors.Bg,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        data,
                        shape = RoundedCornerShape(14.dp),
                        containerColor = DonColors.Ink,
                        contentColor = Color.White,
                        actionColor = Color(0xFF8FB0FF),
                    )
                }
            },
            bottomBar = {
                TabBar(
                    items = Tab.entries.map { TabItem(it.label, it.icon, it.selectedIcon) },
                    selected = tab.ordinal,
                ) { i ->
                    val t = Tab.entries[i]
                    if (t == Tab.Ledger || t == Tab.Stats) {
                        if (tab != Tab.Ledger && tab != Tab.Stats) vm.resetMonth()
                    }
                    tab = t
                }
            },
            floatingActionButton = {
                if (tab == Tab.Home || tab == Tab.Ledger) {
                    AddFab(
                        onClick = {
                            val d = ledgerDate.takeIf { tab == Tab.Ledger && ledgerMode == LedgerMode.CALENDAR }
                            overlay = Overlay.Edit(null, d?.let { EntryDraft(it, 0, "", EntryType.SPEND, Category.ETC, "", false) })
                        },
                        contentDescription = "가계부 쓰기",
                    )
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
                        onDismissSalary = { vm.dismissSalaryNotice() },
                        onSettings = { overlay = Overlay.Settings },
                        onOpenEntry = { overlay = Overlay.Edit(it) },
                        onAllEntries = { vm.resetMonth(); ledgerDate = null; ledgerCategory = null; ledgerMode = LedgerMode.DAILY; tab = Tab.Ledger },
                        onStats = { vm.resetMonth(); tab = Tab.Stats },
                        onHouse = { tab = Tab.House },
                        onShop = { overlay = Overlay.Shop(null) },
                        onBudgets = { overlay = Overlay.Budgets },
                        onGoals = { overlay = Overlay.Goals },
                        weather = weather,
                    )
                    Tab.House -> HouseScreen(
                        home = home,
                        onShop = { overlay = Overlay.Shop(null) },
                        onDress = { overlay = Overlay.Dress },
                        onRecycle = { vm.recycle() },
                        onDiscard = { vm.discard(it) },
                        onBuyWish = { vm.buyWished(it.id) },
                        onGiveUpWish = { vm.giveUpWished(it.id) },
                        weather = weather,
                        city = WeatherService.city(this@MainActivity)?.name,
                    )
                    Tab.Assets -> AssetsScreen(
                        balances = home.accounts,
                        onOpen = { overlay = Overlay.Account(it.id) },
                        onAdd = { overlay = Overlay.EditAccount(null) },
                        onTransfer = { overlay = Overlay.Edit(null, transferDraft(home, null)) },
                    )
                    Tab.Ledger, Tab.Stats -> {
                        val view by vm.month.collectAsStateWithLifecycle()
                        val cycles by vm.cycles.collectAsStateWithLifecycle()
                        val summaries by vm.summaries.collectAsStateWithLifecycle()
                        val v = view ?: return@Box
                        val i = cycles.indexOfFirst { it.nominal == v.nominal }
                        val hasPrev = i in 0 until cycles.lastIndex
                        val hasNext = i > 0
                        if (tab == Tab.Ledger) {
                            LedgerScreen(
                                view = v,
                                summaries = summaries,
                                accountNames = names,
                                hasPrev = hasPrev,
                                hasNext = hasNext,
                                onPrev = { vm.moveMonth(-1); ledgerDate = null },
                                onNext = { vm.moveMonth(1); ledgerDate = null },
                                onPickMonth = { vm.pickMonth(it); ledgerDate = null },
                                selectedDate = ledgerDate,
                                onSelectDate = { ledgerDate = it },
                                category = ledgerCategory,
                                onCategory = { ledgerCategory = it },
                                onOpen = { overlay = Overlay.Edit(it) },
                                onSearch = { overlay = Overlay.Search },
                                mode = ledgerMode,
                                onMode = { ledgerMode = it },
                            )
                        } else {
                            StatsScreen(
                                view = v,
                                hasPrev = hasPrev,
                                hasNext = hasNext,
                                onPrev = { vm.moveMonth(-1) },
                                onNext = { vm.moveMonth(1) },
                                onCategory = { c -> ledgerCategory = c; ledgerDate = null; ledgerMode = LedgerMode.DAILY; tab = Tab.Ledger },
                                summaries = summaries,
                                onBudgets = { overlay = Overlay.Budgets },
                            )
                        }
                    }
                }
            }
        }
    }

    /** 이체 입력 초안: [from]에서 (카드면 통장 → 카드) */
    private fun transferDraft(home: HomeState, from: com.simbio.don.app.data.AccountEntity?): EntryDraft {
        val shown = home.accounts.filter { !it.account.hidden }.map { it.account }
        val card = from?.takeIf { it.type == com.simbio.don.app.data.AccountType.CARD.name }
        val bank = shown.firstOrNull { it.type == com.simbio.don.app.data.AccountType.BANK.name }
        val (a, b) = when {
            card != null -> bank?.id to card.id
            from != null -> from.id to shown.firstOrNull { it.id != from.id }?.id
            else -> bank?.id to shown.firstOrNull { it.type == com.simbio.don.app.data.AccountType.CASH.name }?.id
        }
        return EntryDraft(home.today, 0, if (card != null) "카드값" else "", EntryType.TRANSFER, Category.ETC, "", false, a, b)
    }

    @Composable
    private fun SettingsOverlay(home: HomeState, open: (Overlay) -> Unit, onBack: () -> Unit) {
        val settings by vm.settings.collectAsStateWithLifecycle()
        val fixed by vm.fixedExpenses.collectAsStateWithLifecycle()
        val rules by vm.appRules.collectAsStateWithLifecycle()
        val seen by vm.seenApps.collectAsStateWithLifecycle()
        val learned by vm.learned.collectAsStateWithLifecycle()
        var reminderOn by remember { mutableStateOf(DailyReminder.isOn(this)) }
        var weatherCity by remember { mutableStateOf(WeatherService.city(this)?.name) }
        val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                DailyReminder.setOn(this, true)
                reminderOn = true
            } else {
                Toast.makeText(this, "알림 권한을 허용해야 켤 수 있어요", Toast.LENGTH_SHORT).show()
            }
        }
        val widgets = AppWidgetManager.getInstance(this)
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                vm.exportCsv(uri)
                Toast.makeText(this, "내보냈어요", Toast.LENGTH_SHORT).show()
            }
        }
        val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                vm.exportBackup(uri) { ok -> Toast.makeText(this, if (ok) "백업 파일을 만들었어요" else "백업하지 못했어요", Toast.LENGTH_SHORT).show() }
            }
        }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                vm.restoreBackup(uri) { error -> Toast.makeText(this, error ?: "복원했어요", Toast.LENGTH_LONG).show() }
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
                onSaveBudget = { f, sv, d, now, pd ->
                    vm.updateSettings(f, sv, d, now, pd)
                    Toast.makeText(this, "저장했어요", Toast.LENGTH_SHORT).show()
                },
                onEditRule = { pkg -> open(Overlay.EditRule(pkg)) },
                onBudgets = { open(Overlay.Budgets) },
                onSaveRule = { r -> vm.saveRule(r) },
                learnedCount = learned,
                onResetLearned = { vm.resetLearned() },
                onExport = { export.launch("돈트_내역_${LocalDate.now()}.csv") },
                reminderOn = reminderOn,
                onReminder = { on ->
                    if (on && !DailyReminder.canNotify(this) && Build.VERSION.SDK_INT >= 33) {
                        askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        DailyReminder.setOn(this, on)
                        reminderOn = on
                    }
                },
                onPinWidget = if (widgets.isRequestPinAppWidgetSupported) {
                    { widgets.requestPinAppWidget(ComponentName(this, WeekWidget::class.java), null, null) }
                } else {
                    null
                },
                version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty(),
                weatherCity = weatherCity,
                onWeatherCity = { name ->
                    vm.setWeatherCity(name)
                    weatherCity = name
                },
                salary = home.salary,
                salarySource = home.salarySource,
                onSalary = { open(Overlay.Salary) },
                onBackup = { backup.launch("돈트_백업_${LocalDate.now()}.json") },
                onRestore = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                onBack = onBack,
            )
        }
    }

    @Composable
    private fun Overlay(o: Overlay, home: HomeState, close: () -> Unit, open: (Overlay) -> Unit, goHouse: () -> Unit) {
        when (o) {
            is Overlay.Edit -> {
                val learned by produceState<Map<String, Category>?>(null) { value = vm.learnedMap() }
                val picks by produceState(emptyList<QuickPick>()) { value = vm.quickPicks() }
                val month by vm.month.collectAsStateWithLifecycle()
                val cycles by vm.cycles.collectAsStateWithLifecycle()
                // 지난달 기록을 고칠 때는 그 달 기준으로
                val existingDay = o.existing?.epochDay?.let { LocalDate.ofEpochDay(it) }
                val m = month?.takeIf { existingDay != null && existingDay in it.cycle }
                val exceptions = m?.entries?.count { it.type == EntryType.EXCEPTION } ?: home.ledger.exceptionCount
                val first = cycles.lastOrNull()?.let { LocalDate.ofEpochDay(it.startEpochDay) } ?: home.cycle.start
                learned?.let { l ->
                    EntryEditScreen(
                        minDate = minOf(first, existingDay ?: first),
                        maxDate = maxOf(home.cycle.end, existingDay ?: home.cycle.end),
                        today = home.today,
                        exceptionCount = exceptions,
                        canAddException = exceptions < 2,
                        learned = l,
                        existing = o.existing,
                        accounts = home.accounts.map { it.account },
                        picks = picks,
                        // 새로 적을 때 결제 수단은 마지막으로 직접 적은 기록과 같게
                        initial = o.initial ?: if (o.existing == null) {
                            home.entries.filter { it.source == com.simbio.don.budget.EntrySource.MANUAL.name && it.accountId != null && it.type != EntryType.TRANSFER.name }
                                .maxByOrNull { it.id }?.accountId
                                ?.let { EntryDraft(home.today, 0, "", EntryType.SPEND, Category.ETC, "", false, accountId = it) }
                        } else {
                            null
                        },
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
                    SettlementScreen(
                        week.result,
                        home.mockItems,
                        onDone = { ids -> vm.settle(week, ids) },
                        onBack = close,
                        streakBefore = home.streak,
                        savedTotal = home.savedTotal,
                    )
                }
            }
            Overlay.Salary -> SalaryScreen(home, { vm.updateSalary(it) }, close)
            Overlay.Settings -> SettingsOverlay(home, open, close)
            Overlay.Search -> {
                val q by vm.searchQuery.collectAsStateWithLifecycle()
                val results by vm.searchResults.collectAsStateWithLifecycle()
                SearchScreen(
                    query = q,
                    onQuery = { vm.searchQuery.value = it },
                    results = results,
                    accountNames = home.accounts.associate { it.account.id to it.account.name },
                    onOpen = { open(Overlay.Edit(it)) },
                    onBack = close,
                )
            }
            is Overlay.Account -> {
                val b = home.accounts.firstOrNull { it.account.id == o.id }
                if (b == null) {
                    LaunchedEffect(Unit) { close() }
                } else {
                    val entries by remember(o.id) { vm.entriesOfAccount(o.id) }.collectAsStateWithLifecycle(emptyList())
                    AccountDetailScreen(
                        balance = b,
                        entries = entries,
                        accountNames = home.accounts.associate { it.account.id to it.account.name },
                        onEdit = { open(Overlay.EditAccount(o.id)) },
                        onTransfer = { open(Overlay.Edit(null, transferDraft(home, b.account))) },
                        onOpen = { open(Overlay.Edit(it)) },
                        onBack = close,
                    )
                }
            }
            is Overlay.EditAccount -> {
                val rules by vm.appRules.collectAsStateWithLifecycle()
                val existing = o.id?.let { id -> home.accounts.firstOrNull { it.account.id == id }?.account }
                AccountEditScreen(
                    existing = existing,
                    rules = rules,
                    onSave = { vm.saveAccount(it) },
                    onDelete = existing?.let { a -> { vm.deleteAccount(a) } },
                    onBack = close,
                )
            }
            is Overlay.Shop -> MockShopScreen(
                wallet = home.wallet,
                prefill = o.prefill,
                onBuy = { n, p, k, st -> vm.buyMock(n, p, k, st) },
                onWish = { n, p, k, st -> vm.wishMock(n, p, k, st) },
                onHouse = goHouse,
                onBack = close,
            )
            Overlay.Budgets -> {
                val budgets by vm.categoryBudgets.collectAsStateWithLifecycle()
                val month by vm.month.collectAsStateWithLifecycle()
                val last = month?.takeIf { it.isCurrent }?.previousEntries.orEmpty()
                BudgetsScreen(
                    current = budgets,
                    lastMonth = com.simbio.don.budget.Stats.byCategory(last).associate { it.category to it.amount },
                    disposable = home.plan.disposable,
                    onSave = { vm.saveCategoryBudgets(it) },
                    onBack = close,
                )
            }
            Overlay.Dress -> DressScreen(
                current = home.look,
                onSave = {
                    vm.saveLook(it)
                    Toast.makeText(this, "새 모습으로 바꿨어요", Toast.LENGTH_SHORT).show()
                    close()
                },
                onBack = close,
            )
            Overlay.Goals -> {
                val all by vm.goals.collectAsStateWithLifecycle()
                GoalsScreen(
                    savedTotal = home.savedTotal,
                    goals = home.goals,
                    usedGoals = all.filter { it.used },
                    onAdd = { n, t, i -> vm.addGoal(n, t, i) },
                    onUpdate = { vm.updateGoal(it) },
                    onDelete = { vm.deleteGoal(it) },
                    onBack = close,
                )
            }
            is Overlay.EditRule -> {
                val rules by vm.appRules.collectAsStateWithLifecycle()
                rules.firstOrNull { it.packageName == o.packageName }?.let { r ->
                    RuleEditScreen(r.toRule(), vm::saveRule, { vm.deleteRule(r.packageName) }, close)
                }
            }
        }
    }
}
