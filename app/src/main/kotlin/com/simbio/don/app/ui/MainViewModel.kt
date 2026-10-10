package com.simbio.don.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.ReviewItemEntity
import com.simbio.don.app.data.ReviewResolution
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.WeekResult
import com.simbio.don.notify.AppRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
            while (true) {
                delay(60_000)
                clock.value = LocalDateTime.now()
            }
        }
    }

    fun refreshClock() {
        clock.value = LocalDateTime.now()
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

    fun entries(home: HomeState): Flow<List<EntryEntity>> = repo.entries(home.cycle)

    fun completeOnboarding(
        salary: Long,
        payDay: Int,
        fixed: List<FixedExpense>,
        savings: Long,
        settlementDay: DayOfWeek,
        firstCycleBudget: Long?,
    ) = viewModelScope.launch {
        repo.completeOnboarding(salary, payDay, fixed, savings, settlementDay, LocalDate.now(), firstCycleBudget)
    }

    fun updateSettings(fixed: List<FixedExpense>, savings: Long, settlementDay: DayOfWeek, applyNow: Boolean) =
        viewModelScope.launch { repo.updateSettings(fixed, savings, settlementDay, applyNow) }

    fun addManual(date: LocalDate, amount: Long, label: String, type: EntryType) =
        viewModelScope.launch { repo.addManual(date, amount, label, type) }

    fun deleteEntry(e: EntryEntity) = viewModelScope.launch { repo.deleteEntry(e) }

    fun resolve(item: ReviewItemEntity, r: ReviewResolution) = viewModelScope.launch { repo.resolveReview(item, r) }

    fun settle(week: WeekResult) = viewModelScope.launch { repo.settle(week, LocalDateTime.now()) }

    suspend fun suggestedSalary(home: HomeState): Long = repo.suggestedSalary(home.nextExpectedPayday)

    fun confirmSalary(amount: Long, start: LocalDate) =
        viewModelScope.launch { repo.confirmSalary(amount, start, LocalDateTime.now()) }

    fun saveRule(rule: AppRule) = viewModelScope.launch { repo.saveRule(rule) }
    fun deleteRule(pkg: String) = viewModelScope.launch { repo.deleteRule(pkg) }
}
