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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** 첫 화면에 필요한 것 전부. */
data class HomeState(
    val today: LocalDate,
    val payDay: Int,
    val cycle: PayCycle,
    val plan: CyclePlan,
    val ledger: CycleLedger,
    val results: List<WeekResult>,
    val currentWeek: WeekResult?,
    val savedSoFar: Long,
    val reviewCount: Int,
    /** 결산할 수 있는 지난 주 (가장 오래된 것부터) */
    val settleable: List<WeekResult>,
    /** 끝난 주가 있지만 확인함이 남아 정오까지 결산을 기다리는 중 */
    val waitingForInbox: Boolean,
    /** 다음 월급날이 지나 새 달 월급을 확인해야 함 */
    val needsSalaryConfirm: Boolean,
    val nextExpectedPayday: LocalDate,
    val lastSalary: Long,
    /** 이번 주기 기록 (최신순) */
    val entries: List<EntryEntity>,
    /** 이번 달 실제로 나간 돈 (예외 지출 포함, 환불 뺌) */
    val monthSpent: Long,
    /** 이번 주 남은 돈 ÷ 남은 날 */
    val todayAllowance: Long,
    // ── 게임 ──
    val mockEntities: List<MockPurchaseEntity>,
    val mockItems: List<MockItem>,
    /** 모의 지갑 = 이번 달 가용액 − 실제 쓴 돈 − 아직 돌려받지 않은 모의 구매 */
    val wallet: Long,
    val house: HouseLayout,
    val mood: Mood,
) {
    val shipping: List<MockItem> get() = mockItems.filter { it.status == PurchaseStatus.SHIPPING }
}

