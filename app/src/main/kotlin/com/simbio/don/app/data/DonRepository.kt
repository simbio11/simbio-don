package com.simbio.don.app.data

import android.content.Context
import com.simbio.don.budget.BudgetEngine
import com.simbio.don.budget.Category
import com.simbio.don.budget.CategoryGuesser
import com.simbio.don.budget.Entry
import com.simbio.don.budget.Stats
import com.simbio.don.budget.CycleLedger
import com.simbio.don.budget.CyclePlan
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.PayCycle
import com.simbio.don.budget.Payday
import com.simbio.don.budget.Settlement
import com.simbio.don.budget.WeekResult
import com.simbio.don.game.HouseLayout
import com.simbio.don.game.HouseLevel
import com.simbio.don.game.ItemKind
import com.simbio.don.game.MockItem
import com.simbio.don.game.MockWallet
import com.simbio.don.game.Mood
import com.simbio.don.game.Moods
import com.simbio.don.game.PurchaseStatus
import com.simbio.don.game.Wishlist
import com.simbio.don.game.WeeklySettlement
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.Decision
import com.simbio.don.notify.DefaultApps
import com.simbio.don.notify.NotificationParser
import com.simbio.don.notify.ParsedPayment
import com.simbio.don.notify.RawNotification
import com.simbio.don.notify.ReviewPolicy
import com.simbio.don.notify.ReviewReason
import com.simbio.don.notify.RuleAction
import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.simbio.don.notify.ParseResult
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** 결산할 수 있는 지난 주. 새 달이 시작돼도 지난달 마지막 주는 결산 화면으로 본다 */
data class PendingWeek(val nominal: String, val result: WeekResult)

/** 새 달이 자동으로 시작됐다는 안내 */
data class SalaryNotice(val salary: Long, val source: SalarySource, val start: LocalDate)

/** 첫 화면에 필요한 것 전부. */
data class HomeState(
    val today: LocalDate,
    /** 지금 (1분마다 갱신) — 방 창밖 시간대, 찜 24시간 */
    val now: LocalDateTime,
    val payDay: Int,
    val cycle: PayCycle,
    val plan: CyclePlan,
    val ledger: CycleLedger,
    val results: List<WeekResult>,
    val currentWeek: WeekResult?,
    val savedSoFar: Long,
    val reviewCount: Int,
    /** 결산할 수 있는 지난 주 (가장 오래된 것부터) */
    val settleable: List<PendingWeek>,
    /** 끝난 주가 있지만 확인함이 남아 정오까지 결산을 기다리는 중 */
    val waitingForInbox: Boolean,
    /** 다음 달이 시작될 예상 월급날 */
    val nextExpectedPayday: LocalDate,
    /** 이번 달 월급 */
    val salary: Long,
    val salarySource: SalarySource,
    /** 새 달이 자동으로 시작돼 안내할 것 (봤으면 null) */
    val salaryNotice: SalaryNotice?,
    /** 이번 주기 기록 (최신순) */
    val entries: List<EntryEntity>,
    /** 이번 달 가계부 지출 (고정·예외 지출 포함, 환불 뺌) */
    val monthSpent: Long,
    /** 이번 달 생활비로 쓴 돈 (고정 지출 뺌) */
    val monthLiving: Long,
    /** 이번 주 남은 돈 ÷ 남은 날 */
    val todayAllowance: Long,
    // ── 게임 ──
    val mockEntities: List<MockPurchaseEntity>,
    val mockItems: List<MockItem>,
    /** 모의 지갑 = 이번 달 가용액 − 실제 쓴 생활비 − 아직 돌려받지 않은 모의 구매 */
    val wallet: Long,
    val house: HouseLayout,
    val mood: Mood,
    // ── 가계부 더 ──
    /** 이번 달 수입 */
    val monthIncome: Long = 0,
    val categoryBudgets: Map<Category, Long> = emptyMap(),
    /** 지금까지 모은 돈 (모든 달) */
    val savedTotal: Long = 0,
    /** 이번 달 모은 돈 (먼저 뗀 저축 + 지금까지 아낀 돈) */
    val savedThisMonth: Long = 0,
    val goals: List<GoalProgress> = emptyList(),
    /** 예산을 지킨 결산이 몇 주 연속인지 */
    val streak: Int = 0,
    /** 자산과 잔액 */
    val accounts: List<AccountBalance> = emptyList(),
) {
    val shipping: List<MockItem> get() = mockItems.filter { it.status == PurchaseStatus.SHIPPING }

    /** 찜 목록 (오래된 것부터) */
    val wishes: List<MockItem> get() = mockItems.filter { it.status == PurchaseStatus.WISHED }.sortedBy { it.createdAt }

    /** 하루가 지나 다시 물어볼 찜 */
    val readyWishes: List<MockItem>
        get() = wishes.filter { w -> w.createdAt?.let { Wishlist.isReady(it, now) } ?: true }

    /** 찜했다가 안 산 돈 */
    val gaveUpTotal: Long get() = Wishlist.gaveUpTotal(mockItems)
}

/** 내역·통계 화면용 한 달 묶음. */
data class MonthView(
    val categoryBudgets: Map<Category, Long> = emptyMap(),
    val nominal: String,
    val cycle: PayCycle,
    val plan: CyclePlan,
    val results: List<WeekResult>,
    val entities: List<EntryEntity>,
    val entries: List<Entry>,
    val previousEntries: List<Entry>,
    val today: LocalDate,
    val isCurrent: Boolean,
)

enum class ReviewResolution { APPLY_SPEND, APPLY_REFUND, APPLY_EXCEPTION, APPLY_FIXED, DISMISS }

class DonRepository(private val dao: DonDao, private val db: DonDatabase? = null) {

    /**
     * 읽고 → 판단하고 → 쓰는 작업은 한 번에 하나씩. 알림 두 개가 동시에 오거나
     * 결산·월급 확인 버튼을 두 번 눌러도 같은 돈이 두 번 기록되지 않게.
     */
    private val writeLock = Mutex()

    val settings: Flow<SettingsEntity?> = dao.settings()
    val fixedExpenses: Flow<List<FixedExpenseEntity>> = dao.fixedExpenses()
    val reviewItems: Flow<List<ReviewItemEntity>> = dao.reviewItems()
    val appRules: Flow<List<AppRuleEntity>> = dao.appRules()
    val seenApps: Flow<List<SeenAppEntity>> = dao.seenApps()

    /** 홈에 함께 보여주는 것들 (카테고리 예산·목표·결산 기록) */
    private data class HomeExtras(
        val budgets: Map<Category, Long>,
        val goals: List<SavingsGoalEntity>,
        val settled: List<SettledWeekEntity>,
    )

    private val extras: Flow<HomeExtras> = combine(dao.categoryBudgets(), dao.goals(), dao.allSettledWeeks()) { b, g, w ->
        HomeExtras(b.associate { Category.of(it.category) to it.amount }, g, w)
    }

