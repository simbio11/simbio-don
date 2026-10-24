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

    fun save(draft: EntryDraft, existing: EntryEntity?) = viewModelScope.launch {
        if (existing == null && draft.months > 1) {
            repo.addInstallment(
                draft.date, draft.amount, draft.months, draft.label, draft.category, draft.sub, draft.memo, draft.accountId, draft.minuteOfDay,
            )
        } else if (existing == null) {
            repo.addManual(
                draft.date, draft.amount, draft.label, draft.type, draft.category, draft.memo,
                draft.accountId, draft.toAccountId, draft.minuteOfDay, LocalDateTime.now(), draft.sub,
            )
        } else {
            repo.updateEntry(
                existing.copy(
                    epochDay = draft.date.toEpochDay(),
                    amount = draft.amount,
                    label = draft.label.ifBlank { existing.label },
                    type = draft.type.name,
                    category = draft.category.name,
                    memo = draft.memo,
                    accountId = draft.accountId,
                    toAccountId = if (draft.type == com.simbio.don.budget.EntryType.TRANSFER) draft.toAccountId else null,
                    minuteOfDay = draft.minuteOfDay,
                    sub = draft.sub,
                ),
                existing,
                draft.applyToSameLabel,
            )
        }
    }

    // ── 자산 ──
    val accountBalances = repo.accountBalances.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveAccount(a: com.simbio.don.app.data.AccountEntity) = viewModelScope.launch { repo.saveAccount(a) }

    fun deleteAccount(a: com.simbio.don.app.data.AccountEntity) = viewModelScope.launch { repo.deleteAccount(a) }

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
            setup.firstCycleBudget, setup.accounts, setup.look,
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

    /** 미니게임 한 판 기록 → 실제로 받은 마일 */
    fun playGame(game: com.simbio.don.game.MiniGame, score: Int, reward: Long, granted: (Long) -> Unit) =
        viewModelScope.launch { granted(repo.playGame(game, score, reward, LocalDate.now())) }
    fun discard(ids: Set<Long>) = viewModelScope.launch { repo.discard(ids) }

    fun updateSalary(amount: Long) = viewModelScope.launch { repo.updateSalary(amount) }

    fun dismissSalaryNotice() = viewModelScope.launch { repo.dismissSalaryNotice() }

    fun saveRule(rule: AppRule) = viewModelScope.launch { repo.saveRule(rule) }
    fun deleteRule(pkg: String) = viewModelScope.launch { repo.deleteRule(pkg) }
}
