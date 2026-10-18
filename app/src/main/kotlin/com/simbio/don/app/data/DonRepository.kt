package com.simbio.don.app.data

import com.simbio.don.budget.Subcategories
import com.simbio.don.budget.RecurringIncomes
import com.simbio.don.budget.Installments
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
import com.simbio.don.game.CharacterLook
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
    /** 내 캐릭터 모습 */
    val look: CharacterLook = CharacterLook.MALE,
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

/** 연간 통계: 그 해에 시작한 달(월급 주기)들 */
data class YearView(
    val year: Int,
    /** 고를 수 있는 해 (기록이 있는 해, 최근 먼저) */
    val years: List<Int>,
    /** 달마다 수입·지출 (1월부터) */
    val months: List<com.simbio.don.budget.MonthTotal>,
    val entries: List<Entry>,
) {
    val income: Long get() = months.sumOf { it.income }
    val expense: Long get() = months.sumOf { it.expense }
}

enum class ReviewResolution { APPLY_SPEND, APPLY_REFUND, APPLY_EXCEPTION, APPLY_FIXED, DISMISS }

class DonRepository(private val dao: DonDao, private val db: DonDatabase? = null) {

    /**
     * 읽고 → 판단하고 → 쓰는 작업은 한 번에 하나씩. 알림 두 개가 동시에 오거나
     * 결산·월급 확인 버튼을 두 번 눌러도 같은 돈이 두 번 기록되지 않게.
     */
    private val writeLock = Mutex()

    val settings: Flow<SettingsEntity?> = dao.settings()

    /** 직접 만든 카테고리. 읽을 때마다 [Category]에 알려 준다 (기록을 읽기 전에 이름을 알게) */
    val customCategories: Flow<List<Category>> =
        dao.categoryDefs().map { defs -> defs.map { it.toCategory() }.also { Category.register(it) } }

    /** 카테고리 이름 → 소분류 */
    val subcategories: Flow<Map<String, List<SubcategoryEntity>>> = dao.subcategories().map { it.groupBy { s -> s.category } }

