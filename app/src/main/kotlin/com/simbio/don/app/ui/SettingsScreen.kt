package com.simbio.don.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.AppRuleEntity
import com.simbio.don.app.data.FixedExpenseEntity
import com.simbio.don.app.data.SeenAppEntity
import com.simbio.don.app.data.SettingsEntity
import com.simbio.don.app.data.toRule
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.DefaultApps
import com.simbio.don.notify.KeywordRule
import com.simbio.don.notify.KeywordRuleCodec
import com.simbio.don.notify.RuleAction
import java.time.DayOfWeek

@Composable
fun SettingsScreen(
    settings: SettingsEntity,
    fixed: List<FixedExpenseEntity>,
    rules: List<AppRuleEntity>,
    seenApps: List<SeenAppEntity>,
    listenerEnabled: Boolean,
    appLabel: (String) -> String?,
    onOpenListenerSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSaveBudget: (List<FixedExpense>, Long, DayOfWeek, Boolean) -> Unit,
    onEditRule: (String) -> Unit,
    onSaveRule: (AppRule) -> Unit,
    learnedCount: Int,
    onResetLearned: () -> Unit,
    onExport: () -> Unit,
) {
    val rows = remember(fixed) { mutableStateListOf<FixedRow>().apply { addAll(fixed.map { FixedRow(it.name, it.amount.toString()) }); if (isEmpty()) add(FixedRow()) } }
    var savings by remember(settings) { mutableStateOf(settings.savings.toString()) }
    var settlementDay by remember(settings) { mutableStateOf(DayOfWeek.of(settings.pendingSettlementDay ?: settings.settlementDay)) }
    var applyNow by remember { mutableStateOf(true) }
    var messagingWarning by remember { mutableStateOf<AppRule?>(null) }
    var showHidden by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("설정", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

        RealCard("결제 알림 자동 기록") {
            Text(if (listenerEnabled) "켜져 있어요" else "꺼져 있어요", color = if (listenerEnabled) DonColors.Good else DonColors.Warn)
            Text(
                "알림 접근 권한은 모든 알림을 볼 수 있는 권한이에요. 이 앱은 아래에서 고른 앱의 알림만 열어보고, 기기 밖으로 보내지 않아요.",
                fontSize = 12.sp,
            )
            OutlinedButton(onClick = onOpenListenerSettings) { Text("알림 접근 설정 열기") }
            Text("갤럭시는 배터리 최적화 때문에 알림 수신이 끊길 수 있어요. '제한 없음'으로 바꿔 주세요.", fontSize = 12.sp)
            OutlinedButton(onClick = onOpenBatterySettings) { Text("배터리 설정 열기") }
        }

        RealCard("알림 읽을 앱") {
            Text("켜진 앱의 알림만 읽어요. 앱을 누르면 키워드 규칙을 바꿀 수 있어요.", fontSize = 12.sp)
            // 이 폰에 없는 기본 앱은 접어 둔다
            val presetPackages = DefaultApps.presets.map { it.packageName }.toSet()
            val (shown, hidden) = rules.partition { appLabel(it.packageName) != null || it.packageName !in presetPackages }
            if (shown.isEmpty()) Text("이 폰에서 찾은 결제 앱이 없어요. 아래 '최근 알림을 보낸 앱'에서 골라 주세요.", fontSize = 13.sp)
            (if (showHidden) rules else shown).forEach { r ->
                val installed = appLabel(r.packageName) != null
                Row(
                    Modifier.fillMaxWidth().clickable { onEditRule(r.packageName) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.displayName)
                        if (!installed) Text("이 폰에 없음", fontSize = 11.sp)
                    }
                    Switch(
                        checked = r.enabled,
                        onCheckedChange = { on ->
                            val rule = r.toRule().copy(enabled = on)
                            if (on && r.packageName in DefaultApps.messagingPackages) messagingWarning = rule else onSaveRule(rule)
                        },
                    )
                }
            }
            if (hidden.isNotEmpty()) {
                TextButton(onClick = { showHidden = !showHidden }) {
                    Text(if (showHidden) "이 폰에 없는 앱 숨기기" else "이 폰에 없는 기본 앱 ${hidden.size}개 보기")
                }
            }
            val ruled = rules.map { it.packageName }.toSet()
            val candidates = seenApps.filter { it.packageName !in ruled }
            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("최근 알림을 보낸 앱 (눌러서 추가)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                candidates.take(15).forEach { a ->
                    val label = appLabel(a.packageName) ?: a.packageName
                    Text(
                        "+ $label",
                        modifier = Modifier.fillMaxWidth().clickable {
                            val rule = AppRule(a.packageName, label, enabled = true)
                            if (a.packageName in DefaultApps.messagingPackages) messagingWarning = rule else onSaveRule(rule)
                        }.padding(vertical = 6.dp),
                    )
                }
            }
        }

        RealCard("예산") {
            Text("고정비", fontWeight = FontWeight.Bold)
            FixedExpenseEditor(rows)
            MoneyField("매달 먼저 떼어둘 저축", savings, { savings = it })
            Text("결산 요일 (다음 달부터 적용)", fontWeight = FontWeight.Bold)
            DayOfWeekPicker(settlementDay) { settlementDay = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = applyNow, onCheckedChange = { applyNow = it })
                Text("고정비·저축은 이번 달 예산에도 바로 반영", fontSize = 13.sp)
            }
            Button(onClick = {
                onSaveBudget(rows.mapNotNull { it.toFixed() }, savings.toLongOrNull() ?: 0, settlementDay, applyNow)
            }) { Text("예산 설정 저장") }
        }

        RealCard("카테고리 자동 분류") {
            Text("가게 이름으로 카테고리를 정해요. 기록에서 카테고리를 고치면 그 가게는 다음부터 고친 대로 분류해요.", fontSize = 12.sp)
            Text("기억한 가게 ${learnedCount}곳", fontSize = 13.sp)
            if (learnedCount > 0) OutlinedButton(onClick = onResetLearned) { Text("기억한 가게 지우기") }
        }

        RealCard("백업") {
            Text("모든 기록을 CSV 파일로 내보내요. 엑셀이나 구글 시트에서 열 수 있어요.", fontSize = 12.sp)
            OutlinedButton(onClick = onExport) { Text("내역 내보내기 (CSV)") }
        }
        Spacer(Modifier.height(80.dp))
    }

    messagingWarning?.let { rule ->
        AlertDialog(
            onDismissRequest = { messagingWarning = null },
            title = { Text("문자 앱을 켤까요?") },
            text = { Text("문자에는 은행·카드사를 사칭한 스미싱이 섞여 있어요. 결제 금액이 잘못 잡힐 수 있으니 확인함을 꼭 봐주세요.") },
            confirmButton = { TextButton(onClick = { onSaveRule(rule); messagingWarning = null }) { Text("켜기") } },
            dismissButton = { TextButton(onClick = { messagingWarning = null }) { Text("취소") } },
        )
    }
}

