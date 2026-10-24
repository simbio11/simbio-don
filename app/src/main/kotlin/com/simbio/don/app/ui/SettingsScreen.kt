package com.simbio.don.app.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.UiIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.simbio.don.app.ui.kit.Button
import androidx.compose.material3.Checkbox
import com.simbio.don.app.ui.kit.SubButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.simbio.don.app.ui.kit.TextButton
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
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.DefaultApps
import com.simbio.don.notify.KeywordRule
import com.simbio.don.notify.KeywordRuleCodec
import com.simbio.don.notify.RuleAction
import java.time.DayOfWeek

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    onSaveBudget: (List<FixedExpenseEntity>, Long, DayOfWeek, Boolean, Int?) -> Unit,
    onEditRule: (String) -> Unit,
    onBudgets: () -> Unit = {},
    onSaveRule: (AppRule) -> Unit,
    learnedCount: Int,
    onResetLearned: () -> Unit,
    onExport: () -> Unit,
    reminderOn: Boolean = false,
    onReminder: (Boolean) -> Unit = {},
    onPinWidget: ((com.simbio.don.app.widget.WidgetKind) -> Unit)? = null,
    version: String = "",
    weatherCity: String? = null,
    onWeatherCity: (String?) -> Unit = {},
    salary: Long = 0,
    salarySource: com.simbio.don.app.data.SalarySource = com.simbio.don.app.data.SalarySource.MANUAL,
    onSalary: () -> Unit = {},
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onCategories: () -> Unit = {},
    onRecurringIncome: () -> Unit = {},
    recurringCount: Int = 0,
    naverOn: Boolean = false,
    onNaverKeys: (String, String) -> Unit = { _, _ -> },
    onNaverClear: () -> Unit = {},
    lockOn: Boolean = false,
    onLock: (Boolean) -> Unit = {},
    onChangePin: () -> Unit = {},
    onOwnerName: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val rows = remember(fixed) {
        mutableStateListOf<FixedRow>().apply {
            addAll(fixed.map { FixedRow(it.name, it.amount.toString(), it.day?.toString() ?: "", it.id, it.category, it.accountId) })
            if (isEmpty()) add(FixedRow())
        }
    }
    var payDay by remember(settings) { mutableStateOf(settings.payDay.toString()) }
    var confirmRestore by remember { mutableStateOf(false) }
    var savings by remember(settings) { mutableStateOf(settings.savings.toString()) }
    var settlementDay by remember(settings) { mutableStateOf(DayOfWeek.of(settings.pendingSettlementDay ?: settings.settlementDay)) }
    var applyNow by remember { mutableStateOf(true) }
    var messagingWarning by remember { mutableStateOf<AppRule?>(null) }
    var showHidden by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
    if (onBack != null) BackBar("설정", onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack == null) ScreenTitle("설정", Modifier.padding(top = 8.dp))

        RealCard("월급", trailing = { com.simbio.don.app.ui.kit.MoreButton("고치기", onSalary) }) {
            Text(won(salary), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text(salarySourceText(salarySource), fontSize = 13.sp, color = DonColors.InkSoft)
            Text("월급날이 되면 새 달이 저절로 시작돼요. 월급이 들어오는 은행 앱을 '알림 읽을 앱'에 켜 두면 입금 알림으로 금액도 맞춰요.", fontSize = 12.sp, color = DonColors.InkMuted)
        }

        RealCard(
            "결제 알림 자동 기록",
            trailing = { Badge(if (listenerEnabled) "켜짐" else "꺼짐", if (listenerEnabled) DonColors.Good else DonColors.Warn) },
        ) {
            Text(
                "알림 접근 권한은 모든 알림을 볼 수 있는 권한이에요. 이 앱은 아래에서 고른 앱의 알림만 열어보고, 기기 밖으로 보내지 않아요.",
                fontSize = 13.sp,
                color = DonColors.InkSoft,
            )
            SubButton(onClick = onOpenListenerSettings, modifier = Modifier.fillMaxWidth()) { Text("알림 접근 설정 열기") }
            Text("갤럭시는 배터리 최적화 때문에 알림 수신이 끊길 수 있어요. '제한 없음'으로 바꿔 주세요.", fontSize = 13.sp, color = DonColors.InkSoft)
            SubButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) { Text("배터리 설정 열기") }
        }

        RealCard("내 계좌끼리 옮긴 돈") {
            Text(
                "내 통장끼리 옮긴 돈은 지출·수입이 아니라 이체로 적어요. 송금으로 나간 돈과 같은 금액이 15분 안에 다른 통장에 들어오면 " +
                    "한 건의 이체로 합치고, 알림에 '내 계좌'나 내 이름이 보이면 한쪽만 와도 이체로 봐요. 잘못 적히면 기록을 눌러 바꿀 수 있어요.",
                fontSize = 13.sp,
                color = DonColors.InkSoft,
            )
            var owner by remember(settings.ownerName) { mutableStateOf(settings.ownerName.orEmpty()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it.take(20) },
                    label = { Text("내 이름 (예금주)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                com.simbio.don.app.ui.kit.SmallButton(onClick = { onOwnerName(owner) }) { Text("저장") }
            }
            Text("이름은 이 폰에만 저장되고, 송금·입금 알림을 볼 때만 써요.", fontSize = 12.sp, color = DonColors.InkMuted)
        }

        RealCard("알림·위젯") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("매일 밤 9시 지출 알림", fontSize = 15.sp, color = DonColors.Ink)
                    Text("오늘 쓴 돈과 이번 주 남은 돈, 결산이 준비되면 알려줘요.", fontSize = 12.sp, color = DonColors.InkMuted)
                }
                Switch(checked = reminderOn, onCheckedChange = onReminder)
            }
            Divider(Modifier.padding(vertical = 4.dp))
            Text("홈 화면 위젯", fontSize = 15.sp, color = DonColors.Ink)
            Text("기록이 바뀌면 바로 다시 그려요. 이 폰 안의 기록만 읽어요.", fontSize = 12.sp, color = DonColors.InkMuted)
            com.simbio.don.app.widget.WidgetKind.entries.forEach { k ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(UiIcon.WIDGET, 18.dp, DonColors.InkMuted)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(k.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                        Text(k.description, fontSize = 11.sp, color = DonColors.InkMuted)
                    }
                    if (onPinWidget != null) {
                        com.simbio.don.app.ui.kit.SmallButton(onClick = { onPinWidget(k) }) { Text("추가") }
                    }
                }
            }
            if (onPinWidget == null) {
                Text("홈 화면을 길게 눌러 위젯 → '돈't'를 고르세요.", fontSize = 12.sp, color = DonColors.InkSoft)
            }
        }

        GameCard("내 방 창밖 날씨") {
            Text("고른 도시의 지금 날씨에 따라 창밖에 비·눈·구름이 보여요. 도시 좌표만 Open-Meteo로 보내고, 가계부 기록은 보내지 않아요.", fontSize = 12.sp, color = DonColors.InkSoft)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = weatherCity == null, onClick = { onWeatherCity(null) }, label = { Text("끄기") })
                com.simbio.don.app.weather.WeatherService.CITIES.forEach { c ->
                    FilterChip(selected = weatherCity == c.name, onClick = { onWeatherCity(c.name) }, label = { Text(c.name) })
                }
            }
        }

        RealCard("알림 읽을 앱") {
            Text("켜진 앱의 알림만 읽어요. 앱을 누르면 키워드 규칙을 바꿀 수 있어요.", fontSize = 13.sp, color = DonColors.InkSoft)
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
                        Text(r.displayName, fontSize = 15.sp, color = DonColors.Ink)
                        if (!installed) Text("이 폰에 없음", fontSize = 12.sp, color = DonColors.InkMuted)
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
                Text("최근 알림을 보낸 앱 (눌러서 추가)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DonColors.Ink)
                candidates.take(15).forEach { a ->
                    val label = appLabel(a.packageName) ?: a.packageName
                    Text(
                        "+ $label",
                        color = DonColors.Real,
                        modifier = Modifier.fillMaxWidth().clickable {
                            val rule = AppRule(a.packageName, label, enabled = true)
                            if (a.packageName in DefaultApps.messagingPackages) messagingWarning = rule else onSaveRule(rule)
                        }.padding(vertical = 6.dp),
                    )
                }
            }
        }

        RealCard("예산") {
            OutlinedTextField(
                value = payDay,
                onValueChange = { payDay = it.filter(Char::isDigit).take(2) },
                label = { Text("월급날 (매달 며칠)") },
                supportingText = { Text("바꾸면 이번 달은 새 월급날 전날까지예요.") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                isError = payDay.toIntOrNull()?.let { it !in 1..31 } ?: true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("고정비", fontWeight = FontWeight.Bold, color = DonColors.InkSoft, fontSize = 14.sp)
            FixedExpenseEditor(rows)
            MoneyField("매달 먼저 떼어둘 저축", savings, { savings = it })
            Text("결산 요일 (다음 달부터 적용)", fontWeight = FontWeight.Bold, color = DonColors.InkSoft, fontSize = 14.sp)
            DayOfWeekPicker(settlementDay) { settlementDay = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = applyNow, onCheckedChange = { applyNow = it })
                Text("고정비·저축은 이번 달 예산에도 바로 반영", fontSize = 13.sp)
            }
            SubButton(onClick = onRecurringIncome, modifier = Modifier.fillMaxWidth()) {
                Icon(UiIcon.REPEAT, 18.dp, DonColors.InkSoft)
                Text(if (recurringCount > 0) "반복 수입 ${recurringCount}개" else "반복 수입 (부수입·용돈 등)")
            }
            Button(onClick = {
                val list = rows.mapNotNull { r ->
                    r.amount.toLongOrNull()?.takeIf { it > 0 }?.let {
                        FixedExpenseEntity(id = r.id, name = r.name.ifBlank { "고정비" }, amount = it, day = r.dayValue(), accountId = r.accountId, category = r.category)
                    }
                }
                onSaveBudget(list, savings.toLongOrNull() ?: 0, settlementDay, applyNow, payDay.toIntOrNull()?.takeIf { it in 1..31 })
            }, modifier = Modifier.fillMaxWidth()) { Text("예산 설정 저장") }
            SubButton(onClick = onBudgets, modifier = Modifier.fillMaxWidth()) { Text("카테고리별 예산 정하기") }
        }

        RealCard("카테고리 자동 분류") {
            Text("가게 이름으로 카테고리를 정해요. 기록에서 카테고리를 고치면 그 가게는 다음부터 고친 대로 분류해요.", fontSize = 13.sp, color = DonColors.InkSoft)
            Text("기억한 가게 ${learnedCount}곳", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (learnedCount > 0) SubButton(onClick = onResetLearned) { Text("기억한 가게 지우기") }
            SubButton(onClick = onCategories, modifier = Modifier.fillMaxWidth()) {
                Icon(UiIcon.TAG, 18.dp, DonColors.InkSoft)
                Text("분류 관리 (소분류 · 직접 만들기)")
            }
        }

        GameCard("네이버 쇼핑 검색 (선택)") {
            Text(
                "돈't 몰에서 실제 상품 이름·값을 찾아 모의로 살 수 있어요. 네이버 개발자센터에서 '검색' API 애플리케이션을 만들고 " +
                    "Client ID·Secret을 넣어 주세요. 검색어만 네이버로 보내고, 가계부 기록은 보내지 않아요. 키는 이 폰에만 저장돼요.",
                fontSize = 12.sp,
                color = DonColors.InkSoft,
            )
            if (naverOn) {
                Text("켜져 있어요", fontSize = 14.sp, color = DonColors.Good, fontWeight = FontWeight.Bold)
                SubButton(onClick = onNaverClear, modifier = Modifier.fillMaxWidth()) { Text("키 지우기") }
            } else {
                var id by remember { mutableStateOf("") }
                var secret by remember { mutableStateOf("") }
                OutlinedTextField(value = id, onValueChange = { id = it.trim() }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = secret, onValueChange = { secret = it.trim() }, label = { Text("Client Secret") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SubButton(onClick = { onNaverKeys(id, secret) }, enabled = id.isNotBlank() && secret.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("저장") }
            }
        }

        RealCard("앱 잠금") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("암호 4자리로 잠그기", fontSize = 15.sp, color = DonColors.Ink)
                    Text("앱을 열 때와 30초 넘게 나갔다 돌아올 때 암호를 물어요. 암호는 이 폰에만 있고 백업에는 안 들어가요.", fontSize = 12.sp, color = DonColors.InkMuted)
                }
                Switch(checked = lockOn, onCheckedChange = onLock, modifier = Modifier.semantics { contentDescription = "앱 잠금" })
            }
            if (lockOn) SubButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) { Text("암호 바꾸기") }
        }

        RealCard("백업·복원") {
            Text("백업 파일에는 모든 기록·설정·자산·내 방이 들어가요. 폰을 바꿀 때 파일을 옮겨서 복원하세요. 파일은 고른 곳에만 저장되고 어디로도 보내지 않아요.", fontSize = 13.sp, color = DonColors.InkSoft)
            SubButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                Icon(UiIcon.UPLOAD, 18.dp, DonColors.InkSoft)
                Text("백업 파일 만들기")
            }
            SubButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(UiIcon.RESTORE, 18.dp, DonColors.InkSoft)
                Text("백업에서 복원")
            }
            Divider(Modifier.padding(vertical = 4.dp))
            Text("엑셀이나 구글 시트에서 보려면 CSV로 내보내요.", fontSize = 13.sp, color = DonColors.InkSoft)
            SubButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Icon(UiIcon.DOWNLOAD, 18.dp, DonColors.InkSoft)
                Text("내역 내보내기 (CSV)")
            }
        }

        Text(
            "돈't $version · 아이콘: Material Symbols (Google, Apache License 2.0)",
            fontSize = 11.sp,
            color = DonColors.InkMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(80.dp))
    }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("백업에서 복원할까요?") },
            text = { Text("지금 이 폰에 있는 기록은 모두 지워지고 백업 파일의 내용으로 바뀌어요.") },
            confirmButton = { TextButton(onClick = { confirmRestore = false; onRestore() }, color = DonColors.Warn) { Text("파일 고르기") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("취소") } },
        )
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
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
    BackBar("키워드 규칙", onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(rule.packageName, fontSize = 12.sp, color = DonColors.InkMuted)
        OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("표시 이름") }, singleLine = true)
        Text("알림 제목이나 본문에 아래 단어가 들어가면 그렇게 처리해요. 쉼표로 나눠 적어요. 위에 있는 규칙이 먼저예요.", fontSize = 13.sp, color = DonColors.InkSoft)
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
                            RuleAction.INCOME -> "입금 (수입으로 기록, 월급 알아보기)"
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
        SubButton(onClick = { onSave(rule.copy(keywordRules = AppRule.DEFAULT_KEYWORD_RULES)); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("기본 규칙으로 되돌리기") }
        TextButton(onClick = { onDelete(); onBack() }, color = DonColors.Warn) { Text("이 앱 목록에서 빼기") }
    }
    }
}