    /** 달마다 요약 (최신 달 먼저): 월별 추이·모은 돈 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun summaries(now: Flow<LocalDateTime>): Flow<List<CycleSummary>> =
        combine(dao.settings(), dao.cycles()) { s, cs -> s to cs }.flatMapLatest { (s, cs) ->
            if (s == null || cs.isEmpty()) return@flatMapLatest flowOf(emptyList())
            combine(dao.entriesSince(cs.last().startEpochDay), now) { all, t -> Summaries.cycles(s.payDay, cs, all, t.toLocalDate()) }
        }

    /** 자산과 지금 잔액 */
    val accountBalances: Flow<List<AccountBalance>> =
        combine(dao.accounts(), dao.accountEntries()) { accounts, entries -> AccountBalances.compute(accounts, entries) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun home(now: Flow<LocalDateTime>): Flow<HomeState?> =
        combine(homeBase(now), extras, summaries(now), accountBalances) { h, x, sums, accounts ->
            h?.let {
                val total = sums.sumOf { s -> s.saved }
                it.copy(
                    categoryBudgets = x.budgets,
                    savedTotal = total,
                    savedThisMonth = sums.firstOrNull()?.saved ?: 0,
                    goals = Summaries.goals(total, x.goals),
                    streak = Summaries.streak(x.settled),
                    accounts = accounts,
                )
            }
        }

    /** 지난달에 아직 결산하지 않은 주 (지난달 마지막 주는 새 달이 돼도 결산 화면으로 본다) */
    private data class PrevMonth(val nominal: String, val results: List<WeekResult>, val settled: Set<Int>)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun previousMonth(s: SettingsEntity, cycles: List<CycleEntity>): Flow<PrevMonth?> {
        val c = cycles.getOrNull(1) ?: return flowOf(null)
        val cycle = cycleOf(c, cycles[0], s.payDay)
        return combine(dao.entriesBetween(cycle.start.toEpochDay(), cycle.end.toEpochDay()), dao.settledWeeks(c.nominal)) { entries, settled ->
            val plan = planFor(c, cycle)
            val ledger = entries.fold(CycleLedger(plan)) { l, e -> runCatching { l.add(e.toEntry()) }.getOrDefault(l) }
            PrevMonth(c.nominal, ledger.results(), settled.map { it.weekIndex }.toSet())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun homeBase(now: Flow<LocalDateTime>): Flow<HomeState?> =
        combine(dao.settings(), dao.cycles()) { s, cs -> s to cs }.flatMapLatest { (s, cs) ->
            val c = cs.firstOrNull()
            if (s == null || c == null) return@flatMapLatest flowOf(null)
            val cycle = c.toPayCycle(s.payDay)
            combine(
                dao.entriesBetween(cycle.start.toEpochDay(), cycle.end.toEpochDay()),
                combine(dao.reviewItems(), dao.settledWeeks(c.nominal), previousMonth(s, cs)) { r, w, p -> Triple(r, w, p) },
                now,
                combine(dao.mockPurchases(), dao.house()) { m, h -> m to h },
            ) { entries, rw, t, game -> buildHome(s, c, cycle, entries, rw.first, rw.second, t, game.first, game.second, rw.third) }
        }

    private fun buildHome(
        s: SettingsEntity,
        c: CycleEntity,
        cycle: PayCycle,
        entries: List<EntryEntity>,
        reviews: List<ReviewItemEntity>,
        settled: List<SettledWeekEntity>,
        now: LocalDateTime,
        mocks: List<MockPurchaseEntity> = emptyList(),
        house: HouseEntity? = null,
        prev: PrevMonth? = null,
    ): HomeState {
        val plan = planFor(c, cycle)
        val ledger = entries.fold(CycleLedger(plan)) { l, e -> runCatching { l.add(e.toEntry()) }.getOrDefault(l) }
        val results = ledger.results()
        val today = now.toLocalDate()
        val settledIdx = settled.map { it.weekIndex }.toSet()
        val inboxEmpty = reviews.isEmpty()
        val nextPayday = Payday.nextExpected(cycle.nominal, s.payDay)
        val current = plan.weekIndexOf(today)?.let { results[it] }
        val asEntries = entries.map { it.toEntry() }
        val living = Stats.livingTotal(asEntries)
        val items = mocks.map { it.toItem() }
        val wallet = MockWallet.balance(plan.disposable, living, items)
        val prevPending = prev?.results.orEmpty().filterIndexed { i, r ->
            i !in prev!!.settled && Settlement.canSettle(r.week.end, now, inboxEmpty)
        }.map { PendingWeek(prev!!.nominal, it) }
        val source = enumOr(c.salarySource, SalarySource.MANUAL)
        return HomeState(
            today = today,
            now = now,
            payDay = s.payDay,
            cycle = cycle,
            plan = plan,
            ledger = ledger,
            results = results,
            currentWeek = current,
            savedSoFar = ledger.savedSoFar(today),
            reviewCount = reviews.size,
            settleable = prevPending + results.filterIndexed { i, r ->
                i !in settledIdx && Settlement.canSettle(r.week.end, now, inboxEmpty)
            }.map { PendingWeek(c.nominal, it) },
            waitingForInbox = !inboxEmpty && results.anyIndexed { i, r ->
                i !in settledIdx && today.isAfter(r.week.end) && !Settlement.mustAutoSettle(r.week.end, now)
            },
            nextExpectedPayday = nextPayday,
            salary = c.salary,
            salarySource = source,
            salaryNotice = if (c.noticeShown) null else SalaryNotice(c.salary, source, cycle.start),
            entries = entries,
            monthSpent = Stats.total(asEntries),
            monthLiving = living,
            monthIncome = Stats.income(asEntries),
            todayAllowance = current?.let { Stats.todayAllowance(it, today) } ?: 0,
            mockEntities = mocks,
            mockItems = items,
            wallet = wallet,
            house = HouseLayout.of(enumOr(house?.level, HouseLevel.ONE_ROOM), items),
            mood = Moods.of(current, today, wallet, plan.disposable, cycle),
        )
    }

    // ── 모의 쇼핑·집 ─────────────────────────────────────────

    suspend fun buyMock(name: String, price: Long, kind: ItemKind, store: String?, date: LocalDate, now: LocalDateTime) {
        dao.insertMock(
            MockPurchaseEntity(
                name = name.ifBlank { kind.label }.take(40),
                price = price,
                kind = kind.name,
                store = store,
                epochDay = date.toEpochDay(),
                status = PurchaseStatus.SHIPPING.name,
                createdAtEpochSecond = now.epochSecond(),
            ),
        )
    }

    /** 찜하기: 지갑에서 빠지지 않고, 하루 뒤에 다시 묻는다 */
    suspend fun wishMock(name: String, price: Long, kind: ItemKind, store: String?, now: LocalDateTime) {
        dao.insertMock(
            MockPurchaseEntity(
                name = name.ifBlank { kind.label }.take(40),
                price = price,
                kind = kind.name,
                store = store,
                epochDay = now.toLocalDate().toEpochDay(),
                status = PurchaseStatus.WISHED.name,
                createdAtEpochSecond = now.epochSecond(),
            ),
        )
    }

    /** 찜한 물건을 모의로 산다 (오늘 산 것으로 이번 주 결산에 들어간다) */
    suspend fun buyWished(id: Long, now: LocalDateTime) {
        val w = dao.mockPurchasesNow().firstOrNull { it.id == id && it.status == PurchaseStatus.WISHED.name } ?: return
        dao.updateMocks(
            listOf(w.copy(status = PurchaseStatus.SHIPPING.name, epochDay = now.toLocalDate().toEpochDay(), createdAtEpochSecond = now.epochSecond())),
        )
    }

    /** 찜했다가 안 산다 = 참은 돈 */
    suspend fun giveUpWished(id: Long) {
        val w = dao.mockPurchasesNow().firstOrNull { it.id == id && it.status == PurchaseStatus.WISHED.name } ?: return
        dao.updateMocks(listOf(w.copy(status = PurchaseStatus.GAVE_UP.name)))
    }

    /** 먹고 남은 쓰레기 치우기 */
    suspend fun recycle() {
        val trash = dao.mockPurchasesNow().filter {
            it.status == PurchaseStatus.DELIVERED.name && ItemKind.of(it.kind).isFood
        }
        dao.updateMocks(trash.map { it.copy(status = PurchaseStatus.RECYCLED.name) })
    }

    /** 집에서 비우기 (도감에는 남는다) */
    suspend fun discard(ids: Set<Long>) {
        val items = dao.mockPurchasesNow().filter { it.id in ids && it.status == PurchaseStatus.DELIVERED.name }
        dao.updateMocks(items.map { it.copy(status = PurchaseStatus.DISCARDED.name) })
    }

    /** 그 주 모의 구매를 배송 또는 반품으로 정리 */
    private suspend fun settleMocks(start: LocalDate, end: LocalDate, overspent: Long, returnIds: Set<Long>?) {
        val all = dao.mockPurchasesNow()
        val week = WeeklySettlement.shippingIn(all.map { it.toItem() }, start, end)
        val picked = returnIds?.takeIf { WeeklySettlement.isEnough(week, it, overspent) }
            ?: WeeklySettlement.autoPick(week, overspent)
        val ids = week.map { it.id }.toSet()
        dao.updateMocks(
            all.filter { it.id in ids }.map {
                it.copy(status = if (it.id in picked) PurchaseStatus.RETURNED.name else PurchaseStatus.DELIVERED.name)
            },
        )
    }

    // ── 내역·통계 ─────────────────────────────────────────────

    val cycles: Flow<List<CycleEntity>> = dao.cycles()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun month(nominal: String, today: LocalDate): Flow<MonthView?> =
        combine(monthBase(nominal, today), dao.categoryBudgets()) { m, b -> m?.copy(categoryBudgets = b.associate { Category.of(it.category) to it.amount }) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun monthBase(nominal: String, today: LocalDate): Flow<MonthView?> =
        combine(dao.settings(), dao.cycles()) { s, cs -> s to cs }.flatMapLatest { (s, cs) ->
            val idx = cs.indexOfFirst { it.nominal == nominal }
            if (s == null || idx < 0) return@flatMapLatest flowOf(null)
            val c = cs[idx]
            val cycle = cycleOf(c, cs.getOrNull(idx - 1), s.payDay)
            val prev = cs.getOrNull(idx + 1)?.let { p -> cycleOf(p, c, s.payDay) }
            combine(
                dao.entriesBetween(cycle.start.toEpochDay(), cycle.end.toEpochDay()),
                prev?.let { dao.entriesBetween(it.start.toEpochDay(), it.end.toEpochDay()) } ?: flowOf(emptyList()),
            ) { entities, prevEntities ->
                val plan = planFor(c, cycle)
                val entries = entities.map { it.toEntry() }
                val ledger = entries.fold(CycleLedger(plan)) { l, e -> runCatching { l.add(e) }.getOrDefault(l) }
                MonthView(
                    nominal = c.nominal,
                    cycle = cycle,
                    plan = plan,
                    results = ledger.results(),
                    entities = entities,
                    entries = entries,
                    previousEntries = prevEntities.map { it.toEntry() },
                    today = today,
                    isCurrent = idx == 0,
                )
            }
        }

    /** 다음 주기가 이미 있으면 그 시작 전날까지, 아니면 다음 예상 월급날 전날까지. */
    private fun cycleOf(c: CycleEntity, next: CycleEntity?, payDay: Int): PayCycle {
        val base = c.toPayCycle(payDay)
        val nextStart = next?.let { LocalDate.ofEpochDay(it.startEpochDay) } ?: return base
        return base.copy(end = nextStart.minusDays(1).coerceAtLeast(base.start))
    }

    /** 내역 CSV (엑셀에서 열 수 있게 UTF-8 BOM). */
    suspend fun exportCsv(): String {
        val rows = dao.allEntriesNow()
        val names = dao.accountsNow().associate { it.id to it.name }
        fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        return buildString {
            append('\uFEFF')
            append("날짜,시각,구분,카테고리,내용,금액,자산,받은 자산,메모,기록 방식\n")
            rows.forEach { e ->
                val type = typeLabel(enumOr(e.type, EntryType.SPEND))
                val src = when (e.source) {
                    EntrySource.NOTIFICATION.name -> "알림 자동"
                    EntrySource.RECURRING.name -> "고정 지출 자동"
                    else -> "직접 입력"
                }
                val time = e.minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty()
                append(
                    listOf(
                        LocalDate.ofEpochDay(e.epochDay).toString(), time, type, Category.of(e.category).label, q(e.label),
                        e.amount.toString(), q(e.accountId?.let(names::get).orEmpty()), q(e.toAccountId?.let(names::get).orEmpty()),
                        q(e.memo), src,
                    ).joinToString(","),
                )
                append('\n')
            }
        }
    }

    private inline fun <T> List<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
        forEachIndexed { i, t -> if (predicate(i, t)) return true }
        return false
    }

    private fun planFor(c: CycleEntity, cycle: PayCycle): CyclePlan = BudgetEngine.plan(
        cycle,
        salary = c.salary,
        fixedExpenses = listOf(FixedExpense("고정비", c.fixedTotal)),
        savings = c.savings,
        settlementDay = DayOfWeek.of(c.settlementDay),
        disposableOverride = c.budgetOverride,
    )

    // ── 초기 설정 ──────────────────────────────────────────────

    /** 처음 설정에서 만들 자산 */
    data class AccountDraft(val name: String, val type: AccountType, val opening: Long, val packageName: String? = null)

    /**
     * 처음 설정. 월급은 여기서 한 번만 받고, 다음 달부터는 월급날·입금 알림으로 자동으로 맞춘다.
     *
     * 한 달 중간에 시작하면 지난 날의 지출은 모르므로 오늘부터 기록하고, [firstCycleBudget](남은 기간 생활비)만
     * 주 예산으로 나눈다. 없으면 남은 날 몫으로 계산한다.
     */
    suspend fun completeOnboarding(
        salary: Long,
        payDay: Int,
        fixed: List<FixedExpense>,
        savings: Long,
        settlementDay: DayOfWeek,
        today: LocalDate,
        firstCycleBudget: Long? = null,
        accounts: List<AccountDraft> = emptyList(),
    ) = writeLock.withLock {
        val ids = accounts.mapIndexed { i, a ->
            dao.saveAccount(AccountEntity(name = a.name.take(20), type = a.type.name, opening = a.opening, sortOrder = i, packageName = a.packageName))
        }
        dao.saveSettings(SettingsEntity(payDay = payDay, settlementDay = settlementDay.value, savings = savings))
        // 고정 지출은 보통 통장에서 나간다
        val bankId = accounts.indexOfFirst { it.type == AccountType.BANK }.takeIf { it >= 0 }?.let { ids[it] }
        dao.replaceFixedExpenses(fixed.map { FixedExpenseEntity(name = it.name, amount = it.amount, day = it.day, accountId = bankId) })
        val cycle = PayCycle.containing(today, payDay)
        val midCycle = today.isAfter(cycle.start)
        dao.saveCycle(
            CycleEntity(
                nominal = cycle.nominal.toString(),
                salary = salary,
                startEpochDay = (if (midCycle) today else cycle.start).toEpochDay(),
                settlementDay = settlementDay.value,
                savings = savings,
                fixedTotal = fixed.sumOf { it.amount },
                budgetOverride = if (midCycle) {
                    firstCycleBudget ?: BudgetEngine.proratedDisposable(
                        (salary - fixed.sumOf { it.amount } - savings).coerceAtLeast(0), cycle, today,
                    )
                } else {
                    null
                },
                salarySource = SalarySource.ONBOARDING.name,
            ),
        )
        dao.insertAppRulesIfAbsent(DefaultApps.presets.map { it.toEntity() })
        recordDueFixedLocked(today)
    }

    /**
     * 예산 설정 바꾸기. 고정비·저축은 [applyNow]면 이번 달에도 바로 반영 (결산 요일은 다음 달부터).
     * [payDay]를 바꾸면 이번 달은 새 월급날 전날까지가 된다.
     */
    suspend fun updateSettings(
        fixed: List<FixedExpenseEntity>,
        savings: Long,
        settlementDay: DayOfWeek,
        applyNow: Boolean,
        payDay: Int? = null,
        today: LocalDate = LocalDate.now(),
    ) = writeLock.withLock {
        val s = dao.settingsNow() ?: return@withLock
        // 이미 적은 달은 그대로 두고, 새로 넣은 고정비는 이번 달 나가는 날이 지났으면 이번 달은 건너뛴다 (이미 낸 것)
        val before = dao.fixedExpensesNow().associateBy { it.id }
        val c0 = dao.latestCycleNow()
        val cycle0 = c0?.toPayCycle(s.payDay)
        dao.syncFixedExpenses(
            fixed.map { f ->
                val old = before[f.id]
                when {
                    old != null -> f.copy(doneNominal = old.doneNominal)
                    c0 != null && cycle0 != null && f.day != null &&
                        Payday.dueDate(cycle0, f.day)?.let { !it.isAfter(today) } == true -> f.copy(doneNominal = c0.nominal)
                    else -> f
                }
            },
        )
        val newPayDay = payDay?.takeIf { it in 1..31 && it != s.payDay && payDayFits(it) } ?: s.payDay
        dao.saveSettings(
            s.copy(
                payDay = newPayDay,
                savings = savings,
                pendingSettlementDay = settlementDay.value.takeIf { it != s.settlementDay },
            ),
        )
        if (applyNow) {
            dao.latestCycleNow()?.let { c ->
                val newFixed = fixed.sumOf { f -> f.amount }
                // 한 달 중간에 시작한 첫 달은 예산을 따로 정해 두었으므로, 바뀐 만큼을 남은 날 몫으로 더하거나 뺀다
                val override = c.budgetOverride?.let { o ->
                    val diff = (c.fixedTotal + c.savings) - (newFixed + savings)
                    val full = PayCycle.cycleFor(YearMonth.parse(c.nominal), s.payDay)
                    val from = LocalDate.ofEpochDay(c.startEpochDay)
                    val part = BudgetEngine.proratedDisposable(kotlin.math.abs(diff), full, from)
                    (o + if (diff >= 0) part else -part).coerceAtLeast(0)
                }
                dao.saveCycle(c.copy(savings = savings, fixedTotal = newFixed, budgetOverride = override))
            }
        }
    }

    /** 새 월급날로 이번 달 끝이 이번 달 시작보다 앞서지 않는지 */
    private suspend fun payDayFits(payDay: Int): Boolean {
        val c = dao.latestCycleNow() ?: return true
        val end = Payday.nextExpected(YearMonth.parse(c.nominal), payDay).minusDays(1)
        return !end.isBefore(LocalDate.ofEpochDay(c.startEpochDay))
    }

    // ── 월급날 자동 처리 ─────────────────────────────────────

    /**
     * 날짜가 바뀌었을 때 할 일: 월급날이 지났으면 새 달을 시작하고, 오늘까지 나간 고정 지출을 적는다.
     * 앱을 켤 때, 1분마다, 입금 알림이 올 때 부른다 (할 일이 없으면 아무것도 안 함).
     */
    suspend fun dailyUpkeep(now: LocalDateTime) = writeLock.withLock {
        rollOverLocked(now)
        recordDueFixedLocked(now.toLocalDate())
    }

    /** 월급날이 지났으면 새 달을 시작한다. 여러 달 안 켰으면 그 달들도 차례로. */
    private suspend fun rollOverLocked(now: LocalDateTime) {
        val today = now.toLocalDate()
        repeat(24) {
            val s = dao.settingsNow() ?: return
            val c = dao.latestCycleNow() ?: return
            val nominal = YearMonth.parse(c.nominal)
            val expected = Payday.nextExpected(nominal, s.payDay)
            val lastStart = LocalDate.ofEpochDay(c.startEpochDay)
            // 다음 월급날 즈음 들어온 월급 (예상보다 며칠 일찍 ~ 며칠 늦게)
            val from = maxOf(expected.minusDays(Payday.EARLY_DAYS), lastStart.plusDays(1))
            val to = minOf(today, expected.plusDays(Payday.LATE_DAYS))
            val deposit = if (from.isAfter(to)) null else dao.salaryIncomeBetween(from.toEpochDay(), to.toEpochDay()).firstOrNull()
            val start = Payday.nextStart(expected, today, deposit?.let { LocalDate.ofEpochDay(it.epochDay) }) ?: return
            startCycleLocked(c, s, nominal.plusMonths(1), start, deposit?.amount, now)
        }
    }

    /**
     * 새 달 시작. 지난달은 새 달 시작 전날에 끝나고, 지난달 마지막 주를 뺀 남은 주는 자동 결산한다
     * (마지막 주는 결산 화면에서 본다). 그 전 달에 남은 주는 모두 자동 결산.
     */
    private suspend fun startCycleLocked(prev: CycleEntity, s: SettingsEntity, nominal: YearMonth, start: LocalDate, salary: Long?, now: LocalDateTime) {
        val cycles = dao.cyclesNow()
        cycles.getOrNull(1)?.let { older ->
            settleAllRemaining(older, cycleOf(older, prev, s.payDay), now, keepLast = false)
        }
        val prevCycle = prev.toPayCycle(s.payDay).let { it.copy(end = start.minusDays(1).coerceAtLeast(it.start)) }
        settleAllRemaining(prev, prevCycle, now, keepLast = true)
        // 지난달 마지막 주 전에 산 모의 구매가 영영 "배송 중"으로 남지 않게
        val lastWeekStart = planFor(prev, prevCycle).weeks.lastOrNull()?.start ?: start
        val stale = dao.mockPurchasesNow().filter { it.status == PurchaseStatus.SHIPPING.name && it.epochDay < lastWeekStart.toEpochDay() }
        if (stale.isNotEmpty()) dao.updateMocks(stale.map { it.copy(status = PurchaseStatus.DELIVERED.name) })
        val settlementDay = s.pendingSettlementDay ?: s.settlementDay
        dao.saveCycle(
            CycleEntity(
                nominal = nominal.toString(),
                salary = salary ?: prev.salary,
                startEpochDay = start.toEpochDay(),
                settlementDay = settlementDay,
                savings = s.savings,
                fixedTotal = dao.fixedExpensesNow().sumOf { it.amount },
                salarySource = (if (salary != null) SalarySource.NOTIFICATION else SalarySource.ESTIMATED).name,
                noticeShown = false,
            ),
        )
        dao.saveSettings(s.copy(settlementDay = settlementDay, pendingSettlementDay = null))
    }

    /** 이번 달 월급을 아직 못 봤는데(지난달 월급으로 둠) 월급이 들어왔으면 그 금액으로 다시 짠다 */
    private suspend fun adjustSalaryLocked(amount: Long, date: LocalDate) {
        val c = dao.latestCycleNow() ?: return
        if (enumOr(c.salarySource, SalarySource.MANUAL) != SalarySource.ESTIMATED) return
        val start = LocalDate.ofEpochDay(c.startEpochDay)
        if (date.isBefore(start.minusDays(Payday.EARLY_DAYS)) || date.isAfter(start.plusDays(Payday.LATE_DAYS))) return
        dao.saveCycle(c.copy(salary = amount, salarySource = SalarySource.NOTIFICATION.name, noticeShown = false))
    }

    /** 이번 달 월급 고치기 */
    suspend fun updateSalary(amount: Long) = writeLock.withLock {
        val c = dao.latestCycleNow() ?: return@withLock
        if (amount <= 0) return@withLock
        dao.saveCycle(c.copy(salary = amount, salarySource = SalarySource.MANUAL.name, noticeShown = true))
    }

    /** 새 달 안내 닫기 */
    suspend fun dismissSalaryNotice() {
        val c = dao.latestCycleNow() ?: return
        if (!c.noticeShown) dao.saveCycle(c.copy(noticeShown = true))
    }

    private suspend fun settleAllRemaining(c: CycleEntity, cycle: PayCycle, now: LocalDateTime, keepLast: Boolean) {
        val done = dao.settledWeeksNow(c.nominal).map { it.weekIndex }.toSet()
        val plan = planFor(c, cycle)
        val last = plan.weeks.lastOrNull()?.index
        plan.weeks.filter { it.index !in done && !(keepLast && it.index == last) }.forEach { w ->
            resolveLeftoverReviews(w.end)
            val r = weekResultNow(c, cycle, w.index)
            settleMocks(w.start, w.end, r?.overspent ?: 0, null)
            dao.markSettled(settledOf(c.nominal, w.index, now, true, r))
        }
    }

    // ── 고정 지출 자동 기록 ───────────────────────────────────

    /** 이번 달 나가는 날이 오늘까지인 고정 지출 중 아직 안 적은 것을 적는다 */
    private suspend fun recordDueFixedLocked(today: LocalDate) {
        val s = dao.settingsNow() ?: return
        val c = dao.latestCycleNow() ?: return
        val cycle = c.toPayCycle(s.payDay)
        val inCycle = dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay())
        dao.fixedExpensesNow().forEach { f ->
            val day = f.day ?: return@forEach
            if (f.doneNominal == c.nominal) return@forEach
            val due = Payday.dueDate(cycle, day) ?: return@forEach
            if (due.isAfter(today)) return@forEach
            dao.upsertFixedExpense(f.copy(doneNominal = c.nominal))
            if (inCycle.any { it.fixedId == f.id }) return@forEach
            dao.insertEntry(
                EntryEntity(
                    epochDay = due.toEpochDay(),
                    amount = f.amount,
                    label = f.name,
                    type = EntryType.FIXED.name,
                    source = EntrySource.RECURRING.name,
                    category = f.category,
                    accountId = f.accountId,
                    fixedId = f.id,
                ),
            )
        }
    }

    /**
     * 결제 알림이 고정 지출이면 고정 지출로 적는다. 그날 자동으로 적어 둔 것이 있으면 실제 금액·날짜로 고친다.
     * 이미 알림으로 적은 고정 지출이면 false (확인함으로).
     */
    private suspend fun recordFixedFromPayment(p: ParsedPayment): Boolean {
        val s = dao.settingsNow() ?: return false
        val c = dao.latestCycleNow() ?: return false
        val cycle = c.toPayCycle(s.payDay)
        val date = p.postedAt.toLocalDate()
        val f = dao.fixedExpensesNow().firstOrNull {
            Payday.matchesFixed(p.rawText, p.amount, date, it.toFixed(), cycle)
        } ?: return false
        val existing = dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay()).firstOrNull { it.fixedId == f.id }
        if (existing != null && existing.source != EntrySource.RECURRING.name) return false
        val entry = EntryEntity(
            id = existing?.id ?: 0,
            epochDay = date.toEpochDay(),
            amount = p.amount,
            label = f.name,
            type = EntryType.FIXED.name,
            source = EntrySource.NOTIFICATION.name,
            packageName = p.packageName,
            postedAtEpochSecond = p.postedAt.epochSecond(),
            category = f.category,
            accountId = accountFor(p.packageName) ?: f.accountId,
            minuteOfDay = p.postedAt.hour * 60 + p.postedAt.minute,
            fixedId = f.id,
        )
        if (existing != null) dao.updateEntry(entry) else dao.insertEntry(entry)
        dao.upsertFixedExpense(f.copy(doneNominal = c.nominal))
        return true
    }

    private suspend fun accountFor(pkg: String?): Long? =
        pkg?.let { p -> dao.accountsNow().firstOrNull { it.packageName == p && !it.hidden }?.id }

    // ── 지출 ─────────────────────────────────────────────────

    /** 직접 적기. 월급 수입을 적으면 월급 입금 알림과 똑같이 새 달·월급을 맞춘다. */
    suspend fun addManual(
        date: LocalDate,
        amount: Long,
        label: String,
        type: EntryType,
        category: Category? = null,
        memo: String = "",
        accountId: Long? = null,
        toAccountId: Long? = null,
        minuteOfDay: Int? = null,
        now: LocalDateTime? = null,
    ) = writeLock.withLock {
        val name = label.ifBlank {
            when (type) {
                EntryType.EXCEPTION -> "예외 지출"
                EntryType.INCOME -> "수입"
                EntryType.TRANSFER -> "이체"
                EntryType.FIXED -> "고정 지출"
                else -> "지출"
            }
        }
        val cat = when {
            type == EntryType.TRANSFER -> Category.ETC
            category != null -> category
            type == EntryType.INCOME -> Category.guessIncome(name, amount, dao.latestCycleNow()?.salary ?: 0)
            else -> CategoryGuesser.guess(name, learned())
        }
        // 가게별 카테고리 기억은 지출에만 (수입 카테고리를 지출 추측에 섞지 않게)
        if (category != null && label.isNotBlank() && !category.income && type != EntryType.TRANSFER) learn(name, category)
        dao.insertEntry(
            EntryEntity(
                epochDay = date.toEpochDay(),
                amount = amount,
                label = name,
                type = type.name,
                source = EntrySource.MANUAL.name,
                category = cat.name,
                memo = memo,
                accountId = accountId,
                toAccountId = if (type == EntryType.TRANSFER) toAccountId else null,
                minuteOfDay = minuteOfDay,
            ),
        )
        if (type == EntryType.INCOME && cat == Category.SALARY) {
            adjustSalaryLocked(amount, date)
            rollOverLocked(now ?: LocalDateTime.now())
        }
    }

    /**
     * 기록 고치기. 카테고리를 바꾸면 그 가게를 기억하고,
     * [applyToSameLabel]이면 같은 가게의 다른 기록도 같이 바꾼다.
     */
    suspend fun updateEntry(updated: EntryEntity, previous: EntryEntity, applyToSameLabel: Boolean) {
        dao.updateEntry(updated)
        if (updated.category != previous.category && !Category.of(updated.category).income) {
            learn(updated.label, Category.of(updated.category))
            if (applyToSameLabel) {
                val key = CategoryGuesser.key(updated.label)
                val same = dao.allEntriesNow().filter { it.id != updated.id && it.type != EntryType.INCOME.name && CategoryGuesser.key(it.label) == key }
                dao.updateEntries(same.map { it.copy(category = updated.category) })
            }
        }
    }

    suspend fun learned(): Map<String, Category> =
        dao.categoryRulesNow().associate { it.labelKey to Category.of(it.category) }

    private suspend fun learn(label: String, category: Category) =
        dao.saveCategoryRule(CategoryRuleEntity(CategoryGuesser.key(label), category.name))

    suspend fun resetLearned() = dao.clearCategoryRules()

    val learnedCount: Flow<Int> = dao.categoryRules().map { it.size }

    suspend fun deleteEntry(e: EntryEntity) = dao.deleteEntry(e)

    /** 지운 기록 되살리기 (같은 id로) */
    suspend fun restoreEntry(e: EntryEntity) {
        dao.insertEntry(e)
    }

    /** 최근 90일에서 자주 쓴 곳 */
    suspend fun quickPicks(today: LocalDate): List<QuickPick> =
        QuickPicks.from(dao.entriesBetweenNow(today.minusDays(90).toEpochDay(), today.toEpochDay()))

    fun entries(cycle: PayCycle): Flow<List<EntryEntity>> =
        dao.entriesBetween(cycle.start.toEpochDay(), cycle.end.toEpochDay())

    // ── 결제 알림 ─────────────────────────────────────────────

    suspend fun watchedPackages(): Set<String> = dao.appRulesNow().filter { it.enabled }.map { it.packageName }.toSet()

    suspend fun markSeen(pkg: String, at: LocalDateTime) = dao.markSeen(SeenAppEntity(pkg, at.epochSecond()))

    suspend fun handleNotification(n: RawNotification) = writeLock.withLock {
        val rules = dao.appRulesNow().map { it.toRule() }
        val result = NotificationParser.parse(n, rules)
        val parsed = when (result) {
            is ParseResult.Payment -> result.payment
            is ParseResult.Unclassified -> result.payment
            else -> null
        }
        val since = n.postedAt.minus(ReviewPolicy.DUPLICATE_WINDOW).epochSecond()
        val recentEntries = dao.notificationEntriesSince(since)
        val inbox = dao.reviewItemsNow().filter { it.postedAtEpochSecond >= since }
        // 같은 앱이 같은 알림을 다시 올린 것 (앱을 다시 켠 뒤 갱신된 알림 등) — 이미 센 것
        if (parsed != null) {
            val at = parsed.postedAt.epochSecond()
            val seen = recentEntries.any { it.packageName == parsed.packageName && it.amount == parsed.amount && it.postedAtEpochSecond == at } ||
                inbox.any { it.packageName == parsed.packageName && it.amount == parsed.amount && it.postedAtEpochSecond == at }
            if (seen) return@withLock
        }
        val recent = recentEntries.mapNotNull { e -> e.toParsedOrNull()?.let { it to e.id } }
        // 확인함에 있는 것도 짝 찾기에 넣는다 (둘 다 확인함에 들어가 정오에 둘 다 반영되는 것을 막음)
        val pending = inbox.map { it.toParsed() to null }
        val fixed = dao.fixedExpensesNow().map { it.toFixed() }

        when (val d = ReviewPolicy.decide(result, (recent + pending).map { it.first }, fixed)) {
            null -> Unit
            is Decision.AutoApply -> if (!recordFixedFromPayment(d.payment)) insertFromPayment(d.payment, EntryType.SPEND)
            is Decision.Income -> {
                dao.insertIncome(IncomeEntity(amount = d.payment.amount, postedAtEpochSecond = d.payment.postedAt.epochSecond()))
                // 다른 앱에 같은 입금이 막 잡혔으면 한 번만 적는다
                val dup = recent.any { (p, _) -> ReviewPolicy.isDuplicate(d.payment, p) }
                if (!dup) {
                    val category = insertFromPayment(d.payment, EntryType.INCOME)
                    // 월급이 들어오면 이번 달 월급을 맞추고, 월급날 전에 들어왔으면 그날부터 새 달
                    if (category == Category.SALARY) {
                        adjustSalaryLocked(d.payment.amount, d.payment.postedAt.toLocalDate())
                        rollOverLocked(d.payment.postedAt)
                    }
                }
            }
            is Decision.NeedsReview -> if (d.reason == ReviewReason.LOOKS_LIKE_FIXED_EXPENSE && recordFixedFromPayment(d.payment)) Unit else dao.insertReview(
                ReviewItemEntity(
                    packageName = d.payment.packageName,
                    action = d.payment.action.name,
                    amount = d.payment.amount,
                    label = d.payment.label,
                    postedAtEpochSecond = d.payment.postedAt.epochSecond(),
                    rawText = d.payment.rawText.take(300),
                    reason = d.reason.name,
                    relatedEntryId = d.related?.let { rel -> recent.firstOrNull { it.first == rel }?.second },
                ),
            )
        }
    }

    /** 알림으로 기록. 고른 카테고리를 돌려준다 */
    private suspend fun insertFromPayment(p: ParsedPayment, type: EntryType): Category {
        val learned = learned()
        val lastSalary = dao.latestCycleNow()?.salary ?: 0
        val category = if (type == EntryType.INCOME) {
            if (Payday.isSalaryDeposit(p.rawText + " " + p.label, p.amount, lastSalary)) {
                Category.SALARY
            } else {
                Category.guessIncome(p.rawText, p.amount, 0).takeIf { it != Category.SALARY } ?: Category.OTHER_INCOME
            }
        } else {
            CategoryGuesser.guess(p.label, learned).takeIf { it != Category.ETC } ?: CategoryGuesser.guess(p.rawText, learned)
        }
        dao.insertEntry(
            EntryEntity(
                epochDay = p.postedAt.toLocalDate().toEpochDay(),
                amount = p.amount,
                label = p.label,
                type = type.name,
                source = EntrySource.NOTIFICATION.name,
                packageName = p.packageName,
                postedAtEpochSecond = p.postedAt.epochSecond(),
                category = category.name,
                accountId = accountFor(p.packageName),
                minuteOfDay = p.postedAt.hour * 60 + p.postedAt.minute,
            ),
        )
        return category
    }

    /** 고정 지출과 이름·날짜로 짝을 못 찾았지만 사용자가 고정 지출이라고 한 것: 금액이 가장 가까운 고정 지출로 */
    private suspend fun insertFixedPlain(p: ParsedPayment) {
        val f = dao.fixedExpensesNow().minByOrNull { kotlin.math.abs(it.amount - p.amount) }
        dao.insertEntry(
            EntryEntity(
                epochDay = p.postedAt.toLocalDate().toEpochDay(),
                amount = p.amount,
                label = f?.name ?: p.label,
                type = EntryType.FIXED.name,
                source = EntrySource.NOTIFICATION.name,
                packageName = p.packageName,
                postedAtEpochSecond = p.postedAt.epochSecond(),
                category = f?.category ?: Category.BILLS.name,
                accountId = accountFor(p.packageName) ?: f?.accountId,
                minuteOfDay = p.postedAt.hour * 60 + p.postedAt.minute,
                fixedId = f?.id,
            ),
        )
    }

    suspend fun resolveReview(item: ReviewItemEntity, resolution: ReviewResolution) = writeLock.withLock {
        resolveReviewLocked(item, resolution)
    }

    /** [writeLock]을 쥔 상태에서만 부른다 (Mutex는 다시 잠글 수 없음) */
    private suspend fun resolveReviewLocked(item: ReviewItemEntity, resolution: ReviewResolution) {
        // 두 번 눌러도 한 번만
        if (dao.reviewItemsNow().none { it.id == item.id }) return
        val p = item.toParsed()
        when (resolution) {
            ReviewResolution.APPLY_SPEND -> insertFromPayment(p, EntryType.SPEND)
            ReviewResolution.APPLY_REFUND -> insertFromPayment(p, EntryType.REFUND)
            ReviewResolution.APPLY_EXCEPTION -> insertFromPayment(p, EntryType.EXCEPTION)
            ReviewResolution.APPLY_FIXED -> if (!recordFixedFromPayment(p)) insertFixedPlain(p)
            ReviewResolution.DISMISS -> Unit
        }
        dao.deleteReview(item)
    }

    // ── 결산 ─────────────────────────────────────────────────

    /**
     * 주간 결산. [returnIds]는 사용자가 고른 반품 물건 (null이면 자동으로 고른다).
     * 자동 결산이면 확인함을 먼저 정리하고, 정리된 지출로 초과액을 다시 계산한다.
     */
    suspend fun settle(pending: PendingWeek, now: LocalDateTime, returnIds: Set<Long>? = null) = writeLock.withLock {
        val s = dao.settingsNow() ?: return@withLock
        val cycles = dao.cyclesNow()
        val i = cycles.indexOfFirst { it.nominal == pending.nominal }
        if (i < 0) return@withLock
        val c = cycles[i]
        val week = pending.result
        // 이미 결산한 주 (버튼 두 번 누름 등)
        if (dao.settledWeeksNow(c.nominal).any { it.weekIndex == week.week.index }) return@withLock
        val auto = Settlement.mustAutoSettle(week.week.end, now)
        if (auto) resolveLeftoverReviews(week.week.end)
        val result = weekResultNow(c, cycleOf(c, cycles.getOrNull(i - 1), s.payDay), week.week.index) ?: week
        settleMocks(result.week.start, result.week.end, result.overspent, returnIds)
        dao.markSettled(settledOf(c.nominal, week.week.index, now, auto, result))
    }

    private fun settledOf(nominal: String, index: Int, now: LocalDateTime, auto: Boolean, r: WeekResult?) = SettledWeekEntity(
        nominal = nominal,
        weekIndex = index,
        settledAtEpochSecond = now.epochSecond(),
        auto = auto,
        budget = r?.budget ?: 0,
        spent = r?.spent ?: 0,
        savedBps = r?.savedBps ?: -1,
        overspent = r?.overspent ?: 0,
    )

    private suspend fun weekResultNow(c: CycleEntity, cycle: PayCycle, index: Int): WeekResult? {
        val plan = planFor(c, cycle)
        val entries = dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay()).map { it.toEntry() }
        val ledger = entries.fold(CycleLedger(plan)) { l, e -> runCatching { l.add(e) }.getOrDefault(l) }
        return ledger.results().getOrNull(index)
    }

