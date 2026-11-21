package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ai.Ai
import com.simbio.don.app.ai.AiReply
import com.simbio.don.app.ai.AiTasks
import com.simbio.don.app.ai.CoachReport
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AskGuess
import com.simbio.don.app.data.AssistantCheck
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.Finding
import com.simbio.don.app.data.MoveRuleEntity
import com.simbio.don.app.data.NotifRuleEntity
import com.simbio.don.app.data.ReviewItemEntity
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.card
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 비서: 물어볼 알림에 답하고, 폰이 찾은 꼬인 곳을 보고, 기억한 알림을 고치는 곳.
 * "AI 비서에게 점검 받기"는 이름을 지운 건수·금액 요약만 보낸다 (알림 글·가게·사람·통장 이름은 안 보냄). 누를 때만.
 */
@Composable
fun AssistantScreen(
    asks: List<ReviewItemEntity>,
    accounts: List<AccountEntity>,
    findings: List<Finding>,
    rules: List<NotifRuleEntity>,
    moveRules: List<MoveRuleEntity>,
    askFirst: Boolean,
    monthIncome: Long,
    monthLiving: Long,
    disposable: Long,
    appName: (String) -> String,
    onAnswer: (ReviewItemEntity, AskGuess, Boolean) -> Unit,
    onAskFirst: (Boolean) -> Unit,
    onRuleAuto: (NotifRuleEntity, Boolean) -> Unit,
    onForgetRule: (Long) -> Unit,
    onFix: (Finding.Kind) -> Unit,
    onOpenEntry: (EntryEntity) -> Unit,
    onLearned: () -> Unit,
    onAiSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiOn = remember { Ai.isOn(context) }
    var showSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<CoachReport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val names = accounts.associate { it.id to it.name }
    val summary = remember(findings, asks, rules, moveRules, accounts, askFirst, monthIncome, monthLiving, disposable) {
        AssistantCheck.anonSummary(findings, asks, rules, moveRules, accounts, askFirst, monthIncome, monthLiving, disposable)
    }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("비서", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 물어보고 적기
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("애매한 알림은 물어보고 적기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                        Text(
                            "월급·입금·송금·내 통장끼리 이체는 바로 적지 않고 짐작만 보여 줘요. 맞아요 한 번이면 적고, 기억한 알림은 다음부터 묻지 않아요. 카드 결제처럼 분명한 건 그대로 적어요.",
                            fontSize = 12.sp,
                            color = DonColors.InkMuted,
                        )
                    }
                    Switch(checked = askFirst, onCheckedChange = onAskFirst)
                }
            }

            // 물어볼 것
            if (asks.isEmpty()) {
                Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
                    Text("지금 물어볼 알림이 없어요", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                }
            } else {
                AskCard(asks, accounts, onAnswer, limit = asks.size)
            }

            // 찾은 문제
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("비서가 찾은 것", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                val problems = findings.filter { it.kind != Finding.Kind.ASK && it.kind != Finding.Kind.SALARY_WAITING }
                if (problems.isEmpty()) Text("꼬인 곳이 없어요. 잔액·이체·중복을 계속 지켜볼게요.", fontSize = 12.sp, color = DonColors.Good)
                problems.forEach { f ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.SurfaceAlt).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(f.title + if (f.amount > 0) " · ${won(f.amount)}" else "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                        Text(f.detail, fontSize = 12.sp, color = DonColors.InkSoft)
                        f.entries.take(6).forEach { e ->
                            val from = e.accountId?.let(names::get)
                            val to = e.toAccountId?.let(names::get)
                            Text(
                                "· ${shortDate(LocalDate.ofEpochDay(e.epochDay))} ${e.label} ${won(e.amount)}" + listOfNotNull(from, to).joinToString(" → ").let { if (it.isBlank()) "" else " ($it)" },
                                fontSize = 12.sp,
                                color = DonColors.Real,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().clickable { onOpenEntry(e) }.padding(vertical = 2.dp),
                            )
                        }
                        if (f.entries.isEmpty()) TextButton(onClick = { onFix(f.kind) }) { Text("고치러 가기") }
                    }
                }
            }

            // 기억한 알림
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("기억한 알림 ${rules.size}개", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text("숫자를 뺀 알림 글 모양으로 기억해요. 끄면 그 답을 골라 둔 채 다시 묻고, ✕를 누르면 잊어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                if (rules.isEmpty()) Text("물어볼 알림에 \"다음에도 이렇게\"로 답하면 여기에 쌓여요.", fontSize = 12.sp, color = DonColors.InkMuted)
                rules.sortedByDescending { it.count }.forEach { r ->
                    val g = AskGuess.decode(r.decision)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(r.sample.ifBlank { r.pattern }, fontSize = 13.sp, color = DonColors.Ink, maxLines = 1)
                            Text(
                                "${appName(r.packageName)} · " + (g?.let { askSentence(it, names).removeSuffix(" 같아요").replace(" 같아요 — 맞으면 새 달을 시작해요", "") } ?: "-") + (if (r.byAmount) " · ${won(r.amount)} 안팎일 때" else "") + " · ${r.count}번",
                                fontSize = 11.sp,
                                color = DonColors.InkSoft,
                                maxLines = 2,
                            )
                        }
                        Switch(checked = r.auto, onCheckedChange = { onRuleAuto(r, it) })
                        Text("✕", fontSize = 14.sp, color = DonColors.InkMuted, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onForgetRule(r.id) }.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
                TextButton(onClick = onLearned) { Text("배운 가게·통장·이체 보기") }
            }

            // AI 비서
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI 비서에게 점검 받기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text(
                    "위에서 찾은 문제의 건수·금액과 기억한 규칙 수만 보내서, 왜 자꾸 꼬이는지와 앱에서 무엇을 누르면 되는지 물어봐요. " +
                        "알림 글·가게·사람·통장 이름은 보내지 않아요 (통장은 \"통장1\"처럼 번호로).",
                    fontSize = 12.sp,
                    color = DonColors.InkMuted,
                )
                if (!aiOn) {
                    TextButton(onClick = onAiSettings) { Text("설정 › AI 도우미에서 내 API 키 넣기") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("보낼 내용", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showSent = !showSent }) { Text(if (showSent) "접기" else "보기") }
                    }
                    if (showSent) {
                        Text(
                            summary,
                            fontSize = 11.sp,
                            color = DonColors.InkSoft,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DonColors.SurfaceAlt).padding(10.dp),
                        )
                    }
                    Button(
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                when (val r = Ai.send(context, AiTasks.assistantRequest(summary))) {
                                    is AiReply.Ok -> AiTasks.parseAssistant(r.text)?.let { report = it } ?: run { error = "답을 읽지 못했어요. 다시 해 주세요" }
                                    is AiReply.Refused -> error = "AI가 답하지 않았어요: ${r.reason}"
                                    is AiReply.Failed -> error = r.message
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("점검 받기") }
                    if (busy) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = DonColors.Real, strokeWidth = 2.dp, modifier = Modifier.padding(end = 10.dp))
                            Text("생각하는 중…", fontSize = 13.sp, color = DonColors.InkMuted)
                        }
                    }
                    error?.let { Text(it, fontSize = 13.sp, color = DonColors.Warn) }
                }
            }
            report?.let { CoachCard(it, "", watchTitle = "자꾸 꼬이는 까닭", actionTitle = "앱에서 할 일") }
            Box(Modifier.height(24.dp))
        }
    }
}
