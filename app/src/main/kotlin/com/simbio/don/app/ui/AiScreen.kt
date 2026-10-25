package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ai.Ai
import com.simbio.don.app.ai.AiReply
import com.simbio.don.app.ai.AiRequest
import com.simbio.don.app.ai.AiTasks
import com.simbio.don.app.ai.CoachReport
import com.simbio.don.app.ai.SpokenEntry
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.SplitBook
import com.simbio.don.app.data.Splits
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.popIn
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import kotlinx.coroutines.launch

/** AI 도우미가 하는 일 */
enum class AiMode(val label: String) { SPOKEN("말로 적기"), COACH("이번 달 점검"), ASK("물어보기") }

/** 한 번 보낸 결과 */
private sealed interface AiState {
    data object Idle : AiState
    data object Busy : AiState
    data class Error(val message: String) : AiState
    data class Spoken(val entry: SpokenEntry, val usage: String) : AiState
    data class Coach(val report: CoachReport, val usage: String) : AiState
    data class Answer(val text: String, val usage: String) : AiState
}

private fun usageText(r: AiReply.Ok) = "보낸 ${"%,d".format(r.inputTokens)} · 받은 ${"%,d".format(r.outputTokens)} 토큰"

/** 말로 적은 것 → 입력 화면 초안 (나눠 냈으면 내 몫·인원도) */
fun SpokenEntry.toDraft(): EntryDraft {
    val iPaid = split == "i_paid" && type == EntryType.SPEND
    return EntryDraft(
        date = date, amount = amount, label = label, type = type, category = category, memo = memo, applyToSameLabel = false,
        share = if (iPaid) (if (myShare in 1 until amount) myShare else Splits.myShare(amount, people.coerceAtLeast(2))) else null,
        people = if (iPaid) people.coerceAtLeast(2) else null,
        owe = split == "they_paid" && type == EntryType.SPEND,
    )
}

/**
 * AI 도우미: 말로 적기 · 이번 달 점검 · 물어보기.
 * 보내기 전에 보낼 내용을 그대로 보여 주고, 사용자가 누를 때만 보낸다.
 */
@Composable
fun AiScreen(
    home: HomeState,
    splits: SplitBook,
    start: AiMode,
    onWrite: (EntryDraft) -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(start) }
    var state by remember { mutableStateOf<AiState>(AiState.Idle) }
    var spoken by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var showSent by remember { mutableStateOf(false) }
    val on = remember { Ai.isOn(context) }
    val model = remember { Ai.model(context) }
    val categories = remember { Category.spending + Category.incomes }
    val summary = remember(home, splits) { AiTasks.summary(home, splits) }

    val request: AiRequest? = when (mode) {
        AiMode.SPOKEN -> spoken.takeIf { it.isNotBlank() }?.let { AiTasks.spokenRequest(it, home.today, categories) }
        AiMode.COACH -> AiTasks.coachRequest(summary)
        AiMode.ASK -> question.takeIf { it.isNotBlank() }?.let { AiTasks.askRequest(summary, it) }
    }

    fun run(req: AiRequest) {
        state = AiState.Busy
        scope.launch {
            val r = Ai.send(context, req)
            state = when (r) {
                is AiReply.Refused -> AiState.Error("AI가 답하지 않았어요: ${r.reason}")
                is AiReply.Failed -> AiState.Error(r.message)
                is AiReply.Ok -> when (mode) {
                    AiMode.SPOKEN -> AiTasks.parseSpoken(r.text, home.today, categories)?.let { AiState.Spoken(it, usageText(r)) }
                        ?: AiState.Error("금액을 못 찾았어요. 예: \"어제 점심 김밥 4500원\"")
                    AiMode.COACH -> AiTasks.parseCoach(r.text)?.let { AiState.Coach(it, usageText(r)) } ?: AiState.Error("답을 읽지 못했어요. 다시 해 주세요")
                    AiMode.ASK -> AiState.Answer(r.text.trim(), usageText(r))
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("AI 도우미", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!on) {
                FormCard {
                    Text("AI 도우미가 꺼져 있어요", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    Text(
                        "설정 › AI 도우미에 내 Anthropic API 키를 넣으면 켜져요. 키는 이 폰에만 암호화해 두고, 요금은 키 주인에게 나와요.",
                        fontSize = 13.sp,
                        color = DonColors.InkSoft,
                    )
                    Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("설정 열기") }
                }
                return@Column
            }
            SegmentedToggle(AiMode.entries.map { it.label }, mode.ordinal) {
                mode = AiMode.entries[it]
                state = AiState.Idle
                showSent = false
            }

            when (mode) {
                AiMode.SPOKEN -> FormCard {
                    Text("말하듯이 적으면 날짜·금액·분류·더치페이를 채워 드려요. 저장 전에 한 번 더 확인해요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    OutlinedTextField(
                        value = spoken,
                        onValueChange = { spoken = it.take(200) },
                        label = { Text("예: 어제 친구 셋이랑 고깃집 8만원 내가 다 냄") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("오늘 점심 김밥 4500원", "그저께 회식 2만5천 남이 냄 내 몫 보내야 함", "월급 330만원 들어옴").forEach { s ->
                            FilterChip(false, { spoken = s }, { Text(s) })
                        }
                    }
                }
                AiMode.COACH -> FormCard {
                    Text("이번 달 합계 숫자로 쓰는 속도·월말 예상·이번 주에 할 일을 짚어 드려요.", fontSize = 12.sp, color = DonColors.InkMuted)
                }
                AiMode.ASK -> FormCard {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it.take(200) },
                        label = { Text("궁금한 것") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AiTasks.SAMPLE_QUESTIONS.forEach { q -> FilterChip(false, { question = q }, { Text(q) }) }
                    }
                }
            }

            // 보낼 내용 그대로
            FormCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("보낼 내용", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                        Text(
                            if (mode == AiMode.SPOKEN) "친 문장·오늘 날짜·분류 이름만" else "합계 숫자만 · 가게 이름·메모·계좌·알림 글은 안 보내요",
                            fontSize = 11.sp,
                            color = DonColors.InkMuted,
                        )
                    }
                    TextButton(onClick = { showSent = !showSent }) { Text(if (showSent) "접기" else "보기") }
                }
                if (showSent) {
                    Text(
                        request?.user ?: "(아직 적은 게 없어요)",
                        fontSize = 12.sp,
                        color = DonColors.InkSoft,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DonColors.SurfaceAlt).padding(10.dp),
                    )
                    Text("+ 앱이 붙이는 안내문 (어떻게 답할지)", fontSize = 11.sp, color = DonColors.InkFaint)
                }
                Text("${model.label}로 보내요 · 설정에서 바꿀 수 있어요", fontSize = 11.sp, color = DonColors.InkFaint)
            }

            Button(
                onClick = { request?.let(::run) },
                enabled = request != null && state != AiState.Busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (mode) {
                        AiMode.SPOKEN -> "AI로 정리하기"
                        AiMode.COACH -> "점검 받기"
                        AiMode.ASK -> "물어보기"
                    },
                )
            }

            when (val s = state) {
                AiState.Idle -> Unit
                AiState.Busy -> Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = DonColors.Real, strokeWidth = 2.dp, modifier = Modifier.padding(end = 10.dp))
                    Text("생각하는 중…", fontSize = 13.sp, color = DonColors.InkMuted)
                }
                is AiState.Error -> FormCard { Text(s.message, fontSize = 13.sp, color = DonColors.Warn) }
                is AiState.Spoken -> SpokenCard(s.entry, s.usage) { onWrite(s.entry.toDraft()) }
                is AiState.Coach -> CoachCard(s.report, s.usage)
                is AiState.Answer -> FormCard {
                    Text(s.text, fontSize = 14.sp, color = DonColors.Ink)
                    Text(s.usage, fontSize = 11.sp, color = DonColors.InkFaint)
                }
            }
        }
    }
}