/** 앱 하나의 키워드 규칙 편집. */
@Composable
fun RuleEditScreen(rule: AppRule, onSave: (AppRule) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    val fields = remember(rule) {
        RuleAction.entries.associateWith { a ->
            mutableStateOf(rule.keywordRules.filter { it.action == a }.flatMap { it.keywords }.joinToString(", "))
        }
    }
    var name by remember(rule) { mutableStateOf(rule.displayName) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 돌아가기") }
        Text("키워드 규칙", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(rule.packageName, fontSize = 12.sp)
        OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("표시 이름") }, singleLine = true)
        Text("알림 제목이나 본문에 아래 단어가 들어가면 그렇게 처리해요. 쉼표로 나눠 적어요. 위에 있는 규칙이 먼저예요.", fontSize = 12.sp)
        RuleAction.entries.forEach { a ->
            var value by fields.getValue(a)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = {
                    Text(
                        when (a) {
                            RuleAction.IGNORE -> "무시 (광고 등)"
                            RuleAction.REFUND -> "환불·취소"
                            RuleAction.INCOME -> "입금 (월급 제안용)"
                            RuleAction.SPEND -> "지출"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = {
            val rules = RuleAction.entries.mapNotNull { a ->
                KeywordRuleCodec.splitKeywords(fields.getValue(a).value).takeIf { it.isNotEmpty() }?.let { KeywordRule(it, a) }
            }
            onSave(rule.copy(displayName = name.ifBlank { rule.displayName }, keywordRules = rules))
            onBack()
        }, modifier = Modifier.fillMaxWidth()) { Text("저장") }
        OutlinedButton(onClick = { onSave(rule.copy(keywordRules = AppRule.DEFAULT_KEYWORD_RULES)); onBack() }) { Text("기본 규칙으로 되돌리기") }
        TextButton(onClick = { onDelete(); onBack() }) { Text("이 앱 목록에서 빼기", color = DonColors.Warn) }
    }
}
