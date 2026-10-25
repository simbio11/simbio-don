package com.simbio.don.app.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import com.simbio.don.app.data.SavingsSplit
import com.simbio.don.app.data.GoalMoveEntity
import com.simbio.don.app.data.GoalSource
import com.simbio.don.app.data.AccountBalance
import com.simbio.don.app.ui.kit.pressable
import com.simbio.don.app.ui.kit.popIn
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.data.GoalProgress
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.SavingsGoalEntity
import com.simbio.don.app.data.Summaries
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.IconBubble
import com.simbio.don.app.ui.kit.MoreButton
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import kotlin.random.Random

// ── 숫자가 올라가는 금액 ─────────────────────────────────────

/** 처음 나타날 때 0부터 [amount]까지 올라가는 금액 (모으는 맛) */
@Composable
fun CountUpWon(amount: Long, fontSize: TextUnit, color: Color = DonColors.Ink, prefix: String = "", durationMs: Int = 900) {
    val p = remember(amount) { Animatable(0f) }
    LaunchedEffect(amount) { p.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    val shown = if (p.value >= 1f) amount else (amount * p.value).toLong() / 10 * 10
    Text(prefix + won(shown), fontSize = fontSize, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
}

// ── 꽃가루 ─────────────────────────────────────────────────

private data class Piece(val x: Float, val y: Float, val vx: Float, val vy: Float, val spin: Float, val w: Float, val h: Float, val color: Color)

private val CONFETTI_COLORS = listOf(
    Color(0xFF346AFF), Color(0xFFFFB800), Color(0xFFF04452), Color(0xFF14A06A), Color(0xFF7B61FF), Color(0xFFFF8A3D),
)

/** 한 번 터지고 떨어지는 꽃가루. [key]가 바뀌면 다시 터진다. */
@Composable
fun Confetti(modifier: Modifier = Modifier, key: Any = Unit, count: Int = 70) {
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(2400, easing = LinearEasing)) }
    val pieces = remember(key) {
        val r = Random(42)
        List(count) {
            Piece(
                x = 0.5f + (r.nextFloat() - 0.5f) * 0.3f,
                y = 0.35f,
                vx = (r.nextFloat() - 0.5f) * 1.4f,
                vy = -0.5f - r.nextFloat() * 0.6f,
                spin = (r.nextFloat() - 0.5f) * 6f,
                w = 6f + r.nextFloat() * 6f,
                h = 3f + r.nextFloat() * 4f,
                color = CONFETTI_COLORS[it % CONFETTI_COLORS.size],
            )
        }
    }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        pieces.forEach { c ->
            val x = (c.x + c.vx * p) * size.width
            val y = (c.y + c.vy * p + 1.6f * p * p) * size.height
            val alpha = (1f - p).coerceIn(0f, 1f)
            rotate(c.spin * 360f * p, Offset(x, y)) {
                drawRect(c.color.copy(alpha = alpha), Offset(x - c.w, y - c.h), Size(c.w * 2, c.h * 2))
            }
        }
    }
}

// ── 축하 기록 (한 번만) ────────────────────────────────────

/** 모은 돈 마일스톤·목표 달성을 한 번만 축하하도록 기억한다 */
class Celebrations(context: Context) {
    private val prefs = context.getSharedPreferences("don_prefs", Context.MODE_PRIVATE)

    /** 처음엔 지금까지 넘은 마일스톤을 조용히 기록 (설치하자마자 축하하지 않게) */
    fun pendingMilestone(total: Long): Long? {
        val reached = Summaries.reachedMilestone(total) ?: return null
        if (!prefs.contains(KEY_MILESTONE)) {
            prefs.edit().putLong(KEY_MILESTONE, reached).apply()
            return null
        }
        return reached.takeIf { it > prefs.getLong(KEY_MILESTONE, 0) }
    }

    fun celebrateMilestone(m: Long) = prefs.edit().putLong(KEY_MILESTONE, m).apply()

    fun pendingGoal(goals: List<GoalProgress>): GoalProgress? {
        val done = prefs.getStringSet(KEY_GOALS, emptySet()).orEmpty()
        return goals.firstOrNull { it.done && it.goal.id.toString() !in done }
    }

