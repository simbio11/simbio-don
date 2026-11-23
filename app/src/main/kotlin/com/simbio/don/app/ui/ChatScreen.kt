package com.simbio.don.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloat
import com.simbio.don.app.ui.kit.riseIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ai.Ai
import com.simbio.don.app.ai.ChatLine
import com.simbio.don.app.data.AgentAction
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton

/** 대화 시작 예시 */
private val SAMPLES = listOf(
    "이번 달 어디에 제일 많이 썼어?",
    "지난주 식비 정리해 줘",
    "어제 점심 김밥 4500원 적어 줘",
    "중복으로 적힌 거 있는지 봐 줘",
    "이번 주 남은 돈으로 하루 얼마 쓰면 돼?",
)

/**
 * AI 비서 패널: 두 손가락으로 위로 끌면 지금 화면이 살짝 위로 밀리며 아래에서 튀어나온다 (반쯤).
 * 물어보거나 글 칸을 누르면 끝까지 올라와 대화 화면이 된다. 위 손잡이를 끌어 내리거나 바깥을 누르면 닫힌다.
 * AI는 하네스 도구로 가계부 기록을 직접 찾아보고, 고칠 것은 제안만 한다 — [적용]을 눌러야 바뀐다.
 */
@Composable
fun ChatPanel(
    lines: List<ChatLine>,
    busy: Boolean,
    full: Boolean,
    onSend: (String) -> Unit,
    onAnswer: (ChatLine.Proposal, Boolean) -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    /** 글 칸을 눌렀다 (끝까지 올린다) */
    onFocusInput: () -> Unit,
    /** 손잡이를 끄는 몸짓 (위·아래로 크기 바꾸기) */
    handle: Modifier = Modifier,
) {
    val context = LocalContext.current
    val on = remember { Ai.isOn(context) }
    val label = remember { Ai.label(context) }
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(lines.size, busy) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.size + if (busy) 1 else 0) }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        // 손잡이 + 머리
        Column(handle.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(DonColors.Line))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✦ ", fontSize = 18.sp, color = DonColors.Real)
                Column(Modifier.weight(1f)) {
                    Text("AI 비서", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    if (on) Text(label, fontSize = 11.sp, color = DonColors.InkMuted, maxLines = 1)
                }
                if (lines.isNotEmpty() && !busy) TextButton(onClick = onClear) { Text("새 대화") }
                TextButton(onClick = onClose) { Text("닫기") }
            }
        }
        if (!on) {
            Column(Modifier.fillMaxWidth().padding(16.dp).background(DonColors.Surface, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI가 꺼져 있어요", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text("설정 › AI 도우미에서 Claude·Gemini·ChatGPT 또는 OpenAI 호환 서비스의 내 API 키를 넣으면 켜져요.", fontSize = 13.sp, color = DonColors.InkSoft)
                Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("설정 열기") }
            }
            return@Column
        }
        AnimatedVisibility(full || lines.isEmpty()) {
            Text(
                "가계부 기록(날짜·금액·이름·분류·통장 이름)을 도구로 찾아봐요. 알림 원문·메모·캘린더는 안 보내요. 고치기는 [적용]을 눌러야 해요.",
                fontSize = 11.sp,
                color = DonColors.InkMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lines.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("무엇이든 물어보거나 시켜 보세요", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SAMPLES.forEachIndexed { i, s -> FilterChip(false, { onSend(s) }, { Text(s, fontSize = 13.sp) }, modifier = Modifier.riseIn("sample$s", i)) }
                        }
                    }
                }
            }
            itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                Box(Modifier.animateItem().riseIn("chat$i")) { ChatRow(line, onAnswer) }
            }
            if (busy) {
                item(key = "busy") {
                    Row(Modifier.padding(8.dp).animateItem(), verticalAlignment = Alignment.CenterVertically) {
                        TypingDots()
                        Text("  가계부를 보는 중…", fontSize = 13.sp, color = DonColors.InkMuted)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(500) },
                placeholder = { Text("물어보기 · 시키기") },
                modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onFocusInput() },
                maxLines = 4,
            )
            SmallButton(
                onClick = {
                    if (text.isNotBlank() && !busy) {
                        onSend(text)
                        text = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
                enabled = text.isNotBlank() && !busy,
            ) { Text("보내기") }
        }
    }
}

/** 답을 기다리는 동안 통통 뛰는 점 세 개 */
@Composable
private fun TypingDots() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val y by t.animateFloat(
                0f, -6f,
                androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(380, delayMillis = i * 120, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(Modifier.graphicsLayer { translationY = y * density }.width(7.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(DonColors.Real))
        }
    }
}

@Composable
private fun ChatRow(line: ChatLine, onAnswer: (ChatLine.Proposal, Boolean) -> Unit) {
    when (line) {
        is ChatLine.You -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                line.text,
                fontSize = 14.sp,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(DonColors.RealFill).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        is ChatLine.Bot -> Column(
            Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(16.dp)).background(DonColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(line.text, fontSize = 14.sp, color = DonColors.Ink)
            if (line.usage.isNotBlank()) Text(line.usage, fontSize = 10.sp, color = DonColors.InkFaint)
        }
        is ChatLine.Tool -> {
            var open by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth()) {
                Text(
                    (if (line.failed) "⚠ " else "🔎 ") + line.title + if (open) "  ▲" else "  ▼",
                    fontSize = 12.sp,
                    color = if (line.failed) DonColors.Warn else DonColors.InkMuted,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(horizontal = 6.dp, vertical = 2.dp),
                )
                AnimatedVisibility(open) {
                    Text(
                        "AI에게 보낸 내용\n" + line.sent.take(3_000),
                        fontSize = 11.sp,
                        color = DonColors.InkSoft,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DonColors.SurfaceAlt).padding(8.dp)
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
        is ChatLine.Proposal -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DonColors.RealSurface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val k = when (line.action.kind) {
                AgentAction.Kind.ADD -> "적기"
                AgentAction.Kind.EDIT -> "고치기"
                AgentAction.Kind.DELETE -> "지우기"
            }
            Text("제안 · $k", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DonColors.Real)
            Text(line.action.summary, fontSize = 14.sp, color = DonColors.Ink)
            when (line.state) {
                0 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(onClick = { onAnswer(line, true) }) { Text("적용") }
                    SubButton(onClick = { onAnswer(line, false) }, containerColor = DonColors.Surface) { Text("취소") }
                }
                1 -> Text("✓ 적용했어요", fontSize = 12.sp, color = DonColors.Good)
                else -> Text("적용하지 않았어요", fontSize = 12.sp, color = DonColors.InkMuted)
            }
        }
        is ChatLine.Problem -> Text(
            line.text,
            fontSize = 13.sp,
            color = DonColors.Warn,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.Surface).padding(10.dp),
        )
    }
}
