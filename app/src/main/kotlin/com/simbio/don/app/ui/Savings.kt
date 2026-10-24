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
        modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFF1E6)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(UiIcon.FIRE, 16.dp, Color(0xFFFF6B1A))
        Text("${weeks}주 연속 예산 지킴", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE8590C))
    }
}

// ── 모은 돈 카드 (홈) ───────────────────────────────────────

/**
 * 지금까지 모은 실제 돈 + 다음 마일스톤 + 첫 목표.
 * 모으는 통장(저축·투자)을 넣었으면 그 금액(넣은 돈 + 불어난 돈)이 "모은 돈"이고, 가계부로 아낀 돈은 아래 한 줄로.
 */
@Composable
fun SavingsCard(home: HomeState, onGoals: () -> Unit, onWealth: () -> Unit = {}) {
    val w = home.wealth
    val headline = if (w.pots.isNotEmpty()) w.total else home.savedTotal
    RealCard("모은 돈", icon = { Icon(UiIcon.SAVINGS, 18.dp, DonColors.Good) }, trailing = { MoreButton("목표", onGoals) }) {
        CountUpWon(headline, 30.sp)
        if (w.pots.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("넣은 돈 ${won(w.principal)}", fontSize = 13.sp, color = DonColors.InkSoft)
                if (w.gain != 0L) Text(gainText(w.gain, w.gainBps), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = gainColor(w.gain))
            }
            if (w.history.size >= 2) WealthChart(w.history, Modifier.fillMaxWidth().height(64.dp).padding(vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onWealth).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("저축·투자 ${w.pots.size}곳 · 가계부로 아낀 돈 ${won(home.savedTotal)}", fontSize = 12.sp, color = DonColors.InkMuted, modifier = Modifier.weight(1f))
                Icon(UiIcon.CHEVRON_RIGHT, 16.dp, DonColors.InkMuted)
            }
        } else {
            Text("먼저 떼어둔 저축과 아낀 생활비를 합친 진짜 돈이에요.", fontSize = 12.sp, color = DonColors.InkMuted)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onWealth).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("적금·주식처럼 따로 모으는 돈도 넣어 보세요", fontSize = 12.sp, color = DonColors.Real, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(UiIcon.CHEVRON_RIGHT, 16.dp, DonColors.Real)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge("이번 달 아낀 돈 +${won(home.savedThisMonth)}", DonColors.Good)
            StreakBadge(home.streak)
        }
        Summaries.nextMilestone(headline)?.let { next ->
            val prev = Summaries.reachedMilestone(headline) ?: 0
            Spacer(Modifier.height(4.dp))
            Text("${compactWon(next)}원까지 ${won(next - headline)} 남았어요", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
            Progress((headline - prev).toFloat() / (next - prev), DonColors.Good)
        }
        val goal = home.goals.firstOrNull { !it.done } ?: home.goals.firstOrNull()
        if (goal != null) {
            Divider(Modifier.padding(vertical = 6.dp))
            GoalRow(goal, compact = true)
        } else {
            Divider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onGoals), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(UiIcon.FLAG, DonColors.Real, 36.dp)
                Spacer(Modifier.width(10.dp))
                Text("모으는 목표를 정해 보세요 (여행·노트북…)", fontSize = 13.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                Icon(UiIcon.CHEVRON_RIGHT, 18.dp, DonColors.InkMuted)
            }
        }
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
            Text("${won(p.filled)} / ${won(p.goal.target)}", fontSize = 11.sp, color = DonColors.InkMuted)
        }
    }
}

/** 목표 목록·추가. 모은 돈은 위에서부터 순서대로 채운다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(
    savedTotal: Long,
    goals: List<GoalProgress>,
    usedGoals: List<SavingsGoalEntity>,
    onAdd: (name: String, target: Long, icon: String) -> Unit,
    onUpdate: (SavingsGoalEntity) -> Unit,
    onDelete: (SavingsGoalEntity) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(GOAL_ICONS.first()) }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("모으는 목표", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormCard {
                Text("모은 돈", fontSize = 13.sp, color = DonColors.InkMuted)
                CountUpWon(savedTotal, 28.sp)
                Text("모은 돈을 위 목표부터 차례로 채워요. 목표를 이루고 실제로 돈을 쓰면 '썼어요'를 눌러 주세요.", fontSize = 12.sp, color = DonColors.InkSoft)
            }
            if (goals.isNotEmpty()) {
                FormCard("진행 중") {
                    goals.forEachIndexed { i, p ->
                        if (i > 0) Divider(Modifier.padding(vertical = 4.dp))
                        GoalRow(p)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (i > 0) TextButton(onClick = { onUpdate(p.goal.copy(sortOrder = goals[i - 1].goal.sortOrder - 1)) }) { Text("위로") }
                            if (p.done) TextButton(onClick = { onUpdate(p.goal.copy(used = true)) }, color = DonColors.Good) { Text("썼어요") }
                            TextButton(onClick = { onDelete(p.goal) }, color = DonColors.InkMuted) { Text("지우기") }
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
}