    fun celebrateGoal(id: Long) {
        val done = prefs.getStringSet(KEY_GOALS, emptySet()).orEmpty()
        prefs.edit().putStringSet(KEY_GOALS, done + id.toString()).apply()
    }

    private companion object {
        const val KEY_MILESTONE = "celebrated_milestone"
        const val KEY_GOALS = "celebrated_goals"
    }
}

/** 축하 띠 (꽃가루 + 확인) */
@Composable
fun CelebrationCard(title: String, body: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxWidth().card(DonColors.GoodSurface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBubble(UiIcon.PARTY, DonColors.Good, 48.dp)
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text(body, fontSize = 13.sp, color = DonColors.InkSoft)
            SmallButton(onClick = onDismiss, containerColor = DonColors.Good) { Text("고마워요") }
        }
        Confetti(Modifier.matchParentSize(), key = title)
    }
}

// ── 연속 기록 ───────────────────────────────────────────────

@Composable
fun StreakBadge(weeks: Int, modifier: Modifier = Modifier) {
    if (weeks <= 0) return
    Row(
        modifier.clip(RoundedCornerShape(50)).background(DonColors.FireSurface).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(UiIcon.FIRE, 16.dp, Color(0xFFFF6B1A))
        Text("${weeks}주 연속 예산 지킴", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DonColors.Fire)
    }
}

// ── 모은 돈 카드 (홈) ───────────────────────────────────────

/**
 * 모은 돈을 나눠 본다: **저축**(예금·적금·투자처럼 오래 모으는 돈) · **목표**(여행처럼 쓰려고 모으는 돈) ·
 * **아낀 돈**(생활비에서 남긴 돈 — 목표에 넣을 수 있다). 저축은 목표에 저절로 들어가지 않는다.
 */
@Composable
fun SavingsCard(home: HomeState, onGoals: () -> Unit, onWealth: () -> Unit = {}) {
    val sp = home.split
    val w = home.wealth
    RealCard("모은 돈", icon = { Icon(UiIcon.SAVINGS, 18.dp, DonColors.Good) }, trailing = { MoreButton("목표", onGoals) }) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SavingsPart(
                "저축",
                if (sp.savingsFromAccounts) "예금·적금·투자 ${w.pots.size}곳" else "월급에서 먼저 뗀 돈 (어림)",
                sp.savings,
                DonColors.Good,
                Modifier.weight(1f),
                onWealth,
            )
            SavingsPart(
                "목표",
                if (home.goals.isEmpty()) "쓰려고 모으는 돈" else "목표 ${home.goals.size}개에 모은 돈",
                sp.goals,
                DonColors.Real,
                Modifier.weight(1f),
                onGoals,
            )
        }
        if (sp.savingsFromAccounts && w.gain != 0L) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("저축 넣은 돈 ${won(w.principal)}", fontSize = 12.sp, color = DonColors.InkSoft)
                Text(gainText(w.gain, w.gainBps), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = gainColor(w.gain))
            }
        }
        if (!sp.savingsFromAccounts) {
            Text("적금·예금·주식 통장을 넣으면 저축이 실제 금액으로 바뀌어요.", fontSize = 11.sp, color = DonColors.InkMuted)
        }
        // 아직 목표에 안 넣은 아낀 돈
        if (sp.unallocated > 0) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.GoodSurface)
                    .pressable(onClick = onGoals).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("아낀 돈 중 목표에 안 넣은 돈", fontSize = 12.sp, color = DonColors.InkSoft)
                    Text(won(sp.unallocated), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                }
                Text(if (home.goals.isEmpty()) "목표 만들기" else "목표에 넣기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Good)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge("이번 달 아낀 돈 +${won(home.savedThisMonth)}", DonColors.Good)
            StreakBadge(home.streak)
        }
        Divider(Modifier.padding(vertical = 6.dp))
        if (home.goals.isNotEmpty()) {
            home.goals.sortedBy { it.done }.take(2).forEach { GoalRow(it, compact = true) }
        } else {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onGoals), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(UiIcon.FLAG, DonColors.Real, 36.dp)
                Spacer(Modifier.width(10.dp))
                Text("모으는 목표를 정해 보세요 (여행·노트북…)", fontSize = 13.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                Icon(UiIcon.CHEVRON_RIGHT, 18.dp, DonColors.InkMuted)
            }
        }
    }
}