    val recurringIncomes: Flow<List<RecurringIncomeEntity>> = dao.recurringIncomes()
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
        combine(dao.settings(), dao.cycles(), customCategories) { s, cs, _ -> s to cs }.flatMapLatest { (s, cs) ->
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
            look = CharacterLook.decode(house?.look),
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
        combine(dao.settings(), dao.cycles(), customCategories) { s, cs, _ -> s to cs }.flatMapLatest { (s, cs) ->
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

    /** 연간 통계: [year]년에 시작한 달들의 수입·지출과 기록 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun year(year: Int): Flow<YearView?> =
        combine(dao.settings(), dao.cycles(), customCategories) { s, cs, _ -> s to cs }.flatMapLatest { (s, cs) ->
            if (s == null || cs.isEmpty()) return@flatMapLatest flowOf(null)
            val years = cs.map { YearMonth.parse(it.nominal).year }.distinct().sortedDescending()
            // cs는 최신 달 먼저: 다음 달은 바로 앞 칸
            val inYear = cs.withIndex().filter { YearMonth.parse(it.value.nominal).year == year }
                .map { (i, c) -> c to cycleOf(c, cs.getOrNull(i - 1), s.payDay) }
                .sortedBy { it.first.nominal }
            if (inYear.isEmpty()) return@flatMapLatest flowOf(YearView(year, years, emptyList(), emptyList()))
            val from = inYear.first().second.start.toEpochDay()
            val to = inYear.last().second.end.toEpochDay()
            dao.entriesBetweenFlow(from, to).map { all ->
                val entries = all.map { it.toEntry() }
                val months = inYear.map { (c, cycle) ->
                    val m = entries.filter { it.date in cycle }
                    com.simbio.don.budget.MonthTotal(YearMonth.parse(c.nominal), Stats.income(m), Stats.total(m))
                }
                YearView(year, years, months, entries)
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

    // ── 카테고리·소분류 ─────────────────────────────────────────

    private suspend fun registerCategories() = Category.register(dao.categoryDefsNow().map { it.toCategory() })

    /** 카테고리 직접 만들기 ("U" + 번호) */
    suspend fun addCategory(label: String, income: Boolean, icon: String, color: Long): Category = writeLock.withLock {
        val defs = dao.categoryDefsNow()
        val next = (defs.mapNotNull { it.name.removePrefix(Category.CUSTOM_PREFIX).toIntOrNull() }.maxOrNull() ?: 0) + 1
        val def = CategoryDefEntity("${Category.CUSTOM_PREFIX}$next", label.trim().take(12).ifBlank { "내 분류" }, income, icon, color, defs.size)
        dao.saveCategoryDef(def)
        registerCategories()
        def.toCategory()
    }

    /** 이름·아이콘·색 고치기 (수입/지출은 그대로) */
    suspend fun updateCategory(name: String, label: String, icon: String, color: Long) = writeLock.withLock {
        val def = dao.categoryDefsNow().firstOrNull { it.name == name } ?: return@withLock
        dao.saveCategoryDef(def.copy(label = label.trim().take(12).ifBlank { def.label }, icon = icon, color = color))
        registerCategories()
    }

    /** 직접 만든 카테고리 지우기: 그 기록·고정 지출·반복 수입·가게 기억은 기타(수입이면 기타 수입)로 */
    suspend fun deleteCategory(name: String) = writeLock.withLock {
        val def = dao.categoryDefsNow().firstOrNull { it.name == name } ?: return@withLock
        val to = if (def.income) Category.OTHER_INCOME.name else Category.ETC.name
        transaction {
            dao.moveCategory(name, to)
            dao.moveFixedCategory(name, to)
            dao.moveIncomeCategory(name, to)
            dao.moveRuleCategory(name, to)
            dao.deleteCategoryBudget(name)
            dao.deleteSubcategoriesOf(name)
            dao.deleteCategoryDef(name)
        }
        registerCategories()
    }

    /** 소분류 더하기 (같은 이름이 있으면 그대로) */
    suspend fun addSubcategory(category: Category, name: String) = writeLock.withLock {
        val n = name.trim().take(12)
        if (n.isEmpty()) return@withLock
        val mine = dao.subcategoriesNow().filter { it.category == category.name }
        if (mine.any { it.name == n }) return@withLock
        dao.saveSubcategory(SubcategoryEntity(category = category.name, name = n, sortOrder = mine.size))
    }

    /** 소분류 이름 바꾸기 — 이미 적은 기록도 같이 */
    suspend fun renameSubcategory(s: SubcategoryEntity, name: String) = writeLock.withLock {
        val n = name.trim().take(12)
        if (n.isEmpty() || n == s.name) return@withLock
        if (dao.subcategoriesNow().any { it.category == s.category && it.name == n }) return@withLock
        transaction {
            dao.saveSubcategory(s.copy(name = n))
            dao.renameSub(s.category, s.name, n)
        }
    }

    /** 소분류 지우기 — 그 기록은 카테고리만 남는다 */
    suspend fun deleteSubcategory(s: SubcategoryEntity) = writeLock.withLock {
        transaction {
            dao.deleteSubcategory(s.id)
            dao.renameSub(s.category, s.name, null)
        }
    }

    /** 처음 한 번 기본 소분류를 넣는다 (지운 것을 다시 넣지 않게 표시) */
    private suspend fun seedSubcategoriesLocked() {
        val s = dao.settingsNow() ?: return
        if (s.subSeeded) return
        val have = dao.subcategoriesNow().map { it.category }.toSet()
        dao.insertSubcategories(
            Subcategories.defaults.filterKeys { it.name !in have }.flatMap { (c, names) ->
                names.mapIndexed { i, n -> SubcategoryEntity(category = c.name, name = n, sortOrder = i) }
            },
        )
        dao.saveSettings(s.copy(subSeeded = true))
    }

    // ── 할부 ─────────────────────────────────────────────────

    /**
     * 할부로 산 것: [months]개월로 나눠 산 날부터 달마다 같은 날 지출로 적는다 (남는 원은 첫 달에).
     * 알림에서 왔으면 [from]의 앱·시각을 첫 기록에 남겨 같은 알림을 두 번 세지 않게 한다.
     */
    suspend fun addInstallment(
        date: LocalDate,
        total: Long,
        months: Int,
        label: String,
        category: Category,
        sub: String? = null,
        memo: String = "",
        accountId: Long? = null,
        minuteOfDay: Int? = null,
    ): Long = writeLock.withLock {
        if (label.isNotBlank() && !category.income) learn(label, category)
        addInstallmentLocked(date, total, months, label.ifBlank { "할부" }, category, sub, memo, accountId, minuteOfDay, null)
    }

    private suspend fun addInstallmentLocked(
        date: LocalDate,
        total: Long,
        months: Int,
        label: String,
        category: Category,
        sub: String?,
        memo: String,
        accountId: Long?,
        minuteOfDay: Int?,
        from: ParsedPayment?,
    ): Long {
        val amounts = Installments.split(total, months)
        val dates = Installments.dates(date, months)
        var id = 0L
        transaction {
            id = dao.insertInstallment(InstallmentEntity(label = label, total = total, months = months, firstEpochDay = date.toEpochDay(), category = category.name, accountId = accountId))
            amounts.forEachIndexed { i, a ->
                dao.insertEntry(
                    EntryEntity(
                        epochDay = dates[i].toEpochDay(),
                        amount = a,
                        label = label,
                        type = EntryType.SPEND.name,
                        source = if (i == 0 && from != null) EntrySource.NOTIFICATION.name else EntrySource.MANUAL.name,
                        packageName = if (i == 0) from?.packageName else null,
                        postedAtEpochSecond = if (i == 0) from?.postedAt?.epochSecond() else null,
                        category = category.name,
                        memo = memo,
                        accountId = accountId,
                        minuteOfDay = if (i == 0) minuteOfDay else null,
                        sub = sub,
                        installmentId = id,
                        installmentNo = "${i + 1}/$months",
                    ),
                )
            }
        }
        return id
    }

    /** 할부 전체 지우기 (나눠 적은 기록 모두) */
    suspend fun deleteInstallment(id: Long) = writeLock.withLock {
        transaction {
            dao.deleteInstallmentEntries(id)
            dao.deleteInstallment(id)
        }
    }

    suspend fun installment(id: Long): InstallmentEntity? = dao.installment(id)

    // ── 반복 수입 ─────────────────────────────────────────────

    /**
     * 반복 수입 목록 저장. 새로 넣은 것은 이번 달 이미 지난 날을 건너뛴다 (이미 받은 것).
     * 이미 있던 것은 처리한 날을 그대로 둔다.
     */
    suspend fun saveRecurringIncomes(items: List<RecurringIncomeEntity>, today: LocalDate = LocalDate.now()) = writeLock.withLock {
        val before = dao.recurringIncomesNow().associateBy { it.id }
        val s = dao.settingsNow()
        val cycle = s?.let { dao.latestCycleNow()?.toPayCycle(it.payDay) }
        val keep = items.filter { it.id != 0L }.map { it.id }.toSet()
        transaction {
            before.keys.filter { it !in keep }.forEach { dao.deleteRecurringIncome(it) }
            items.filter { it.amount > 0 && it.day in 1..31 }.forEach { r ->
                val old = before[r.id]
                val last = when {
                    old != null -> old.lastDueEpochDay
                    cycle != null -> Payday.dueDates(cycle, r.day).lastOrNull { !it.isAfter(today) }?.toEpochDay()
                    else -> null
                }
                dao.upsertRecurringIncome(r.copy(name = r.name.trim().take(20), lastDueEpochDay = last))
            }
        }
        recordDueIncomeLocked(today)
    }

    /** 반복 수입 중 들어올 날이 오늘까지인 것을 적는다 (그 근처에 이미 적은 수입이 있으면 안 적음) */
    private suspend fun recordDueIncomeLocked(today: LocalDate) {
        val s = dao.settingsNow() ?: return
        val c = dao.latestCycleNow() ?: return
        val cycle = c.toPayCycle(s.payDay)
        val incomes = dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay()).filter { it.type == EntryType.INCOME.name }
        dao.recurringIncomesNow().forEach { r ->
            val due = Payday.dueDates(cycle, r.day).filter { !it.isAfter(today) && it.toEpochDay() > (r.lastDueEpochDay ?: Long.MIN_VALUE) }
            if (due.isEmpty()) return@forEach
            due.forEach { d ->
                val near = incomes.any { e ->
                    kotlin.math.abs(e.epochDay - d.toEpochDay()) <= FIXED_MATCH_DAYS &&
                        (e.incomeId == r.id || (e.incomeId == null && (e.label == r.name || e.amount == r.amount)))
                }
                if (!near) {
                    dao.insertEntry(
                        EntryEntity(
                            epochDay = d.toEpochDay(),
                            amount = r.amount,
                            label = r.name,
                            type = EntryType.INCOME.name,
                            source = EntrySource.RECURRING.name,
                            category = r.category,
                            accountId = r.accountId,
                            incomeId = r.id,
                        ),
                    )
                }
            }
            dao.upsertRecurringIncome(r.copy(lastDueEpochDay = due.last().toEpochDay()))
        }
    }

    /**
     * 입금 알림이 반복 수입이면 그것으로 적는다. 그 날 근처에 저절로 적어 둔 것이 있으면 실제 금액·날짜로 고친다.
     * 이름이 알림에 있으면 바로, 금액·날짜만 맞으면 월급처럼 보이지 않을 때만. 반복 수입이 아니면 false.
     */
    private suspend fun recordIncomeFromPayment(p: ParsedPayment): Boolean {
        val date = p.postedAt.toLocalDate()
        val (_, cycle) = cycleAt(date) ?: return false
        val text = p.rawText + " " + p.label
        val lastSalary = dao.latestCycleNow()?.salary ?: 0
        val r = dao.recurringIncomesNow().firstOrNull { r ->
            val named = r.name.length >= 2 && r.name in text
            RecurringIncomes.matches(text, p.amount, date, r.toRecurring(), cycle) &&
                (named || !Payday.isSalaryDeposit(text, p.amount, lastSalary))
        } ?: return false
        val existing = dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay())
            .filter { it.incomeId == r.id && kotlin.math.abs(it.epochDay - date.toEpochDay()) <= FIXED_MATCH_DAYS }
            .minByOrNull { kotlin.math.abs(it.epochDay - date.toEpochDay()) }
        val replace = existing?.takeIf { it.source == EntrySource.RECURRING.name }
        val entry = EntryEntity(
            id = replace?.id ?: 0,
            epochDay = date.toEpochDay(),
            amount = p.amount,
            label = r.name,
            type = EntryType.INCOME.name,
            source = EntrySource.NOTIFICATION.name,
            packageName = p.packageName,
            postedAtEpochSecond = p.postedAt.epochSecond(),
            category = r.category,
            accountId = accountFor(p.packageName) ?: r.accountId,
            minuteOfDay = p.postedAt.hour * 60 + p.postedAt.minute,
            incomeId = r.id,
        )
        if (replace != null) dao.updateEntry(entry) else dao.insertEntry(entry)
        // 들어오기로 한 날 전에 먼저 들어왔으면 그날 또 적지 않게
        val due = Payday.dueDates(cycle, r.day).minByOrNull { kotlin.math.abs(it.toEpochDay() - date.toEpochDay()) }
        if (due != null && due.toEpochDay() > (r.lastDueEpochDay ?: Long.MIN_VALUE)) {
            dao.upsertRecurringIncome(r.copy(lastDueEpochDay = due.toEpochDay()))
        }
        return true
    }