    /**
     * 정오 자동 결산 때 확인함에 남은 그 주 알림: 중복 의심은 버리고, 고정 지출 의심은 아직 안 적은 고정 지출이면
     * 고정 지출로, 환불은 환불로, 나머지는 지출로 반영.
     */
    private suspend fun resolveLeftoverReviews(weekEnd: LocalDate) {
        val limit = weekEnd.plusDays(1).atStartOfDay().epochSecond()
        dao.reviewItemsNow().filter { it.postedAtEpochSecond < limit }.forEach { item ->
            val resolution = when (item.reason) {
                // 짝이 되는 지출이 이미 반영돼 있다
                ReviewReason.POSSIBLE_DUPLICATE.name -> ReviewResolution.DISMISS
                ReviewReason.LOOKS_LIKE_FIXED_EXPENSE.name ->
                    if (fixedAlreadyRecorded(item.amount)) ReviewResolution.APPLY_SPEND else ReviewResolution.APPLY_FIXED
                else -> if (item.action == RuleAction.REFUND.name) ReviewResolution.APPLY_REFUND else ReviewResolution.APPLY_SPEND
            }
            resolveReviewLocked(item, resolution)
        }
    }

    /** 이 금액에 가장 가까운 고정 지출을 이번 달에 이미 적었는지 */
    private suspend fun fixedAlreadyRecorded(amount: Long): Boolean {
        val s = dao.settingsNow() ?: return false
        val c = dao.latestCycleNow() ?: return false
        val f = dao.fixedExpensesNow().minByOrNull { kotlin.math.abs(it.amount - amount) } ?: return true
        val cycle = c.toPayCycle(s.payDay)
        return dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay()).any { it.fixedId == f.id }
    }

    // ── 카테고리 예산·저축 목표 ───────────────────────────────

    val categoryBudgets: Flow<Map<Category, Long>> = dao.categoryBudgets().map { l -> l.associate { Category.of(it.category) to it.amount } }

    suspend fun saveCategoryBudgets(budgets: Map<Category, Long>) =
        dao.replaceCategoryBudgets(budgets.filterValues { it > 0 }.map { (c, a) -> CategoryBudgetEntity(c.name, a) })

    val goals: Flow<List<SavingsGoalEntity>> = dao.goals()

    suspend fun addGoal(name: String, target: Long, icon: String, today: LocalDate) {
        val order = (dao.goalsNow().maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.saveGoal(SavingsGoalEntity(name = name.take(30), target = target, icon = icon, createdAtEpochDay = today.toEpochDay(), sortOrder = order))
    }

    suspend fun updateGoal(g: SavingsGoalEntity) = dao.saveGoal(g)

    suspend fun deleteGoal(g: SavingsGoalEntity) = dao.deleteGoal(g)

    // ── 자산 ─────────────────────────────────────────────────

    val accounts: Flow<List<AccountEntity>> = dao.accounts()

    suspend fun saveAccount(a: AccountEntity): Long {
        val order = if (a.id == 0L) (dao.accountsNow().maxOfOrNull { it.sortOrder } ?: -1) + 1 else a.sortOrder
        // 한 앱의 알림은 한 자산으로만
        if (a.packageName != null) {
            dao.accountsNow().filter { it.id != a.id && it.packageName == a.packageName }.forEach { dao.saveAccount(it.copy(packageName = null)) }
        }
        return dao.saveAccount(a.copy(name = a.name.take(20), sortOrder = order))
    }

    /** 자산 지우기. 그 자산으로 적은 기록은 남기고 자산만 비운다 */
    suspend fun deleteAccount(a: AccountEntity) = writeLock.withLock {
        dao.detachAccountFrom(a.id)
        dao.detachAccountTo(a.id)
        dao.detachAccountFixed(a.id)
        dao.deleteAccount(a)
    }

    fun entriesOfAccount(id: Long): Flow<List<EntryEntity>> = dao.entriesOfAccount(id)

    // ── 전체 검색 ─────────────────────────────────────────────

    fun search(query: String): Flow<List<EntryEntity>> {
        val q = query.trim().replace(",", "").removeSuffix("원")
        return if (q.isEmpty()) flowOf(emptyList()) else dao.search(q)
    }

    // ── 백업·복원 ─────────────────────────────────────────────

    /** 모든 기록을 JSON 한 덩어리로 (이 폰 안에서 파일로 저장한다) */
    suspend fun exportBackup(now: LocalDateTime): String = Backup.encode(
        Backup.Data(
            settings = listOfNotNull(dao.settingsNow()),
            fixed = dao.allFixed(),
            cycles = dao.allCycles(),
            entries = dao.allEntriesNow(),
            reviews = dao.allReviews(),
            rules = dao.allRules(),
            settled = dao.allSettled(),
            categoryRules = dao.categoryRulesNow(),
            mocks = dao.mockPurchasesNow(),
            houses = dao.allHouses(),
            categoryBudgets = dao.allCategoryBudgets(),
            goals = dao.goalsNow(),
            accounts = dao.accountsNow(),
        ),
        now,
    )

    /** 백업으로 지금 기록을 모두 바꾼다. 파일이 잘못됐으면 아무것도 바꾸지 않고 예외 */
    suspend fun restoreBackup(json: String) {
        val d = Backup.decode(json)
        writeLock.withLock {
            transaction {
                dao.wipeSettings(); dao.wipeFixed(); dao.wipeCycles(); dao.wipeEntries(); dao.wipeReviews(); dao.wipeIncome()
                dao.wipeRules(); dao.wipeSeen(); dao.wipeSettled(); dao.wipeCategoryRules(); dao.wipeMocks(); dao.wipeHouses()
                dao.wipeCategoryBudgets(); dao.wipeGoals(); dao.wipeAccounts()
                dao.putSettings(d.settings); dao.putFixed(d.fixed); dao.putCycles(d.cycles); dao.putEntries(d.entries)
                dao.putReviews(d.reviews); dao.putRules(d.rules); dao.putSettled(d.settled); dao.putCategoryRules(d.categoryRules)
                dao.putMocks(d.mocks); dao.putHouses(d.houses); dao.putCategoryBudgets(d.categoryBudgets); dao.putGoals(d.goals)
                dao.putAccounts(d.accounts)
            }
        }
    }

    private suspend fun transaction(block: suspend () -> Unit) {
        val database = db
        if (database != null) database.withTransaction { block() } else block()
    }

    // ── 알림 규칙 ─────────────────────────────────────────────

    suspend fun saveRule(rule: AppRule) = dao.saveAppRule(rule.toEntity())
    suspend fun deleteRule(pkg: String) = dao.deleteAppRule(pkg)

    companion object {
        fun typeLabel(t: EntryType): String = when (t) {
            EntryType.SPEND -> "지출"
            EntryType.REFUND -> "환불"
            EntryType.EXCEPTION -> "예외 지출"
            EntryType.INCOME -> "수입"
            EntryType.FIXED -> "고정 지출"
            EntryType.TRANSFER -> "이체"
        }

        @Volatile private var instance: DonRepository? = null
        fun get(context: Context): DonRepository = instance ?: synchronized(this) {
            instance ?: DonDatabase.get(context).let { d -> DonRepository(d.dao(), d) }.also { instance = it }
        }

        @androidx.annotation.VisibleForTesting
        fun resetForTests() = synchronized(this) {
            instance = null
            DonDatabase.resetForTests()
        }
    }
}