/** 모은 돈 한 칸 (저축 · 목표) */
@Composable
private fun SavingsPart(label: String, sub: String, amount: Long, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().card(DonColors.SurfaceAlt, 12.dp).pressable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        }
        CountUpWon(amount, 19.sp)
        Text(sub, fontSize = 11.sp, color = DonColors.InkMuted, maxLines = 2)
    }
}

@Composable
fun Progress(ratio: Float, color: Color, height: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(ChartColors.Track)) {
        Box(Modifier.fillMaxWidth(ratio.coerceIn(0.01f, 1f)).height(height).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

// ── 저축 목표 ───────────────────────────────────────────────

val GOAL_ICONS = listOf("FLAG", "FLIGHT", "COMPUTER", "CAR", "HOME", "DIAMOND", "PET", "CLOTHES", "SOFA", "SAVINGS")

fun goalIcon(name: String): UiIcon = UiIcon.entries.firstOrNull { it.name == name } ?: UiIcon.FLAG

@Composable
fun GoalRow(p: GoalProgress, compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(goalIcon(p.goal.icon), if (p.done) DonColors.Good else DonColors.Real, if (compact) 36.dp else 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.goal.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (p.done) "달성!" else "${(p.ratio * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (p.done) DonColors.Good else DonColors.Real)
            }
            Spacer(Modifier.height(4.dp))
            Progress(p.ratio, if (p.done) DonColors.Good else DonColors.Real, 6.dp)
            Text("${won(p.filled)} / ${won(p.goal.target)}" + if (p.linked) " · 통장 잔액" else "", fontSize = 11.sp, color = DonColors.InkMuted)
        }
    }
}

/** 목표 넣기·빼기·통장 잇기 창 */
private sealed interface GoalDialog {
    val goal: GoalProgress

    data class Put(override val goal: GoalProgress) : GoalDialog
    data class Take(override val goal: GoalProgress) : GoalDialog
    data class Link(override val goal: GoalProgress) : GoalDialog
}