    // ── 캐릭터 ─────────────────────────────────────────────────

    /** 캐릭터 모습 저장 (집 단계·이름은 그대로) */
    suspend fun saveLook(look: CharacterLook) = writeLock.withLock { saveLookLocked(look) }

    private suspend fun saveLookLocked(look: CharacterLook) {
        val h = dao.houseNow() ?: HouseEntity()
        dao.saveHouse(h.copy(look = look.encode()))
    }

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
        look: CharacterLook = CharacterLook.MALE,
    ) = writeLock.withLock {
        saveLookLocked(look)
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
        seedSubcategoriesLocked()
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
        // 이미 처리한 납부일은 그대로 두고, 새로 넣은 고정비는 이번 달 이미 지난 납부일을 건너뛴다 (이미 낸 것)
        val before = dao.fixedExpensesNow().associateBy { it.id }
        val cycle0 = dao.latestCycleNow()?.toPayCycle(s.payDay)
        dao.syncFixedExpenses(
            fixed.map { f ->
                val old = before[f.id]
                when {
                    old != null -> f.copy(lastDueEpochDay = old.lastDueEpochDay)
                    cycle0 != null && f.day != null ->
                        f.copy(lastDueEpochDay = Payday.dueDates(cycle0, f.day).lastOrNull { !it.isAfter(today) }?.toEpochDay())
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
        recordDueIncomeLocked(now.toLocalDate())
        seedSubcategoriesLocked()
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
            // 상여·용돈처럼 적은 입금은 월급으로 보지 않는다 (지난 월급의 70% 이상만, 가장 큰 것)
            val deposit = if (from.isAfter(to)) {
                null
            } else {
                dao.salaryIncomeBetween(from.toEpochDay(), to.toEpochDay()).firstOrNull { it.amount * 10 >= c.salary * 7 }
            }
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
        // 결산 화면으로 남는 지난달 마지막 주 전에 산 모의 구매가 영영 "배송 중"으로 남지 않게.
        // 월급이 일찍 들어와 짧아진 마지막 날들이 이미 결산한 주에 합쳐졌으면 새 달 시작 전 것 모두
        val lastWeek = planFor(prev, prevCycle).weeks.lastOrNull()
        val lastSettled = lastWeek != null && dao.settledWeeksNow(prev.nominal).any { it.weekIndex == lastWeek.index }
        val until = if (lastWeek == null || lastSettled) start else lastWeek.start
        val stale = dao.mockPurchasesNow().filter { it.status == PurchaseStatus.SHIPPING.name && it.epochDay < until.toEpochDay() }
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

    /**
     * 새 달 즈음 월급이 들어오면 이번 달 월급을 맞춘다: 아직 못 봤으면(지난달 월급으로 둠) 그 금액으로,
     * 이미 입금으로 맞췄는데 더 큰 입금이 오면(먼저 온 건 상여 등) 큰 쪽으로. 직접 고친 월급은 그대로.
     */
    private suspend fun adjustSalaryLocked(amount: Long, date: LocalDate) {
        val c = dao.latestCycleNow() ?: return
        val source = enumOr(c.salarySource, SalarySource.MANUAL)
        val start = LocalDate.ofEpochDay(c.startEpochDay)
        if (date.isBefore(start.minusDays(Payday.EARLY_DAYS)) || date.isAfter(start.plusDays(Payday.LATE_DAYS))) return
        val apply = source == SalarySource.ESTIMATED || (source == SalarySource.NOTIFICATION && amount > c.salary)
        if (apply) dao.saveCycle(c.copy(salary = amount, salarySource = SalarySource.NOTIFICATION.name, noticeShown = false))
    }

    /** 이번 달 월급 고치기 */
    suspend fun updateSalary(amount: Long) = writeLock.withLock {
        val c = dao.latestCycleNow() ?: return@withLock
        if (amount <= 0) return@withLock
        dao.saveCycle(c.copy(salary = amount, salarySource = SalarySource.MANUAL.name, noticeShown = true))
    }

    /** 새 달 안내 닫기 */
    suspend fun dismissSalaryNotice() = writeLock.withLock {
        val c = dao.latestCycleNow() ?: return@withLock
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

    /** 고정 지출과 그 기록을 같은 것으로 보는 날짜 차이 (알림이 며칠 일찍·늦게 와도) */
    private val FIXED_MATCH_DAYS = 10L

    /** [date]가 들어 있는 주기 (다음 주기가 있으면 그 시작 전날까지) */
    private suspend fun cycleAt(date: LocalDate): Pair<CycleEntity, PayCycle>? {
        val s = dao.settingsNow() ?: return null
        val cycles = dao.cyclesNow()
        cycles.forEachIndexed { i, c ->
            val cycle = cycleOf(c, cycles.getOrNull(i - 1), s.payDay)
            if (date in cycle) return c to cycle
        }
        return null
    }

    /**
     * 이번 달 납부일이 오늘까지인 고정 지출 중 아직 처리하지 않은 납부일을 적는다 (한 달에 두 번 올 수도 있다).
     * 그 납부일 근처에 이미 알림·직접 기록한 고정 지출이 있으면 새로 적지 않는다.
     */
    private suspend fun recordDueFixedLocked(today: LocalDate) {
        val s = dao.settingsNow() ?: return
        val c = dao.latestCycleNow() ?: return
        val cycle = c.toPayCycle(s.payDay)
        val inCycle = dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay())
            .filter { it.type == EntryType.FIXED.name }
        dao.fixedExpensesNow().forEach { f ->
            val day = f.day ?: return@forEach
            val due = Payday.dueDates(cycle, day).filter { !it.isAfter(today) && it.toEpochDay() > (f.lastDueEpochDay ?: Long.MIN_VALUE) }
            if (due.isEmpty()) return@forEach
            due.forEach { d ->
                val near = inCycle.any { e ->
                    kotlin.math.abs(e.epochDay - d.toEpochDay()) <= FIXED_MATCH_DAYS &&
                        (e.fixedId == f.id || (e.fixedId == null && (e.label == f.name || e.amount == f.amount)))
                }
                if (!near) {
                    dao.insertEntry(
                        EntryEntity(
                            epochDay = d.toEpochDay(),
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
            dao.upsertFixedExpense(f.copy(lastDueEpochDay = due.last().toEpochDay()))
        }
    }

    /**
     * 결제 알림이 고정 지출이면 고정 지출로 적는다 (결제한 날이 든 달 기준).
     * 그 납부일에 자동으로 적어 둔 것이 있으면 실제 금액·날짜로 고친다. 이미 알림으로 적은 고정 지출이면 false (확인함으로).
     */
    private suspend fun recordFixedFromPayment(p: ParsedPayment): Boolean {
        val date = p.postedAt.toLocalDate()
        val (_, cycle) = cycleAt(date) ?: return false
        val f = dao.fixedExpensesNow().firstOrNull {
            Payday.matchesFixed(p.rawText, p.amount, date, it.toFixed(), cycle)
        } ?: return false
        return applyFixedPayment(f, cycle, p, strict = true)
    }

    /**
     * 고정 지출 [f]의 결제 [p]를 적는다. 가까운 날짜의 자동 기록(아직 결제 확인 안 됨)이 있으면 그것을 고치고,
     * 이미 확인된 기록이 있으면 [strict]일 때 false (중복일 수 있어 확인함으로), 아니면 하나 더 적는다.
     */
    private suspend fun applyFixedPayment(f: FixedExpenseEntity, cycle: PayCycle, p: ParsedPayment, strict: Boolean): Boolean {
        val date = p.postedAt.toLocalDate()
        val existing = dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay())
            .filter { it.fixedId == f.id && kotlin.math.abs(it.epochDay - date.toEpochDay()) <= FIXED_MATCH_DAYS }
            .minByOrNull { kotlin.math.abs(it.epochDay - date.toEpochDay()) }
        val replace = existing?.takeIf { it.source == EntrySource.RECURRING.name }
        if (existing != null && replace == null && strict) return false
        val entry = EntryEntity(
            id = replace?.id ?: 0,
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
        if (replace != null) dao.updateEntry(entry) else dao.insertEntry(entry)
        // 납부일 전에 먼저 빠져나갔으면 그 납부일에 또 적지 않게
        val due = f.day?.let { d -> Payday.dueDates(cycle, d).minByOrNull { kotlin.math.abs(it.toEpochDay() - date.toEpochDay()) } }
        if (due != null && due.toEpochDay() > (f.lastDueEpochDay ?: Long.MIN_VALUE)) {
            dao.upsertFixedExpense(f.copy(lastDueEpochDay = due.toEpochDay()))
        }
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
        sub: String? = null,
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
                sub = sub?.takeIf { type != EntryType.TRANSFER && it.isNotBlank() },
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
        // 월급날 새벽에 온 알림이 지난달에 적히지 않게, 새 달이 됐으면 먼저 시작한다
        rollOverLocked(n.postedAt)
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
            // 할부는 첫 달 몫만 적으므로 금액 대신 앱·시각으로 본다
            val seen = recentEntries.any {
                it.packageName == parsed.packageName && it.postedAtEpochSecond == at && (it.amount == parsed.amount || it.installmentId != null)
            } ||
                inbox.any { it.packageName == parsed.packageName && it.amount == parsed.amount && it.postedAtEpochSecond == at }
            if (seen) return@withLock
        }
        // 할부 첫 기록은 산 금액 전체로 견준다 (다른 앱에 온 같은 결제 알림과 짝이 맞게)
        val recent = recentEntries.mapNotNull { e ->
            e.toParsedOrNull()?.let { p ->
                val total = e.installmentId?.let { dao.installment(it)?.total }
                (if (total != null) p.copy(amount = total) else p) to e.id
            }
        }
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
                if (!dup && !recordIncomeFromPayment(d.payment)) {
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
        // 할부 결제 알림 ("3개월")이면 달마다 나눠 적는다
        val months = if (type == EntryType.SPEND) Installments.monthsIn(p.rawText) else null
        if (months != null && p.amount >= months) {
            addInstallmentLocked(
                p.postedAt.toLocalDate(), p.amount, months, p.label, category, null, "", accountFor(p.packageName),
                p.postedAt.hour * 60 + p.postedAt.minute, p,
            )
            return category
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
        val date = p.postedAt.toLocalDate()
        val f = dao.fixedExpensesNow().minByOrNull { kotlin.math.abs(it.amount - p.amount) }
        val cycle = cycleAt(date)?.second
        if (f != null && cycle != null) {
            applyFixedPayment(f, cycle, p, strict = false)
            return
        }
        dao.insertEntry(
            EntryEntity(
                epochDay = date.toEpochDay(),
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
                    if (fixedAlreadyPaid(item.amount, item.postedAtEpochSecond.toLocalDateTime().toLocalDate())) ReviewResolution.APPLY_SPEND else ReviewResolution.APPLY_FIXED
                else -> if (item.action == RuleAction.REFUND.name) ReviewResolution.APPLY_REFUND else ReviewResolution.APPLY_SPEND
            }
            resolveReviewLocked(item, resolution)
        }
    }

    /** 이 금액에 가장 가까운 고정 지출이 그날 근처에 이미 결제 확인됐는지 (자동으로만 적어 둔 건 아직 아님) */
    private suspend fun fixedAlreadyPaid(amount: Long, date: LocalDate): Boolean {
        val f = dao.fixedExpensesNow().minByOrNull { kotlin.math.abs(it.amount - amount) } ?: return true
        val cycle = cycleAt(date)?.second ?: return true
        return dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay()).any {
            it.fixedId == f.id && it.source != EntrySource.RECURRING.name && kotlin.math.abs(it.epochDay - date.toEpochDay()) <= FIXED_MATCH_DAYS
        }
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
            categoryDefs = dao.categoryDefsNow(),
            subcategories = dao.subcategoriesNow(),
            installments = dao.allInstallments(),
            recurringIncomes = dao.recurringIncomesNow(),
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
                dao.wipeCategoryDefs(); dao.wipeSubcategories(); dao.wipeInstallments(); dao.wipeRecurringIncomes()
                dao.putSettings(d.settings); dao.putFixed(d.fixed); dao.putCycles(d.cycles); dao.putEntries(d.entries)
                dao.putReviews(d.reviews); dao.putRules(d.rules); dao.putSettled(d.settled); dao.putCategoryRules(d.categoryRules)
                dao.putMocks(d.mocks); dao.putHouses(d.houses); dao.putCategoryBudgets(d.categoryBudgets); dao.putGoals(d.goals)
                dao.putAccounts(d.accounts)
                dao.putCategoryDefs(d.categoryDefs); dao.putSubcategories(d.subcategories); dao.putInstallments(d.installments)
                dao.putRecurringIncomes(d.recurringIncomes)
            }
            Category.register(dao.categoryDefsNow().map { it.toCategory() })
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