/** 내역·통계 화면용 한 달 묶음. */
data class MonthView(
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

enum class ReviewResolution { APPLY_SPEND, APPLY_REFUND, APPLY_EXCEPTION, DISMISS }

class DonRepository(private val dao: DonDao) {

    val settings: Flow<SettingsEntity?> = dao.settings()
    val fixedExpenses: Flow<List<FixedExpenseEntity>> = dao.fixedExpenses()
    val reviewItems: Flow<List<ReviewItemEntity>> = dao.reviewItems()
    val appRules: Flow<List<AppRuleEntity>> = dao.appRules()
    val seenApps: Flow<List<SeenAppEntity>> = dao.seenApps()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun home(now: Flow<LocalDateTime>): Flow<HomeState?> =
        combine(dao.settings(), dao.latestCycle()) { s, c -> s to c }.flatMapLatest { (s, c) ->
            if (s == null || c == null) return@flatMapLatest flowOf(null)
            val cycle = c.toPayCycle(s.payDay)
            combine(
                dao.entriesBetween(cycle.start.toEpochDay(), cycle.end.toEpochDay()),
                dao.reviewItems(),
                dao.settledWeeks(c.nominal),
                now,
                combine(dao.mockPurchases(), dao.house()) { m, h -> m to h },
            ) { entries, reviews, settled, t, game -> buildHome(s, c, cycle, entries, reviews, settled, t, game.first, game.second) }
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
    ): HomeState {
        val plan = planFor(c, cycle)
        val ledger = entries.fold(CycleLedger(plan)) { l, e -> runCatching { l.add(e.toEntry()) }.getOrDefault(l) }
        val results = ledger.results()
        val today = now.toLocalDate()
        val settledIdx = settled.map { it.weekIndex }.toSet()
        val inboxEmpty = reviews.isEmpty()
        val nextPayday = PayCycle.expectedPayday(cycle.nominal.plusMonths(1), s.payDay)
        val current = plan.weekIndexOf(today)?.let { results[it] }
        return HomeState(
            today = today,
            payDay = s.payDay,
            cycle = cycle,
            plan = plan,
            ledger = ledger,
            results = results,
            currentWeek = current,
            savedSoFar = ledger.savedSoFar(today),
            reviewCount = reviews.size,
            settleable = results.filterIndexed { i, r ->
                i !in settledIdx && Settlement.canSettle(r.week.end, now, inboxEmpty)
            },
            waitingForInbox = !inboxEmpty && results.anyIndexed { i, r ->
                i !in settledIdx && today.isAfter(r.week.end) && !Settlement.mustAutoSettle(r.week.end, now)
            },
            needsSalaryConfirm = !today.isBefore(nextPayday),
            nextExpectedPayday = nextPayday,
            lastSalary = c.salary,
            entries = entries,
            monthSpent = Stats.total(entries.map { it.toEntry() }),
            todayAllowance = current?.let { Stats.todayAllowance(it, today) } ?: 0,
            mockEntities = mocks,
            mockItems = mocks.map { it.toItem() },
            wallet = MockWallet.balance(plan.disposable, Stats.total(entries.map { it.toEntry() }), mocks.map { it.toItem() }),
            house = HouseLayout.of(HouseLevel.valueOf(house?.level ?: HouseLevel.ONE_ROOM.name), mocks.map { it.toItem() }),
            mood = Moods.of(current, today),
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
        fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        return buildString {
            append('\uFEFF')
            append("날짜,구분,카테고리,내용,금액,메모,기록 방식\n")
            rows.forEach { e ->
                val type = when (EntryType.valueOf(e.type)) {
                    EntryType.SPEND -> "지출"
                    EntryType.REFUND -> "환불"
                    EntryType.EXCEPTION -> "예외 지출"
                }
                val src = if (e.source == EntrySource.NOTIFICATION.name) "알림 자동" else "직접 입력"
                append(listOf(LocalDate.ofEpochDay(e.epochDay).toString(), type, Category.of(e.category).label, q(e.label),
                    e.amount.toString(), q(e.memo), src).joinToString(","))
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

    suspend fun completeOnboarding(
        salary: Long,
        payDay: Int,
        fixed: List<FixedExpense>,
        savings: Long,
        settlementDay: DayOfWeek,
        today: LocalDate,
        firstCycleBudget: Long? = null,
    ) {
        dao.saveSettings(SettingsEntity(payDay = payDay, settlementDay = settlementDay.value, savings = savings))
        dao.replaceFixedExpenses(fixed.map { FixedExpenseEntity(name = it.name, amount = it.amount) })
        val cycle = PayCycle.containing(today, payDay)
        // 한 달 중간에 시작하면 지난 날의 지출은 모르므로 오늘부터 기록하고, 남은 날 몫만 예산으로 잡는다
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
            ),
        )
        dao.insertAppRulesIfAbsent(DefaultApps.presets.map { it.toEntity() })
    }

    /** 고정비·저축 변경을 이번 달 예산에 바로 반영 (결산 요일은 다음 달부터). */
    suspend fun updateSettings(fixed: List<FixedExpense>, savings: Long, settlementDay: DayOfWeek, applyNow: Boolean) {
        val s = dao.settingsNow() ?: return
        dao.replaceFixedExpenses(fixed.map { FixedExpenseEntity(name = it.name, amount = it.amount) })
        dao.saveSettings(
            s.copy(
                savings = savings,
                pendingSettlementDay = settlementDay.value.takeIf { it != s.settlementDay },
            ),
        )
        if (applyNow) {
            dao.latestCycleNow()?.let { dao.saveCycle(it.copy(savings = savings, fixedTotal = fixed.sumOf { f -> f.amount })) }
        }
    }

    // ── 월급 확인 ─────────────────────────────────────────────

    /** 새 달 월급 제안 금액: 월급날 전후 입금 알림 중 가장 큰 것, 없으면 지난달 월급. */
    suspend fun suggestedSalary(expectedPayday: LocalDate): Long {
        val since = expectedPayday.minusDays(5).atStartOfDay().epochSecond()
        return dao.largestIncomeSince(since)?.amount ?: dao.latestCycleNow()?.salary ?: 0
    }

    suspend fun confirmSalary(amount: Long, actualStart: LocalDate, now: LocalDateTime) {
        val s = dao.settingsNow() ?: return
        val prev = dao.latestCycleNow() ?: return
        settleAllRemaining(prev, s, now)
        val settlementDay = s.pendingSettlementDay ?: s.settlementDay
        val nominal = YearMonth.parse(prev.nominal).plusMonths(1)
        dao.saveCycle(
            CycleEntity(
                nominal = nominal.toString(),
                salary = amount,
                startEpochDay = actualStart.toEpochDay(),
                settlementDay = settlementDay,
                savings = s.savings,
                fixedTotal = dao.fixedExpensesNow().sumOf { it.amount },
            ),
        )
        dao.saveSettings(s.copy(settlementDay = settlementDay, pendingSettlementDay = null))
    }

    private suspend fun settleAllRemaining(c: CycleEntity, s: SettingsEntity, now: LocalDateTime) {
        val done = dao.settledWeeksNow(c.nominal).map { it.weekIndex }.toSet()
        val plan = planFor(c, c.toPayCycle(s.payDay))
        plan.weeks.filter { it.index !in done }.forEach { w ->
            resolveLeftoverReviews(w.end)
            val r = weekResultNow(c, s.payDay, w.index)
            settleMocks(w.start, w.end, r?.overspent ?: 0, null)
            dao.markSettled(SettledWeekEntity(c.nominal, w.index, now.epochSecond(), auto = true))
        }
    }

    // ── 지출 ─────────────────────────────────────────────────

    suspend fun addManual(
        date: LocalDate,
        amount: Long,
        label: String,
        type: EntryType,
        category: Category? = null,
        memo: String = "",
    ) {
        val name = label.ifBlank { if (type == EntryType.EXCEPTION) "예외 지출" else "지출" }
        val cat = category ?: CategoryGuesser.guess(name, learned())
        if (category != null && label.isNotBlank()) learn(name, category)
        dao.insertEntry(
            EntryEntity(
                epochDay = date.toEpochDay(),
                amount = amount,
                label = name,
                type = type.name,
                source = EntrySource.MANUAL.name,
                category = cat.name,
                memo = memo,
            ),
        )
    }

    /**
     * 기록 고치기. 카테고리를 바꾸면 그 가게를 기억하고,
     * [applyToSameLabel]이면 같은 가게의 다른 기록도 같이 바꾼다.
     */
    suspend fun updateEntry(updated: EntryEntity, previous: EntryEntity, applyToSameLabel: Boolean) {
        dao.updateEntry(updated)
        if (updated.category != previous.category) {
            learn(updated.label, Category.of(updated.category))
            if (applyToSameLabel) {
                val key = CategoryGuesser.key(updated.label)
                val same = dao.allEntriesNow().filter { it.id != updated.id && CategoryGuesser.key(it.label) == key }
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

    fun entries(cycle: PayCycle): Flow<List<EntryEntity>> =
        dao.entriesBetween(cycle.start.toEpochDay(), cycle.end.toEpochDay())

    // ── 결제 알림 ─────────────────────────────────────────────

    suspend fun watchedPackages(): Set<String> = dao.appRulesNow().filter { it.enabled }.map { it.packageName }.toSet()

    suspend fun markSeen(pkg: String, at: LocalDateTime) = dao.markSeen(SeenAppEntity(pkg, at.epochSecond()))

    suspend fun handleNotification(n: RawNotification) {
        val rules = dao.appRulesNow().map { it.toRule() }
        val result = NotificationParser.parse(n, rules)
        val since = n.postedAt.minus(ReviewPolicy.DUPLICATE_WINDOW).epochSecond()
        val recentEntries = dao.notificationEntriesSince(since)
        val recent = recentEntries.mapNotNull { e -> e.toParsedOrNull()?.let { it to e.id } }
        val fixed = dao.fixedExpensesNow().map { it.toFixed() }

        when (val d = ReviewPolicy.decide(result, recent.map { it.first }, fixed)) {
            null -> Unit
            is Decision.AutoApply -> insertFromPayment(d.payment, EntryType.SPEND)
            is Decision.Income -> dao.insertIncome(IncomeEntity(amount = d.payment.amount, postedAtEpochSecond = d.payment.postedAt.epochSecond()))
            is Decision.NeedsReview -> dao.insertReview(
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

    private suspend fun insertFromPayment(p: ParsedPayment, type: EntryType) {
        val learned = learned()
        val category = CategoryGuesser.guess(p.label, learned).takeIf { it != Category.ETC }
            ?: CategoryGuesser.guess(p.rawText, learned)
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
            ),
        )
    }

    suspend fun resolveReview(item: ReviewItemEntity, resolution: ReviewResolution) {
        val p = item.toParsed()
        when (resolution) {
            ReviewResolution.APPLY_SPEND -> insertFromPayment(p, EntryType.SPEND)
            ReviewResolution.APPLY_REFUND -> insertFromPayment(p, EntryType.REFUND)
            ReviewResolution.APPLY_EXCEPTION -> insertFromPayment(p, EntryType.EXCEPTION)
            ReviewResolution.DISMISS -> Unit
        }
        dao.deleteReview(item)
    }

    // ── 결산 ─────────────────────────────────────────────────

    /**
     * 주간 결산. [returnIds]는 사용자가 고른 반품 물건 (null이면 자동으로 고른다).
     * 자동 결산이면 확인함을 먼저 정리하고, 정리된 지출로 초과액을 다시 계산한다.
     */
    suspend fun settle(week: WeekResult, now: LocalDateTime, returnIds: Set<Long>? = null) {
        val c = dao.latestCycleNow() ?: return
        val s = dao.settingsNow() ?: return
        val auto = Settlement.mustAutoSettle(week.week.end, now)
        if (auto) resolveLeftoverReviews(week.week.end)
        val result = weekResultNow(c, s.payDay, week.week.index) ?: week
        settleMocks(result.week.start, result.week.end, result.overspent, returnIds)
        dao.markSettled(SettledWeekEntity(c.nominal, week.week.index, now.epochSecond(), auto))
    }

    private suspend fun weekResultNow(c: CycleEntity, payDay: Int, index: Int): WeekResult? {
        val cycle = c.toPayCycle(payDay)
        val plan = planFor(c, cycle)
        val entries = dao.entriesBetweenNow(cycle.start.toEpochDay(), cycle.end.toEpochDay()).map { it.toEntry() }
        val ledger = entries.fold(CycleLedger(plan)) { l, e -> runCatching { l.add(e) }.getOrDefault(l) }
        return ledger.results().getOrNull(index)
    }

    /** 정오 자동 결산 때 확인함에 남은 그 주 알림: 중복·고정비 의심은 버리고, 환불은 환불로, 나머지는 지출로 반영. */
    private suspend fun resolveLeftoverReviews(weekEnd: LocalDate) {
        val limit = weekEnd.plusDays(1).atStartOfDay().epochSecond()
        dao.reviewItemsNow().filter { it.postedAtEpochSecond < limit }.forEach { item ->
            val resolution = when (item.reason) {
                // 짝이 되는 지출이 이미 반영돼 있다
                ReviewReason.POSSIBLE_DUPLICATE.name -> ReviewResolution.DISMISS
                // 고정비는 월급에서 이미 뺐다
                ReviewReason.LOOKS_LIKE_FIXED_EXPENSE.name -> ReviewResolution.DISMISS
                else -> if (item.action == RuleAction.REFUND.name) ReviewResolution.APPLY_REFUND else ReviewResolution.APPLY_SPEND
            }
            resolveReview(item, resolution)
        }
    }

    // ── 알림 규칙 ─────────────────────────────────────────────

    suspend fun saveRule(rule: AppRule) = dao.saveAppRule(rule.toEntity())
    suspend fun deleteRule(pkg: String) = dao.deleteAppRule(pkg)

    companion object {
        @Volatile private var instance: DonRepository? = null
        fun get(context: Context): DonRepository = instance ?: synchronized(this) {
            instance ?: DonRepository(DonDatabase.get(context).dao()).also { instance = it }
        }

        @androidx.annotation.VisibleForTesting
        fun resetForTests() = synchronized(this) {
            instance = null
            DonDatabase.resetForTests()
        }
    }
}