/**
 * 모으는 목표: 목표마다 넣은 만큼만 찬다 (아낀 돈에서 / 따로 생긴 돈). 예금·적금·투자는 저축으로 따로 본다.
 * 따로 모으는 통장이 있으면 목표에 이어 그 잔액으로 채운다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(
    split: SavingsSplit,
    goals: List<GoalProgress>,
    usedGoals: List<SavingsGoalEntity>,
    moves: List<GoalMoveEntity>,
    accounts: List<AccountBalance>,
    onAdd: (name: String, target: Long, icon: String) -> Unit,
    onUpdate: (SavingsGoalEntity) -> Unit,
    onDelete: (SavingsGoalEntity) -> Unit,
    onPut: (goalId: Long, amount: Long, source: GoalSource) -> Unit,
    onTake: (goalId: Long, amount: Long) -> Unit,
    onLink: (goalId: Long, accountId: Long?) -> Unit,
    onUse: (goalId: Long) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(GOAL_ICONS.first()) }
    var dialog by remember { mutableStateOf<GoalDialog?>(null) }
    val names = accounts.associate { it.account.id to it.account.name }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("모으는 목표", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormCard {
                Row(Modifier.fillMaxWidth()) {
                    SplitNumber("저축", split.savings, DonColors.Good, Modifier.weight(1f))
                    SplitNumber("목표", split.goals, DonColors.Real, Modifier.weight(1f))
                    SplitNumber("안 넣은 아낀 돈", split.unallocated, DonColors.Ink, Modifier.weight(1f))
                }
                Text(
                    "예금·적금·투자는 \"저축\"으로 따로 봐요. 목표는 넣은 만큼만 차요 — 생활비에서 아낀 돈이나 보너스·용돈처럼 따로 생긴 돈을 넣어요. " +
                        "목표용 통장(세이프박스·모으기 통장 등)이 있으면 목표에 이어 그 잔액으로 채워요.",
                    fontSize = 12.sp,
                    color = DonColors.InkSoft,
                )
            }
            if (goals.isNotEmpty()) {
                FormCard("진행 중") {
                    goals.forEachIndexed { i, p ->
                        if (i > 0) Divider(Modifier.padding(vertical = 4.dp))
                        GoalRow(p)
                        val linkedName = p.goal.accountId?.let { names[it] }
                        if (p.linked && linkedName != null) {
                            Text("$linkedName 잔액으로 채워요", fontSize = 12.sp, color = DonColors.Real)
                        } else {
                            moves.lastOrNull { it.goalId == p.goal.id }?.let { m ->
                                Text(
                                    "최근 ${shortDate(java.time.LocalDate.ofEpochDay(m.epochDay))} ${if (m.amount >= 0) "+" else "−"}${won(kotlin.math.abs(m.amount))} · " +
                                        (GoalSource.entries.firstOrNull { it.name == m.source }?.label ?: ""),
                                    fontSize = 11.sp,
                                    color = DonColors.InkMuted,
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!p.linked) SmallButton(onClick = { dialog = if (dialog == GoalDialog.Put(p)) null else GoalDialog.Put(p) }) { Text("넣기") }
                            if (!p.linked && p.filled > 0) TextButton(onClick = { dialog = if (dialog == GoalDialog.Take(p)) null else GoalDialog.Take(p) }) { Text("빼기") }
                            TextButton(onClick = { if (p.goal.accountId != null) onLink(p.goal.id, null) else dialog = GoalDialog.Link(p) }) {
                                Text(if (p.goal.accountId != null) "통장 떼기" else "통장 잇기")
                            }
                            if (i > 0) TextButton(onClick = { onUpdate(p.goal.copy(sortOrder = goals[i - 1].goal.sortOrder - 1)) }) { Text("위로") }
                            if (p.filled > 0) TextButton(onClick = { onUse(p.goal.id) }, color = DonColors.Good) { Text("썼어요") }
                            TextButton(onClick = { onDelete(p.goal) }, color = DonColors.InkMuted) { Text("지우기") }
                        }
                        // 넣기·빼기는 팝업 대신 그 목표 바로 아래에 펼친다
                        when (val d = dialog) {
                            is GoalDialog.Put -> if (d.goal.goal.id == p.goal.id) PutPanel(p, split.unallocated, { amount, source -> onPut(p.goal.id, amount, source); dialog = null }) { dialog = null }
                            is GoalDialog.Take -> if (d.goal.goal.id == p.goal.id) TakePanel(p, { amount -> onTake(p.goal.id, amount); dialog = null }) { dialog = null }
                            else -> Unit
                        }
                    }
                }
            }
            FormCard("새 목표") {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("이름 (예: 제주 여행)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField("목표 금액", target, { target = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GOAL_ICONS.forEach { n ->
                        val on = n == icon
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (on) DonColors.Real else DonColors.SurfaceAlt)
                                .clickable(role = Role.RadioButton) { icon = n },
                            contentAlignment = Alignment.Center,
                        ) { Icon(goalIcon(n), 22.dp, if (on) Color.White else DonColors.InkSoft) }
                    }
                }
                Button(
                    onClick = {
                        onAdd(name.ifBlank { "목표" }, target.toLong(), icon)
                        name = ""
                        target = ""
                    },
                    enabled = (target.toLongOrNull() ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("목표 추가") }
            }
            if (usedGoals.isNotEmpty()) {
                FormCard("이룬 목표") {
                    usedGoals.forEach { g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(UiIcon.CHECK, 18.dp, DonColors.Good)
                            Spacer(Modifier.width(8.dp))
                            Text(g.name, fontSize = 14.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                            Text(won(g.target), fontSize = 14.sp, color = DonColors.InkSoft)
                        }
                    }
                }
            }
            SubButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
        }
    }
    when (val d = dialog) {
        is GoalDialog.Link -> LinkDialog(
            d.goal,
            accounts.filter { !it.account.hidden && it.type != com.simbio.don.app.data.AccountType.CARD && it.account.id !in goals.mapNotNull { g -> g.goal.accountId } },
            { id -> onLink(d.goal.goal.id, id); dialog = null },
        ) { dialog = null }
        else -> Unit
    }
}

@Composable
private fun SplitNumber(label: String, amount: Long, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = DonColors.InkMuted)
        Text(compactWon(amount) + "원", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** 목표에 넣기 (목표 아래에 펼침): 아낀 돈에서(안 넣은 만큼까지) / 따로 생긴 돈 */