@Composable
private fun SpokenCard(e: SpokenEntry, usage: String, onWrite: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DonColors.Surface).padding(16.dp).popIn(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("이렇게 적을까요?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        val kind = when (e.type) {
            EntryType.INCOME -> "수입"
            EntryType.TRANSFER -> "이체"
            else -> "지출"
        }
        SpokenRow("날짜", shortDate(e.date))
        SpokenRow("금액", "${won(e.amount)} · $kind")
        SpokenRow("내용", e.label.ifBlank { "-" })
        if (e.type != EntryType.TRANSFER) SpokenRow("분류", e.category.label)
        val d = e.toDraft()
        when {
            d.share != null -> SpokenRow("나눠 냄", "${d.people}명 · 내 몫 ${won(d.share)} · 받을 돈 ${won(e.amount - d.share)}")
            d.owe -> SpokenRow("나눠 냄", "남이 냄 · 보낼 돈 ${won(e.amount)}")
        }
        if (e.memo.isNotBlank()) SpokenRow("메모", e.memo)
        if (e.unsure.isNotBlank()) Text("확인해 주세요: ${e.unsure}", fontSize = 12.sp, color = DonColors.Warn)
        Button(onClick = onWrite, modifier = Modifier.fillMaxWidth()) { Text("이대로 적기 (확인 후 저장)") }
        Text(usage, fontSize = 11.sp, color = DonColors.InkFaint)
    }
}

@Composable
private fun SpokenRow(label: String, value: String) {
    Row {
        Text(label, fontSize = 13.sp, color = DonColors.InkMuted, modifier = Modifier.weight(0.28f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, modifier = Modifier.weight(0.72f))
    }
}

@Composable
private fun CoachCard(r: CoachReport, usage: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DonColors.Surface).padding(16.dp).popIn(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(r.headline, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        if (r.pace.isNotBlank()) Text(r.pace, fontSize = 13.sp, color = DonColors.InkSoft)
        CoachList("잘하고 있어요", r.good, DonColors.Good)
        CoachList("조심할 점", r.watch, DonColors.Warn)
        CoachList("이번 주에 해 볼 것", r.actions, DonColors.Real)
        Text(usage, fontSize = 11.sp, color = DonColors.InkFaint)
    }
}

@Composable
private fun CoachList(title: String, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 4.dp))
    items.forEach { Text("· $it", fontSize = 13.sp, color = DonColors.Ink) }
}

/** 홈 카드: AI를 켰을 때만 */
@Composable
fun AiHomeCard(onOpen: (AiMode) -> Unit) {
    SectionCard("AI 도우미") {
        Text("말로 적거나, 이번 달을 점검받거나, 돈 고민을 물어보세요. 누를 때만 보내요.", fontSize = 12.sp, color = DonColors.InkMuted)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AiMode.entries.forEach { m ->
                SubButton(onClick = { onOpen(m) }, modifier = Modifier.weight(1f)) { Text(m.label, fontSize = 13.sp) }
            }
        }
    }
}
