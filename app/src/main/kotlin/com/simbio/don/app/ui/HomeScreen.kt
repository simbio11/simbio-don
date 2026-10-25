package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.toEntry
import com.simbio.don.app.ui.kit.CharacterSprite
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.ui.kit.IconBubble
import com.simbio.don.app.ui.kit.MoreButton
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.WarnLine
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.Stats

@Composable
fun HomeScreen(
    home: HomeState,
    listenerEnabled: Boolean,
    onOpenListenerSettings: () -> Unit,
    onReview: () -> Unit,
    onSettle: () -> Unit,
    onSalary: () -> Unit,
    onDismissSalary: () -> Unit = {},
    onSettings: () -> Unit = {},
    onOpenEntry: (EntryEntity) -> Unit,
    onAllEntries: () -> Unit,
    onStats: () -> Unit,
    onHouse: () -> Unit,
    onShop: () -> Unit,
    onBudgets: () -> Unit = {},
    onGoals: () -> Unit = {},
    weather: com.simbio.don.app.weather.Weather? = null,
    onWealth: () -> Unit = {},
    onAssetCheck: () -> Unit = {},
    onCalendarDay: (java.time.LocalDate) -> Unit = {},
    onAddFixed: (com.simbio.don.budget.Subscription) -> Unit = {},
    onPlans: () -> Unit = {},
    onCustomize: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val celebrations = remember { Celebrations(context) }
    var dismissed by remember { mutableStateOf(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AppName(24.sp)
                Text(shortDate(home.today), fontSize = 13.sp, color = DonColors.InkMuted)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(DonColors.GameSurface), contentAlignment = Alignment.Center) {
                CharacterSprite(home.mood, 34.dp)
            }
            Spacer(Modifier.width(4.dp))
            RoundIconButton(UiIcon.GEAR, "설정", true, onSettings)
        }

        if (!listenerEnabled) {
            Banner("결제 알림 자동 기록이 꺼져 있어요", "알림 접근을 허용하면 결제할 때마다 자동으로 기록돼요.", "켜러 가기", onOpenListenerSettings)
        }
        home.salaryNotice?.let { n -> SalaryNoticeCard(n, home.plan.disposable, onSalary, onDismissSalary) }
        if (home.assetCheckDue && home.salaryNotice == null) {
            Banner(
                "월급날 자산 점검",
                "저축 통장에 넣은 돈과 지금 금액을 적고 얼마나 불었는지 봐요.",
                "점검하기",
                onAssetCheck,
                UiIcon.SAVINGS,
                DonColors.Good,
            )
        }
        if (home.settleable.isNotEmpty()) {
            Banner(
                "지난주 결산이 준비됐어요",
                home.settleable.first().result.let { "${shortDate(it.week.start)} ~ ${shortDate(it.week.end)}" },
                "결산 보기",
                onSettle,
                UiIcon.TROPHY,
                DonColors.Game,
            )
        } else if (home.waitingForInbox) {
            Banner("확인함을 정리하면 결산해요", "오늘 정오가 지나면 자동으로 결산해요.", "확인함", onReview, UiIcon.BELL, DonColors.Real)
        }
        if (home.readyWishes.isNotEmpty()) {
            val w = home.readyWishes.first()
            Banner(
                "찜한 물건, 아직도 사고 싶어요?",
                "${w.name} · ${won(w.price)}" + if (home.readyWishes.size > 1) " 외 ${home.readyWishes.size - 1}개" else "",
                "보러 가기",
                onHouse,
                UiIcon.HEART_ON,
                DonColors.Warn,
            )
        }
        if (home.reviewCount > 0 && !home.waitingForInbox) {
            SubButton(onClick = onReview, modifier = Modifier.fillMaxWidth(), containerColor = DonColors.Surface) {
                Text("확인할 결제 알림 ${home.reviewCount}건")
            }
        }

        HomeCards.layout.shown.forEach { card ->
            when (card) {
                HomeCard.WEEK -> {
                val week = home.currentWeek
                RealCard("실제 내 돈 · 이번 주") {
                    if (week != null) {
                        Text("이번 주 남은 돈", fontSize = 13.sp, color = DonColors.InkMuted)
                        Text(
                            won(week.available),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (week.available >= 0) DonColors.Ink else DonColors.Warn,
                        )
                        Text(
                            if (week.available > 0) "오늘은 ${won(home.todayAllowance)}까지 쓰면 딱 맞아요" else "이번 주 예산을 다 썼어요",
                            fontSize = 14.sp,
                            color = if (week.available > 0) DonColors.Real else DonColors.Warn,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        Meter(week.spent, week.budget)
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                            Text("쓴 돈 ${won(week.spent)}", fontSize = 12.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                            Text("기본 예산 ${won(week.budget)}", fontSize = 12.sp, color = DonColors.InkMuted)
                        }
                        if (week.overspent > 0) WarnLine("기본 예산보다 ${won(week.overspent)} 더 썼어요", fontSize = 12)
                        if (week.carryIn != 0L) Text("지난주에서 넘어온 돈 ${won(week.carryIn)} 포함", fontSize = 12.sp, color = DonColors.InkMuted)
                        Text("${shortDate(week.week.start)} ~ ${shortDate(week.week.end)} · ${week.week.days}일", fontSize = 12.sp, color = DonColors.InkMuted)
                    } else {
                        Text("이번 주기 밖의 날짜예요. 월급을 확인해 주세요.")
                    }
                }
                }
                HomeCard.FORECAST -> home.insights.forecast?.let { ForecastCard(it, home.cycle.end) }
                HomeCard.CALENDAR -> HomeCalendarCard(home, onCalendarDay)
                HomeCard.UPCOMING -> UpcomingCard(home.insights.upcoming, home.today, onPlans)
                HomeCard.TILES -> {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("이번 달 지출", won(home.monthSpent), "고정 지출 포함", Modifier.weight(1f))
                    StatTile("이번 달 수입", won(home.monthIncome), null, Modifier.weight(1f))
                    StatTile("아낀 돈", won(home.savedSoFar), "지난 주들 합계", Modifier.weight(1f))
                }
                }
                HomeCard.SAVED -> {
                // 모은 돈이 마일스톤을 넘었거나 목표를 이뤘으면 한 번 축하
                key(dismissed) {
                    val goal = celebrations.pendingGoal(home.goals)
                    val milestone = if (goal == null) celebrations.pendingMilestone(home.savedTotal) else null
                    when {
                        goal != null -> CelebrationCard("목표 달성!", "'${goal.goal.name}' ${won(goal.goal.target)}을 모았어요.") {
                            celebrations.celebrateGoal(goal.goal.id)
                            dismissed++
                        }
                        milestone != null -> CelebrationCard("모은 돈 ${compactWon(milestone)}원 돌파!", "아낀 돈이 진짜로 쌓이고 있어요. 지금 ${won(home.savedTotal)}.") {
                            celebrations.celebrateMilestone(milestone)
                            dismissed++
                        }
                    }
                }
                SavingsCard(home, onGoals, onWealth)
                }
                HomeCard.ROOM -> HomeGameCard(home, onHouse, onShop, weather)
                HomeCard.INSIGHTS -> InsightCard(home.insights.insights)
                HomeCard.SUBSCRIPTIONS -> SubscriptionCard(home.insights.subscriptions, onAddFixed)
                HomeCard.CATEGORIES -> {
                // 홈은 생활비에 집중: 고정 지출은 빼고 본다 (가계부·통계 탭에는 들어감)
                val categories = Stats.byCategory(home.entries.map { it.toEntry() }.filter { it.type != com.simbio.don.budget.EntryType.FIXED })
                // 카테고리 예산: 80% 넘게 쓴 것만 미리 알려준다
                val spentBy = categories.associate { it.category to it.amount }
                val tight = home.categoryBudgets.filter { (c, b) -> b > 0 && (spentBy[c] ?: 0) * 10 >= b * 8 }
                if (tight.isNotEmpty()) {
                    SectionCard("카테고리 예산 주의", trailing = { MoreButton("예산", onBudgets) }) {
                        BudgetBars(tight, spentBy, limit = 3)
                    }
                }
                if (categories.isNotEmpty()) {
                    SectionCard("이번 달 많이 쓴 카테고리", trailing = { MoreButton("통계", onStats) }) {
                        CategoryBars(categories, limit = 3)
                    }
                }
                }
                HomeCard.RECENT -> {
                SectionCard("최근 내역", trailing = if (home.entries.size > 5) ({ MoreButton("전체 ${home.entries.size}건", onAllEntries) }) else null) {
                    if (home.entries.isEmpty()) {
                        Text("아직 기록이 없어요. 결제하면 알림으로 자동 기록되고, + 버튼으로 직접 넣을 수도 있어요.", fontSize = 13.sp, color = DonColors.InkSoft)
                    }
                    val names = home.accounts.associate { it.account.id to it.account.name }
                    home.entries.sortedWith(compareByDescending<EntryEntity> { it.epochDay }.thenByDescending { it.minuteOfDay ?: -1 }.thenByDescending { it.id })
                        .take(5).forEach { LedgerRow(it, names, onOpenEntry) }
                }
                }
            }
        }
        com.simbio.don.app.ui.kit.TextButton(onClick = onCustomize, modifier = Modifier.align(Alignment.CenterHorizontally), color = DonColors.InkMuted) {
            com.simbio.don.app.ui.kit.Icon(UiIcon.EDIT, 16.dp, DonColors.InkMuted)
            Spacer(Modifier.width(4.dp))
            Text("홈 꾸미기 (카드 숨기기·차례 바꾸기)", fontSize = 13.sp)
        }
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
fun Line(label: String, value: String, bold: Boolean = false) = com.simbio.don.app.ui.kit.KeyValue(label, value, bold)

/** 알림 띠: 동그란 아이콘 + 제목·설명 + 작은 버튼 */
@Composable
fun Banner(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    icon: UiIcon = UiIcon.WARN,
    tint: Color = DonColors.Warn,
) {
    Row(
        Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DonColors.Ink)
            Text(body, fontSize = 12.sp, color = DonColors.InkMuted)
        }
        Spacer(Modifier.width(8.dp))
        SmallButton(onClick = onAction, containerColor = tint) { Text(action) }
    }
}