@Composable
private fun PutPanel(p: GoalProgress, unallocated: Long, onPut: (Long, GoalSource) -> Unit, onClose: () -> Unit) {
    var source by remember { mutableStateOf(if (unallocated > 0) GoalSource.SAVED else GoalSource.EXTRA) }
    var amount by remember { mutableStateOf("") }
    val v = amount.toLongOrNull() ?: 0
    val over = source == GoalSource.SAVED && v > unallocated
    val left = (p.goal.target - p.filled).coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.RealSurface).padding(12.dp).popIn(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("'${p.goal.name}'에 넣기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(source == GoalSource.SAVED, { source = GoalSource.SAVED }, { Text("아낀 돈에서") }, enabled = unallocated > 0)
            FilterChip(source == GoalSource.EXTRA, { source = GoalSource.EXTRA }, { Text("따로 생긴 돈") })
        }
        Text(
            if (source == GoalSource.SAVED) "안 넣은 아낀 돈 ${won(unallocated)}" else "보너스·용돈·중고 판매처럼 생활비 밖에서 생긴 돈",
            fontSize = 12.sp,
            color = DonColors.InkMuted,
        )
        MoneyField("넣을 금액", amount, { amount = it })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(10_000L, 50_000L, 100_000L).forEach { n ->
                FilterChip(false, { amount = (v + n).toString() }, { Text("+${compactWon(n)}") })
            }
            if (source == GoalSource.SAVED && unallocated > 0) {
                FilterChip(false, { amount = (if (left > 0) minOf(unallocated, left) else unallocated).toString() }, { Text("채울 만큼") })
            }
        }
        if (over) Text("아낀 돈은 ${won(unallocated)}까지 넣을 수 있어요", fontSize = 12.sp, color = DonColors.Warn)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubButton(onClick = onClose, modifier = Modifier.weight(1f), containerColor = DonColors.Surface) { Text("닫기") }
            Button(onClick = { onPut(v, source) }, enabled = v > 0 && !over, modifier = Modifier.weight(2f)) { Text("넣기") }
        }
    }
}

/** 목표에서 빼기 (아낀 돈에서 넣었던 만큼은 아낀 돈으로 돌아간다) */
@Composable
private fun TakePanel(p: GoalProgress, onTake: (Long) -> Unit, onClose: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    val v = amount.toLongOrNull() ?: 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.WarnSurface).padding(12.dp).popIn(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("'${p.goal.name}'에서 빼기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        Text("지금 ${won(p.filled)} 모였어요. 아낀 돈에서 넣었던 만큼은 다시 아낀 돈으로 돌아가요.", fontSize = 12.sp, color = DonColors.InkSoft)
        MoneyField("뺄 금액", amount, { amount = it })
        FilterChip(false, { amount = p.filled.toString() }, { Text("전부") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubButton(onClick = onClose, modifier = Modifier.weight(1f), containerColor = DonColors.Surface) { Text("닫기") }
            Button(onClick = { onTake(v) }, enabled = v in 1..p.filled, containerColor = DonColors.Warn, modifier = Modifier.weight(2f)) { Text("빼기") }
        }
    }
}

/** 목표에 통장 잇기: 그 통장 잔액이 곧 목표에 모은 돈 (저축 합계에서는 빠진다) */
@Composable
private fun LinkDialog(p: GoalProgress, accounts: List<AccountBalance>, onLink: (Long) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("'${p.goal.name}'에 통장 잇기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("이 목표만을 위해 모으는 통장(세이프박스·모으기 통장 등)을 고르면 그 잔액으로 채워요. 그 통장은 저축 합계에서 빠져요.", fontSize = 12.sp, color = DonColors.InkMuted)
                if (accounts.isEmpty()) Text("이을 통장이 없어요. 자산 탭에서 먼저 추가해 주세요.", fontSize = 13.sp, color = DonColors.InkSoft)
                accounts.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).pressable { onLink(b.account.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(b.account.name, fontSize = 15.sp, color = DonColors.Ink, modifier = Modifier.weight(1f))
                        Text(won(b.balance), fontSize = 14.sp, color = DonColors.InkSoft)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        containerColor = DonColors.Surface,
    )
}
