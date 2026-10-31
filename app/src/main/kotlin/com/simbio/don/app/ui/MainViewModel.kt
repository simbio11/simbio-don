package com.simbio.don.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.MonthView
import com.simbio.don.app.data.ReviewItemEntity
import com.simbio.don.app.data.ReviewResolution
import com.simbio.don.game.ItemKind
import com.simbio.don.notify.AppRule
import com.simbio.don.app.weather.WeatherService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import com.simbio.don.app.calendar.CalEvent
import com.simbio.don.app.calendar.DeviceCalendar
import com.simbio.don.app.calendar.PhoneCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 앱 첫 상태: 아직 모름 / 초기 설정 필요 / 준비됨 */
sealed interface AppState {
    data object Loading : AppState
    data object NeedsOnboarding : AppState
    data class Ready(val home: HomeState) : AppState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DonRepository.get(app)

    /** 날짜·시각이 바뀌면 다시 계산하도록 1분마다 */
    private val clock = MutableStateFlow(LocalDateTime.now())

    init {
        viewModelScope.launch {
            repo.dailyUpkeep(LocalDateTime.now())
            while (true) {
                delay(60_000)
                val now = LocalDateTime.now()
                // 날짜가 바뀌면 월급날·고정 지출 확인
                if (now.toLocalDate() != clock.value.toLocalDate()) repo.dailyUpkeep(now)
                clock.value = now
            }
        }
    }

    fun refreshClock() {
        val now = LocalDateTime.now()
        clock.value = now
        viewModelScope.launch { repo.dailyUpkeep(now) }
        refreshWeather()
        // 해외 결제가 있으면 오늘 환율 (켜 둔 때만)
        viewModelScope.launch { runCatching { com.simbio.don.app.fx.FxRates.refreshIfNeeded(getApplication()) } }
    }

    /** 창밖 날씨 (null = 끔 또는 아직 모름 → 맑음) */
    val weather = MutableStateFlow(WeatherService.cached(app))

    fun refreshWeather() = viewModelScope.launch { weather.value = WeatherService.refresh(getApplication()) }

