package com.simbio.don.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.GameState
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.PixelImage
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.kit.pressable
import com.simbio.don.app.ui.kit.icon16
import com.simbio.don.app.ui.kit.popIn
import com.simbio.don.game.Cards
import com.simbio.don.game.Catalog
import com.simbio.don.game.MemoryGame
import com.simbio.don.game.MemoryState
import com.simbio.don.game.MiniGame
import com.simbio.don.game.PriceQuiz
import kotlinx.coroutines.delay
import kotlin.random.Random

private sealed interface GamePage {
    data object Menu : GamePage
    data class Play(val game: MiniGame, val seed: Long) : GamePage
    data class Result(val game: MiniGame, val score: Int, val reward: Long) : GamePage
}

/**
 * 미니게임: 얼마일까?(값 맞히기) · 짝 맞추기. 마일은 이번 주 결산 마일의 10%까지만 (실제로 아낀 만큼만 쌓이게).
 * [onPlay]는 한 판을 기록하고 실제로 받은 마일을 돌려준다.
 */
@Composable
fun MiniGameScreen(
    state: GameState,
    salary: Long,
    start: MiniGame?,
    onPlay: (game: MiniGame, score: Int, reward: Long, granted: (Long) -> Unit) -> Unit,
    onBack: () -> Unit,
    seed: () -> Long = { Random.nextLong() },
) {
    var page by remember { mutableStateOf<GamePage>(start?.let { GamePage.Play(it, seed()) } ?: GamePage.Menu) }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        val title = when (val p = page) {
            GamePage.Menu -> "미니게임"
            is GamePage.Play -> p.game.label
            is GamePage.Result -> p.game.label
        }
        BackBar(title, { if (page == GamePage.Menu) onBack() else page = GamePage.Menu }, trailing = {
            Badge("남은 ${miles(state.left)}", DonColors.GameDeep, modifier = Modifier.padding(end = 16.dp))
        })
        ScreenBackHandler(page != GamePage.Menu) { page = GamePage.Menu }
        androidx.compose.animation.AnimatedContent(
            page,
            Modifier.weight(1f),
            transitionSpec = { stepTransition(targetState !is GamePage.Menu) },
            contentKey = { if (it is GamePage.Play) "play${it.seed}" else it::class },
            label = "game",
        ) { p ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (p) {
                GamePage.Menu -> GameMenu(state) { page = GamePage.Play(it, seed()) }
                is GamePage.Play -> when (p.game) {
                    MiniGame.QUIZ -> QuizGame(p.seed, salary) { right -> page = GamePage.Result(p.game, right, PriceQuiz.reward(right)) }
                    MiniGame.MEMORY -> MemoryGameView(p.seed) { moves -> page = GamePage.Result(p.game, moves, MemoryGame.reward(moves)) }
                }
                is GamePage.Result -> GameResult(p, state, onPlay, again = { page = GamePage.Play(p.game, seed()) }, menu = { page = GamePage.Menu })
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

@Composable
private fun GameMenu(state: GameState, onStart: (MiniGame) -> Unit) {
    GameCard("이번 주 미니게임 마일") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(miles(state.left), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text(" 남음 / ${miles(state.cap)}", fontSize = 13.sp, color = DonColors.InkSoft, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (state.cap > 0) ProgressLine(state.earned.toFloat() / state.cap)
        Text(
            if (state.cap > 0) "이번 주 결산으로 받은 ${miles(state.settled)}의 10%까지 받을 수 있어요. 다 받아도 계속 놀 수 있어요."
            else "미니게임 마일은 이번 주 결산 마일의 10%까지예요. 지난주 결산 전이거나 예산을 넘긴 주는 마일 없이 놀아요.",
            fontSize = 12.sp,
            color = DonColors.InkSoft,
        )
    }
    MiniGame.entries.forEach { g ->
        val best = state.best(g)
        Row(
            Modifier.fillMaxWidth().card().pressable { onStart(g) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(DonColors.GameSurface), contentAlignment = Alignment.Center) {
                PixelImage(if (g == MiniGame.QUIZ) com.simbio.don.app.R.drawable.px16_mile else com.simbio.don.app.R.drawable.px16_p_t5, 40.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text(
                    if (g == MiniGame.QUIZ) "몰 물건 값을 맞혀요 · 한 문제 ${PriceQuiz.MILES_PER_RIGHT}마일" else "같은 그림 두 장 찾기 · 적게 뒤집을수록 많이 (최대 50마일)",
                    fontSize = 12.sp,
                    color = DonColors.InkSoft,
                )
                if (best != null) {
                    Text(if (g == MiniGame.QUIZ) "최고 ${best}/${PriceQuiz.ROUNDS}" else "최고 ${best}번 만에", fontSize = 12.sp, color = DonColors.GameDeep, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("하기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Game)
        }
    }
}

// ── 얼마일까? ──────────────────────────────────────────────

@Composable
private fun QuizGame(seed: Long, salary: Long, onDone: (right: Int) -> Unit) {
    val qs = remember(seed) { PriceQuiz.questions(seed) }
    var i by remember(seed) { mutableIntStateOf(0) }
    var right by remember(seed) { mutableIntStateOf(0) }
    var picked by remember(seed) { mutableStateOf<Long?>(null) }
    val q = qs[i]
    Text("${i + 1} / ${qs.size} · 맞힌 문제 $right", fontSize = 13.sp, color = DonColors.InkSoft)
    ProgressLine((i + if (picked != null) 1 else 0).toFloat() / qs.size)
    Column(
        Modifier.fillMaxWidth().card().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(20.dp)).background(DonColors.GameSurface), contentAlignment = Alignment.Center) {
            PixelImage(q.product.icon16(), 96.dp)
        }
        Text(q.product.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        Text("이 물건은 얼마일까요?", fontSize = 14.sp, color = DonColors.InkSoft)
    }
    q.choices.forEach { c ->
        val done = picked != null
        val (fill, line, ink) = when {
            !done -> Triple(DonColors.Surface, DonColors.Line, DonColors.Ink)
            c == q.answer -> Triple(DonColors.GoodSurface, DonColors.Good, DonColors.Good)
            c == picked -> Triple(DonColors.WarnSurface, DonColors.Warn, DonColors.Warn)
            else -> Triple(DonColors.Surface, DonColors.Line, DonColors.InkFaint)
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(fill).border(2.dp, line, RoundedCornerShape(14.dp))
                .pressable(enabled = !done) {
                    picked = c
                    if (c == q.answer) right++
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(won(c), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink) }
    }
    picked?.let { p ->
        Column(Modifier.fillMaxWidth().popIn(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (p == q.answer) "정답이에요!" else "아쉬워요! 정답은 ${won(q.answer)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (p == q.answer) DonColors.Good else DonColors.Warn)
            val share = Cards.salaryShareBps(q.answer, salary)
            val hours = Cards.workHours(q.answer, salary)
            if (share != null && hours != null) {
                Text(
                    "실제로 산다면 월급의 ${"%.1f".format(share / 100.0)}% · ${if (hours < 1) "${(hours * 60).toInt()}분" else "${hours}시간"} 일한 돈",
                    fontSize = 13.sp,
                    color = DonColors.InkSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Button(
            onClick = {
                if (i + 1 < qs.size) {
                    i++
                    picked = null
                } else {
                    onDone(right)
                }
            },
            containerColor = DonColors.Game,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (i + 1 < qs.size) "다음 문제" else "결과 보기") }
    }
}

// ── 짝 맞추기 ──────────────────────────────────────────────

@Composable
private fun MemoryGameView(seed: Long, onDone: (moves: Int) -> Unit) {
    var s by remember(seed) { mutableStateOf(MemoryState(MemoryGame.deal(seed))) }
    LaunchedEffect(s) {
        if (s.mismatch) {
            delay(800)
            s = s.hide()
        } else if (s.done) {
            delay(600)
            onDone(s.moves)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("뒤집은 횟수 ${s.moves}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, modifier = Modifier.weight(1f))
        Text("맞춘 짝 ${s.matched.size / 2}/${MemoryGame.PAIRS}", fontSize = 14.sp, color = DonColors.GameDeep, fontWeight = FontWeight.SemiBold)
    }
    s.cards.indices.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { i ->
                MemoryCard(s.cards[i], s.isFaceUp(i), i in s.matched, Modifier.weight(1f)) { s = s.flip(i) }
            }
        }
    }
    Text("같은 그림 두 장을 찾아요. 적게 뒤집을수록 마일이 많아요.", fontSize = 12.sp, color = DonColors.InkMuted)
}

@Composable
private fun MemoryCard(productId: String, faceUp: Boolean, matched: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val angle by animateFloatAsState(if (faceUp) 180f else 0f, tween(250), label = "flip")
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(12.dp)
    val product = remember(productId) { Catalog.byId(productId) }
    Box(
        modifier.aspectRatio(0.8f)
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 12 * density
            }
            .clip(shape)
            .background(if (angle > 90f) (if (matched) DonColors.GoodSurface else DonColors.Surface) else DonColors.Game)
            .border(2.dp, if (angle > 90f && matched) DonColors.Good else Color.Transparent, shape)
            .clickable(enabled = !faceUp, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (faceUp) "카드 ${product?.name}" else "뒤집힌 카드" },
        contentAlignment = Alignment.Center,
    ) {
        if (angle > 90f) {
            Box(Modifier.graphicsLayer { rotationY = 180f }) { product?.let { PixelImage(it.icon16(), 48.dp) } }
        } else {
            PixelImage(com.simbio.don.app.R.drawable.px16_mile, 32.dp, silhouette = Color(0x55FFFFFF))
        }
    }
}

// ── 결과 ───────────────────────────────────────────────────

@Composable
private fun GameResult(
    r: GamePage.Result,
    state: GameState,
    onPlay: (MiniGame, Int, Long, (Long) -> Unit) -> Unit,
    again: () -> Unit,
    menu: () -> Unit,
) {
    var granted by remember(r) { mutableStateOf<Long?>(null) }
    LaunchedEffect(r) { onPlay(r.game, r.score, r.reward) { granted = it } }
    Column(
        Modifier.fillMaxWidth().card().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val stars = if (r.game == MiniGame.QUIZ) (r.score + 1) / 2 else MemoryGame.stars(r.score)
        Row(Modifier.popIn(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { k -> com.simbio.don.app.ui.kit.Icon(com.simbio.don.app.ui.kit.UiIcon.STAR, 36.dp, if (k < stars) DonColors.Highlight else DonColors.Line) }
        }
        Text(
            if (r.game == MiniGame.QUIZ) "${PriceQuiz.ROUNDS}문제 중 ${r.score}개 맞혔어요" else "${r.score}번 만에 다 맞췄어요",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DonColors.Ink,
        )
        when (val g = granted) {
            null -> Text("기록하는 중…", fontSize = 14.sp, color = DonColors.InkMuted)
            else -> if (g > 0) {
                Text("+${miles(g)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DonColors.GameDeep)
            } else {
                Text(
                    when {
                        r.reward <= 0 -> "이번엔 마일이 없어요. 다음엔 더 맞혀 봐요!"
                        state.cap <= 0 -> "이번 주는 마일 없이 놀았어요 (결산 마일의 10%까지)"
                        else -> "이번 주 미니게임 마일을 다 받았어요"
                    },
                    fontSize = 14.sp,
                    color = DonColors.InkSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    Button(onClick = again, containerColor = DonColors.Game, modifier = Modifier.fillMaxWidth()) { Text("한 판 더") }
    SubButton(onClick = menu, modifier = Modifier.fillMaxWidth()) { Text("다른 게임") }
}