/** 새 달이 저절로 시작됐다는 안내 */
@Composable
fun SalaryNoticeCard(n: com.simbio.don.app.data.SalaryNotice, disposable: Long, onFix: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().card(DonColors.RealSurface).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(UiIcon.PAYMENTS, DonColors.Real)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (n.source == com.simbio.don.app.data.SalarySource.NOTIFICATION) "월급 ${won(n.salary)} 들어왔어요" else "새 달이 시작됐어요",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = DonColors.Ink,
                )
                Text(
                    if (n.source == com.simbio.don.app.data.SalarySource.NOTIFICATION) {
                        val diff = n.previous?.let { n.salary - it }?.takeIf { it != 0L }
                        "통장에 들어온 금액으로 이번 달 월급을 바꿨어요" +
                            (diff?.let { " (지난달보다 ${if (it > 0) "+" else "−"}${won(kotlin.math.abs(it))})" } ?: "") +
                            ". ${shortDate(n.start)}부터 새 달, 생활비는 ${won(disposable)}이에요."
                    } else {
                        "${shortDate(n.start)}부터 새 달이에요. 월급 입금을 아직 못 봐서 지난달 월급 ${won(n.salary)}으로 짰어요. 입금 알림이 오면 저절로 맞춰요."
                    },
                    fontSize = 12.sp,
                    color = DonColors.InkSoft,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SubButton(onClick = onFix, modifier = Modifier.weight(1f), containerColor = DonColors.Surface) { Text("월급 고치기") }
            SmallButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("확인") }
        }
    }
}

/** 카드 안 구분선 (목록 사이) */
@Composable
fun ListDivider() = Divider(Modifier.padding(vertical = 2.dp))