    fun setWeatherCity(name: String?) {
        WeatherService.setCity(getApplication(), name)
        weather.value = null
        refreshWeather()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AppState> = repo.settings
        .flatMapLatest { s ->
            if (s == null) flowOf(AppState.NeedsOnboarding)
            else repo.home(clock).map { h -> if (h == null) AppState.NeedsOnboarding else AppState.Ready(h) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)

    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val fixedExpenses = repo.fixedExpenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reviewItems = repo.reviewItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val appRules = repo.appRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val seenApps = repo.seenApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 내역·통계: 고른 달 (null = 이번 달)
    val cycles = repo.cycles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 달마다 요약 (월별 추이·모은 돈) */
    val summaries = repo.summaries(clock).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryBudgets = repo.categoryBudgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun saveCategoryBudgets(b: Map<com.simbio.don.budget.Category, Long>) = viewModelScope.launch { repo.saveCategoryBudgets(b) }

    val goals = repo.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addGoal(name: String, target: Long, icon: String) = viewModelScope.launch { repo.addGoal(name, target, icon, clock.value.toLocalDate()) }

    fun updateGoal(g: com.simbio.don.app.data.SavingsGoalEntity) = viewModelScope.launch { repo.updateGoal(g) }

    fun deleteGoal(g: com.simbio.don.app.data.SavingsGoalEntity) = viewModelScope.launch { repo.deleteGoal(g) }

    fun putIntoGoal(goalId: Long, amount: Long, source: com.simbio.don.app.data.GoalSource) =
        viewModelScope.launch { repo.putIntoGoal(goalId, amount, source, clock.value.toLocalDate()) }

    fun takeFromGoal(goalId: Long, amount: Long) = viewModelScope.launch { repo.takeFromGoal(goalId, amount, clock.value.toLocalDate()) }

    fun linkGoal(goalId: Long, accountId: Long?) = viewModelScope.launch { repo.linkGoal(goalId, accountId) }

    fun useGoal(goalId: Long) = viewModelScope.launch { repo.useGoal(goalId, clock.value.toLocalDate()) }

    val goalMoves = repo.goalMoves.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selectedNominal = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val month: StateFlow<MonthView?> = combine(selectedNominal, repo.cycles, clock) { sel, cs, t -> Triple(sel, cs, t) }
        .flatMapLatest { (sel, cs, t) ->
            val nominal = sel?.takeIf { n -> cs.any { it.nominal == n } } ?: cs.firstOrNull()?.nominal
            if (nominal == null) flowOf(null) else repo.month(nominal, t.toLocalDate())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** [delta] = -1 지난달, +1 다음 달 */
    fun moveMonth(delta: Int) {
        val cs = cycles.value
        val current = month.value?.nominal ?: return
        val i = cs.indexOfFirst { it.nominal == current }
        // cycles는 최신순
        cs.getOrNull(i - delta)?.let { selectedNominal.value = it.nominal }
    }

    fun resetMonth() {
        selectedNominal.value = null
    }

    fun pickMonth(nominal: String) {
        selectedNominal.value = nominal
    }

    val learned = repo.learnedCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    suspend fun learnedMap() = repo.learned()
    fun resetLearned() = viewModelScope.launch { repo.resetLearned() }

    /** [learned]: 알림 기록 이름을 고쳐 같은 앱 기록도 고친 수 (0이면 없음) */
    fun save(draft: EntryDraft, existing: EntryEntity?, learned: (Int) -> Unit = {}) = viewModelScope.launch {
        if (existing == null && draft.months > 1) {
            repo.addInstallment(
                draft.date, draft.amount, draft.months, draft.label, draft.category, draft.sub, draft.memo, draft.accountId, draft.minuteOfDay,
            )
            draft.planId?.let { repo.completePlan(it, null, draft.amount, draft.date) }
        } else if (existing == null) {
            val id = repo.addManual(
                draft.date, draft.amount, draft.label, draft.type, draft.category, draft.memo,
                draft.accountId, draft.toAccountId, draft.minuteOfDay, LocalDateTime.now(), draft.sub,
                share = draft.share, people = draft.people, owe = draft.owe,
            )
            // 계획에서 "썼어요"로 적었으면 계획과 잇는다 (계획과 실제를 견줌), 아니면 맞는 계획을 찾아 잇는다
            draft.planId?.let { repo.completePlan(it, id, draft.amount, draft.date) } ?: repo.autoLinkPlans()
        } else {
            val n = repo.updateEntry(
                existing.copy(
                    epochDay = draft.date.toEpochDay(),
                    amount = draft.amount,
                    label = draft.label.ifBlank { existing.label },
                    type = draft.type.name,
                    category = draft.category.name,
                    memo = draft.memo,
                    accountId = if (draft.owe || existing.settleFor != null) null else draft.accountId,
                    toAccountId = if (draft.type == com.simbio.don.budget.EntryType.TRANSFER) draft.toAccountId else null,
                    minuteOfDay = draft.minuteOfDay,
                    sub = draft.sub,
                    share = draft.share?.takeIf { it in 0 until draft.amount },
                    people = draft.people,
                    owe = draft.owe,
                ),
                existing,
                draft.applyToSameLabel,
            )
            if (n > 0) learned(n)
            // 나눠 냈다고 고치면 내 몫으로 계획과 다시 견준다
            if (draft.share != existing.share || draft.amount != existing.amount) repo.autoLinkPlans()
        }
    }

    suspend fun lastMonthReport() = repo.lastMonthReport()

    fun setDailyCategories(names: Set<String>) = viewModelScope.launch { repo.setDailyCategories(names) }

    // ── 더치페이 정산 ──
    private val splitPrefs = app.getSharedPreferences("splits", android.content.Context.MODE_PRIVATE)
    private val dismissedHints = MutableStateFlow(
        splitPrefs.getStringSet("dismissed", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
    )
    val splits = repo.splitBook(dismissedHints).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.simbio.don.app.data.SplitBook.Empty)

    /** "아니에요": 이 입금·송금은 다시 안 묻는다 (최근 300개만 기억) */
    fun dismissSplitHint(key: Long) {
        val next = (dismissedHints.value + key).sortedDescending().take(300).toSet()
        dismissedHints.value = next
        splitPrefs.edit().putStringSet("dismissed", next.map { it.toString() }.toSet()).apply()
    }

    fun settleWith(target: EntryEntity, income: EntryEntity) = viewModelScope.launch { repo.settleWith(target, income) }
    fun settleDirect(target: EntryEntity, amount: Long, accountId: Long?) = viewModelScope.launch { repo.settleDirect(target, amount, accountId, LocalDate.now()) }
    fun forgiveSplit(target: EntryEntity) = viewModelScope.launch { repo.forgiveSplit(target) }
    fun unsettle(receipt: EntryEntity) = viewModelScope.launch { repo.unsettle(receipt) }
    fun markPaid(owe: EntryEntity, accountId: Long?) = viewModelScope.launch { repo.markPaid(owe, accountId) }
    fun payWith(owe: EntryEntity, out: EntryEntity) = viewModelScope.launch { repo.payWith(owe, out) }

    // ── 자산 ──
    val accountBalances = repo.accountBalances.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveAccount(a: com.simbio.don.app.data.AccountEntity, balance: Long? = null) = viewModelScope.launch { repo.saveAccount(a, balance) }

    fun deleteAccount(a: com.simbio.don.app.data.AccountEntity) = viewModelScope.launch { repo.deleteAccount(a) }

    fun checkAssets(inputs: List<com.simbio.don.app.data.DonRepository.AssetCheckInput>, from: Long?) =
        viewModelScope.launch { repo.checkAssets(inputs, from, LocalDate.now()) }

    fun entriesOfAccount(id: Long) = repo.entriesOfAccount(id)

    // ── 전체 검색 ──
    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<EntryEntity>> = searchQuery.flatMapLatest { repo.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 백업 ──
    fun exportBackup(uri: Uri, done: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val ok = runCatching {
            val json = repo.exportBackup(LocalDateTime.now())
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ?: error("파일을 열 수 없어요")
        }.isSuccess
        kotlinx.coroutines.withContext(Dispatchers.Main) { done(ok) }
    }

    /** 복원 결과: null = 성공, 아니면 이유 */
    fun restoreBackup(uri: Uri, done: (String?) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val error = try {
            val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("파일을 열 수 없어요")
            repo.restoreBackup(json)
            repo.dailyUpkeep(LocalDateTime.now())
            null
        } catch (e: com.simbio.don.app.data.Backup.BadBackup) {
            e.message
        } catch (e: Exception) {
            "복원하지 못했어요"
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) { done(error) }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val csv = repo.exportCsv()
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
    }

    fun completeOnboarding(setup: OnboardingResult) = viewModelScope.launch {
        repo.completeOnboarding(
            setup.salary, setup.payDay, setup.fixed, setup.savings, setup.settlementDay, LocalDate.now(),
            setup.firstCycleBudget, setup.accounts, setup.look, setup.apps,
        )
    }

    fun saveLook(look: com.simbio.don.game.CharacterLook) = viewModelScope.launch { repo.saveLook(look) }

    // ── 카테고리·소분류·할부·반복 수입 ──
    val customCategories = repo.customCategories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val subcategories = repo.subcategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val recurringIncomes = repo.recurringIncomes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCategory(label: String, income: Boolean, icon: String, color: Long) = viewModelScope.launch { repo.addCategory(label, income, icon, color) }
    fun updateCategory(name: String, label: String, icon: String, color: Long) = viewModelScope.launch { repo.updateCategory(name, label, icon, color) }
    fun deleteCategory(name: String) = viewModelScope.launch { repo.deleteCategory(name) }
    fun addSubcategory(c: com.simbio.don.budget.Category, name: String) = viewModelScope.launch { repo.addSubcategory(c, name) }
    fun renameSubcategory(s: com.simbio.don.app.data.SubcategoryEntity, name: String) = viewModelScope.launch { repo.renameSubcategory(s, name) }
    fun deleteSubcategory(s: com.simbio.don.app.data.SubcategoryEntity) = viewModelScope.launch { repo.deleteSubcategory(s) }
    fun deleteInstallment(id: Long) = viewModelScope.launch { repo.deleteInstallment(id) }
    fun saveRecurringIncomes(items: List<com.simbio.don.app.data.RecurringIncomeEntity>) = viewModelScope.launch { repo.saveRecurringIncomes(items) }

    // ── 돈't 몰 ──
    /** 장바구니 (앱을 닫으면 비워진다) */
    val cart = MutableStateFlow<List<com.simbio.don.game.CartLine>>(emptyList())

    // ── 마일·가게 ──
    fun depositMiles(n: Long) = viewModelScope.launch { repo.depositMiles(n) }
    fun withdrawMiles(n: Long) = viewModelScope.launch { repo.withdrawMiles(n) }
    fun moveHouse(level: com.simbio.don.game.HouseLevel, done: (Boolean) -> Unit) = viewModelScope.launch { done(repo.moveHouse(level)) }
    fun buyStorage(slot: com.simbio.don.game.Slot, done: (Boolean) -> Unit) = viewModelScope.launch { done(repo.buyStorage(slot)) }
    fun chooseInterior(part: com.simbio.don.game.Shop.Interior, theme: String, isDefault: Boolean, done: (Boolean) -> Unit) =
        viewModelScope.launch { done(repo.chooseInterior(part, theme, isDefault)) }
    fun applyLook(look: com.simbio.don.game.CharacterLook, payWithMiles: Boolean, done: (com.simbio.don.app.data.LookResult) -> Unit) =
        viewModelScope.launch { done(repo.applyLook(look, payWithMiles)) }

    /** 연간 통계에서 보는 해 */
    private val year = MutableStateFlow(LocalDate.now().year)

    @OptIn(ExperimentalCoroutinesApi::class)
    val yearView = year.flatMapLatest { repo.year(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pickYear(y: Int) { year.value = y }

    fun updateSettings(
        fixed: List<com.simbio.don.app.data.FixedExpenseEntity>,
        savings: Long,
        settlementDay: DayOfWeek,
        applyNow: Boolean,
        payDay: Int? = null,
    ) = viewModelScope.launch { repo.updateSettings(fixed, savings, settlementDay, applyNow, payDay) }

    private val _deleted = MutableStateFlow<EntryEntity?>(null)

    /** 방금 지운 기록 (되돌리기 안내용) */
    val deleted: StateFlow<EntryEntity?> = _deleted

    fun deleteEntry(e: EntryEntity) = viewModelScope.launch {
        repo.deleteEntry(e)
        _deleted.value = e
    }

    fun undoDelete() {
        val e = _deleted.value ?: return
        _deleted.value = null
        viewModelScope.launch { repo.restoreEntry(e) }
    }

    fun clearDeleted() {
        _deleted.value = null
    }

    suspend fun quickPicks() = repo.quickPicks(clock.value.toLocalDate())

    fun resolve(item: ReviewItemEntity, r: ReviewResolution) = viewModelScope.launch { repo.resolveReview(item, r) }

    fun settle(week: com.simbio.don.app.data.PendingWeek, returnIds: Set<Long>) =
        viewModelScope.launch { repo.settle(week, LocalDateTime.now(), returnIds) }

    fun buyMock(name: String, price: Long, kind: ItemKind, store: String?) =
        viewModelScope.launch { repo.buyMock(name, price, kind, store, LocalDate.now(), LocalDateTime.now()) }

    fun wishMock(name: String, price: Long, kind: ItemKind, store: String?) =
        viewModelScope.launch { repo.wishMock(name, price, kind, store, LocalDateTime.now()) }

    fun buyWished(id: Long) = viewModelScope.launch { repo.buyWished(id, LocalDateTime.now()) }

    fun giveUpWished(id: Long) = viewModelScope.launch { repo.giveUpWished(id) }

    fun recycle() = viewModelScope.launch { repo.recycle() }

    fun chooseTitle(title: String) = viewModelScope.launch { repo.chooseTitle(title) }

    fun saveOwnerName(name: String) = viewModelScope.launch { repo.saveOwnerName(name) }

    // ── 돈 나갈 계획 ──
    val plans = repo.plans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 계획과 맞을 것 같은 결제 (물어볼 것) */
    val planHints = repo.planHints(LocalDate.now()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun confirmPlanHint(planId: Long, entryId: Long, people: Int?) = viewModelScope.launch { repo.confirmPlanHint(planId, entryId, people) }

    /** 아니에요 (저절로 이었던 것이면 풀기) */
    fun rejectPlanMatch(planId: Long, entryId: Long) = viewModelScope.launch { repo.rejectPlanMatch(planId, entryId) }

    // ── 실제 잔액 맞추기 ──
    val balanceGaps = repo.balanceGaps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun fixBalance(accountId: Long, diff: Long, how: com.simbio.don.app.data.DonRepository.BalanceFix) =
        viewModelScope.launch { repo.fixBalance(accountId, diff, how) }

    fun dismissBalanceGap(accountId: Long) = viewModelScope.launch { repo.dismissBalanceGap(accountId) }

    // ── 환율 ──
    val fxRates = repo.fxRates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveFxRate(currency: String, rate: Double) =
        viewModelScope.launch { repo.saveFxRate(currency, rate, com.simbio.don.app.data.DonRepository.FX_MANUAL) }

    /** 계획 저장. [toCalendar]면 폰 캘린더에도 하루 종일 일정으로 넣는다 (계획을 지우면 그 일정도 지움) */
    fun savePlan(p: com.simbio.don.app.data.PlanEntity, toCalendar: Boolean = false) = viewModelScope.launch {
        val c = getApplication<Application>()
        val eventId = if (toCalendar && p.eventId == null) {
            withContext(Dispatchers.IO) { PhoneCalendar.insert(c, p.label, java.time.LocalDate.ofEpochDay(p.epochDay), null, p.amount) }
        } else null
        repo.savePlan(if (eventId != null) p.copy(eventId = eventId, calendarOwned = true) else p)
        repo.autoLinkPlans()
        if (eventId != null) refreshCalendar()
    }

    fun deletePlan(id: Long) = viewModelScope.launch {
        val p = repo.planNow(id)
        repo.deletePlan(id)
        if (p?.calendarOwned == true && p.eventId != null) {
            withContext(Dispatchers.IO) { PhoneCalendar.delete(getApplication(), p.eventId) }
            refreshCalendar()
        }
    }
    fun cancelPlan(id: Long, canceled: Boolean) = viewModelScope.launch { repo.cancelPlan(id, canceled) }
    fun linkPlan(id: Long, e: com.simbio.don.app.data.EntryEntity) = viewModelScope.launch { repo.linkPlan(id, e) }

    // ── 캘린더: 폰 캘린더(켰을 때만, 동기화는 폰 계정이) + 이 앱 안 캘린더 ──
    val calendarOn = MutableStateFlow(false)
    private val phoneEvents = MutableStateFlow<List<CalEvent>>(emptyList())
    val deviceCalendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val writeCalendarId = MutableStateFlow<Long?>(null)
    val shownCalendarIds = MutableStateFlow<Set<Long>?>(null)

    /** 캘린더 탭에서 보고 있는 달 */
    val calendarMonth = MutableStateFlow(java.time.YearMonth.now())

    /** 읽을 범위: 가계부 달·오늘 앞뒤 6주·캘린더 탭 달을 모두 덮게 */
    private val calendarRange = MutableStateFlow(LocalDate.now().minusDays(7) to LocalDate.now().plusDays(49))

    /** 날짜별 일정 (폰 + 앱 안) */
    val calendarEvents: StateFlow<Map<LocalDate, List<CalEvent>>> =
        combine(phoneEvents, repo.appEvents, calendarRange) { phone, local, range ->
            PhoneCalendar.byDay(phone + com.simbio.don.app.calendar.LocalCalendar.toEvents(local, range.first, range.second))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 캘린더 탭 달력(앞뒤 주 포함)의 가계부 기록 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val calendarEntries: StateFlow<List<EntryEntity>> = calendarMonth.flatMapLatest { ym ->
        val (from, to) = gridRange(ym)
        repo.entriesIn(from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 달력 칸 (일요일부터 6주) */
    fun gridRange(ym: java.time.YearMonth): Pair<LocalDate, LocalDate> {
        val first = ym.atDay(1)
        val start = first.minusDays((first.dayOfWeek.value % 7).toLong())
        return start to start.plusDays(41)
    }

    fun showCalendarMonth(ym: java.time.YearMonth) {
        calendarMonth.value = ym
        refreshCalendar()
    }

    /** 일정 다시 읽기 */
    fun refreshCalendar() = viewModelScope.launch(Dispatchers.IO) { loadCalendar() }

    /** 한 번에 하나씩 (켜기와 다시 읽기가 겹쳐 옛 값을 쓰지 않게) */
    private val calendarLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun loadCalendar() = calendarLock.withLock {
        val c = getApplication<Application>()
        val today = LocalDate.now()
        val viewed = month.value?.cycle
        val (gridFrom, gridTo) = gridRange(calendarMonth.value)
        val from = minOf(viewed?.start ?: today, today.minusDays(7), gridFrom)
        val to = maxOf(viewed?.end ?: today, today.plusDays(49), gridTo)
        calendarRange.value = from to to
        val on = PhoneCalendar.isOn(c)
        calendarOn.value = on
        writeCalendarId.value = PhoneCalendar.writeCalendarId(c)
        shownCalendarIds.value = PhoneCalendar.shownIds(c)
        val events = if (on) PhoneCalendar.events(c, from, to).values.flatten() else emptyList()
        deviceCalendars.value = if (on) PhoneCalendar.calendars(c) else emptyList()
        if (events != phoneEvents.value) {
            phoneEvents.value = events
            // 위젯 달력의 일정 점도
            com.simbio.don.app.widget.Widgets.refresh(c)
        }
    }

    /** 권한을 받은 뒤 켠다. 새 일정 넣을 곳을 안 골랐으면 구글 기본 캘린더(계정 이름과 같은 것) 먼저 */
    fun connectCalendar() = viewModelScope.launch(Dispatchers.IO) {
        val c = getApplication<Application>()
        PhoneCalendar.setOn(c, true)
        if (PhoneCalendar.writeCalendarId(c) == null) {
            val cals = PhoneCalendar.calendars(c).filter { it.writable }
            val pick = cals.firstOrNull { it.name == it.account && "@" in it.account } ?: cals.firstOrNull { "@" in it.account } ?: cals.firstOrNull()
            pick?.let { PhoneCalendar.setWriteCalendarId(c, it.id) }
        }
        loadCalendar()
    }

    fun disconnectCalendar() {
        PhoneCalendar.setOn(getApplication(), false)
        refreshCalendar()
    }

    fun setCalendarShown(id: Long, shown: Boolean) {
        val c = getApplication<Application>()
        val all = deviceCalendars.value.map { it.id }.toSet()
        val now = PhoneCalendar.shownIds(c) ?: all
        PhoneCalendar.setShownIds(c, if (shown) now + id else now - id)
        refreshCalendar()
    }

    fun setWriteCalendar(id: Long) {
        PhoneCalendar.setWriteCalendarId(getApplication(), id)
        writeCalendarId.value = id
    }

    /** 일정에 쓸 돈 정하기 → 돈 나갈 계획 (이번 주 오늘 몫에서 미리 빠짐) */
    fun budgetEvent(e: CalEvent, amount: Long) = viewModelScope.launch {
        repo.budgetForEvent(e.eventId, e.title, e.startDate, amount)
        repo.autoLinkPlans()
    }

    /** 새 일정이 들어갈 캘린더: 폰 캘린더를 이었고 넣을 곳이 있으면 거기, 아니면 앱 안 */
    fun defaultTarget(): Long = writeCalendarId.value?.takeIf { calendarOn.value } ?: com.simbio.don.app.calendar.LocalCalendar.CALENDAR_ID

    /** 일정 넣기 (+ 쓸 돈을 적었으면 예산도). 못 넣으면 false */
    fun addCalendarEvent(date: LocalDate, title: String, minuteOfDay: Int?, budget: Long?, done: (Boolean) -> Unit = {}) =
        saveEvent(null, com.simbio.don.app.calendar.EventDraft(title, date, minuteOfDay, memo = PhoneCalendar.budgetMemo(budget)), defaultTarget(), budget, done)

    /**
     * 일정 만들기·고치기. [target]은 새 일정의 캘린더 (앱 안이면 LocalCalendar.CALENDAR_ID).
     * [budget]이 있으면 그 일정(시작 날)에 쓸 돈으로 계획을 만들거나 금액을 바꾼다. 날을 옮기면 안 쓴 예산도 따라간다.
     */
    fun saveEvent(existing: CalEvent?, draft: com.simbio.don.app.calendar.EventDraft, target: Long, budget: Long?, done: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            val c = getApplication<Application>()
            val local = existing?.local ?: (target == com.simbio.don.app.calendar.LocalCalendar.CALENDAR_ID)
            val id: Long? = when {
                existing == null && local -> repo.saveAppEvent(null, draft)
                existing == null -> withContext(Dispatchers.IO) { PhoneCalendar.insert(c, draft, target) }
                local -> repo.saveAppEvent(com.simbio.don.app.calendar.LocalCalendar.entityId(existing.eventId), draft)
                else -> existing.eventId.takeIf { withContext(Dispatchers.IO) { PhoneCalendar.update(c, existing.eventId, draft) } }
            }
            if (id != null) {
                if (existing != null) repo.movePlansOfEvent(id, existing.startDate, draft.startDate)
                if (budget != null) repo.budgetForEvent(id, draft.title, draft.startDate, budget)
                repo.autoLinkPlans()
                if (local) com.simbio.don.app.notify.EventReminders.reschedule(c)
            }
            loadCalendar()
            done(id != null)
        }

    /** 일정 지우기 (안 쓴 그 일정 예산도) */
    fun deleteEvent(e: CalEvent, done: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val c = getApplication<Application>()
        val ok = if (e.local) {
            repo.deleteAppEvent(com.simbio.don.app.calendar.LocalCalendar.entityId(e.eventId)); true
        } else {
            withContext(Dispatchers.IO) { PhoneCalendar.delete(c, e.eventId) }
        }
        if (ok) {
            repo.deletePlansOfEvent(e.eventId)
            if (e.local) com.simbio.don.app.notify.EventReminders.reschedule(c)
        }
        loadCalendar()
        done(ok)
    }

    fun planMatches(plans: List<com.simbio.don.app.data.PlanEntity>, entries: List<com.simbio.don.app.data.EntryEntity>) = repo.planMatches(plans, entries)

    fun addFixedExpense(name: String, amount: Long, day: Int?) = viewModelScope.launch { repo.addFixedExpense(name, amount, day) }

    /** 미니게임 한 판 기록 → 실제로 받은 마일 */
    fun playGame(game: com.simbio.don.game.MiniGame, score: Int, reward: Long, granted: (Long) -> Unit) =
        viewModelScope.launch { granted(repo.playGame(game, score, reward, LocalDate.now())) }
    fun discard(ids: Set<Long>) = viewModelScope.launch { repo.discard(ids) }

    fun updateSalary(amount: Long) = viewModelScope.launch { repo.updateSalary(amount) }

    fun dismissSalaryNotice() = viewModelScope.launch { repo.dismissSalaryNotice() }

    fun saveRule(rule: AppRule) = viewModelScope.launch { repo.saveRule(rule) }
    fun deleteRule(pkg: String) = viewModelScope.launch { repo.deleteRule(pkg) }
}
