package com.simbio.don.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CharacterSprite
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.IconBubble
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.BudgetEngine
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.PayCycle
import com.simbio.don.budget.Payday
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.Gender
import com.simbio.don.game.Mood
import java.time.DayOfWeek
import java.time.LocalDate

/** 고정비 편집용 한 줄 ([id]가 있으면 이미 있는 고정비) */
class FixedRow(name: String = "", amount: String = "", day: String = "", val id: Long = 0, val category: String = "BILLS", val accountId: Long? = null) {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
    var day by mutableStateOf(day)
    fun dayValue(): Int? = day.toIntOrNull()?.takeIf { it in 1..31 }
    fun toFixed(): FixedExpense? = amount.toLongOrNull()?.takeIf { it > 0 }?.let { FixedExpense(name.ifBlank { "고정비" }, it, dayValue()) }
}

@Composable
fun FixedExpenseEditor(rows: MutableList<FixedRow>) {
    rows.forEachIndexed { i, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = row.name,
                onValueChange = { row.name = it.take(20) },
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            MoneyField("금액", row.amount, { row.amount = it }, Modifier.weight(1.25f))
            OutlinedTextField(
                value = row.day,
                onValueChange = { row.day = it.filter(Char::isDigit).take(2) },
                label = { Text("날") },
                placeholder = { Text("일") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = row.day.isNotEmpty() && row.dayValue() == null,
                singleLine = true,
                modifier = Modifier.width(62.dp),
            )
            if (rows.size > 1) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { rows.removeAt(i) }
                        .semantics { contentDescription = "이 줄 지우기" },
                    contentAlignment = Alignment.Center,
                ) { Icon(UiIcon.CLOSE, 18.dp, DonColors.InkMuted) }
            }
        }
    }
    Text("'날'은 매달 빠져나가는 날이에요. 적어 두면 그날 가계부에 저절로 적어요.", fontSize = 12.sp, color = DonColors.InkMuted)
    TextButton(onClick = { rows.add(FixedRow()) }, color = DonColors.Real) { Text("+ 고정비 추가") }
}

/** 처음 설정 결과 */
data class OnboardingResult(
    val salary: Long,
    val payDay: Int,
    val fixed: List<FixedExpense>,
    val savings: Long,
    val settlementDay: DayOfWeek,
    val firstCycleBudget: Long?,
    val accounts: List<DonRepository.AccountDraft>,
    val look: CharacterLook = CharacterLook.MALE,
)

/** 이번 달 남은 생활비를 정하는 방법 */
private enum class StartMode(val label: String) { PRORATE("남은 날만큼"), BALANCE("통장 잔액으로"), DIRECT("직접 정하기") }

private enum class Step { WELCOME, CHARACTER, SALARY, FIXED, SAVINGS, THIS_MONTH, ASSETS, FINISH }

/**
 * 처음 설정 (한 번만). 월급은 여기서만 묻고, 다음 달부터는 월급날·입금 알림으로 저절로 돌아간다.
 * 월급날이 이미 지난 채로 시작하는 게 보통이라, 이번 달은 남은 기간 생활비만 정해서 시작한다.
 */
@Composable
fun OnboardingScreen(
    today: LocalDate,
    listenerEnabled: Boolean = false,
    onOpenListenerSettings: () -> Unit = {},
    onDone: (OnboardingResult) -> Unit,
) {
    var step by remember { mutableStateOf(Step.WELCOME) }
    var salary by remember { mutableStateOf("") }
    var payDay by remember { mutableStateOf("25") }
    val fixed = remember { mutableStateListOf(FixedRow("월세"), FixedRow("통신비"), FixedRow("보험")) }
    var savings by remember { mutableStateOf("") }
    var settlementDay by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var mode by remember { mutableStateOf(StartMode.PRORATE) }
    var balance by remember { mutableStateOf("") }
    var savedAlready by remember { mutableStateOf(false) }
    var direct by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var bankTouched by remember { mutableStateOf(false) }
    var useCard by remember { mutableStateOf(true) }
    var cardDebt by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(Gender.MALE) }

    val salaryValue = salary.toLongOrNull() ?: 0
    val fixedList = fixed.mapNotNull { it.toFixed() }
    val fixedTotal = fixedList.sumOf { it.amount }
    val savingsValue = savings.toLongOrNull() ?: 0
    val disposable = salaryValue - fixedTotal - savingsValue
    val payDayValue = payDay.toIntOrNull()?.takeIf { it in 1..31 }
    val cycle = payDayValue?.let { PayCycle.containing(today, it) }
    val midCycle = cycle?.let { today.isAfter(it.start) } ?: false
    val daysLeft = cycle?.let { (it.end.toEpochDay() - today.toEpochDay() + 1).toInt() } ?: 0
    // 다음 월급날 전까지 아직 나갈 고정비 (날을 적은 것만)
    val remainingFixed = cycle?.let { c ->
        val rest = c.copy(start = today)
        fixedList.filter { f -> f.day?.let { Payday.dueDate(rest, it) } != null }.sumOf { it.amount }
    } ?: 0
    val unknownDayFixed = fixedList.filter { it.day == null }.sumOf { it.amount }
    val prorated = cycle?.let { BudgetEngine.proratedDisposable(disposable.coerceAtLeast(0), it, today) } ?: 0
    val fromBalance = ((balance.toLongOrNull() ?: 0) - remainingFixed - unknownDayFixed - (if (savedAlready) 0 else savingsValue)).coerceAtLeast(0) / 1_000 * 1_000
    val firstBudget = when (mode) {
        StartMode.PRORATE -> prorated
        StartMode.BALANCE -> fromBalance
        StartMode.DIRECT -> direct.toLongOrNull() ?: 0
    }
    val steps = Step.entries.filter { it != Step.THIS_MONTH || midCycle }
    val index = steps.indexOf(step)

    fun next() {
        steps.getOrNull(index + 1)?.let { step = it }
    }

    fun finish() {
        val accounts = buildList {
            add(DonRepository.AccountDraft("현금", AccountType.CASH, cash.toLongOrNull() ?: 0))
            add(DonRepository.AccountDraft("생활비 통장", AccountType.BANK, (if (bankTouched) bank else balance).toLongOrNull() ?: 0))
            if (useCard) add(DonRepository.AccountDraft("신용카드", AccountType.CARD, -(cardDebt.toLongOrNull() ?: 0)))
        }
        onDone(
            OnboardingResult(
                salaryValue, payDayValue!!, fixedList, savingsValue, settlementDay,
                if (midCycle) firstBudget else null, accounts, CharacterLook.default(gender),
            ),
        )
    }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        if (step != Step.WELCOME) {
            BackBar("처음 설정 ${index}/${steps.size - 1}", onBack = { steps.getOrNull(index - 1)?.let { step = it } })
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(DonColors.Line)) {
                Box(Modifier.fillMaxWidth(index.toFloat() / (steps.size - 1)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(DonColors.Real))
            }
        }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (s) {
                    Step.WELCOME -> Welcome()
                    Step.CHARACTER -> {
                        StepTitle("내 캐릭터", "아낀 돈으로 꾸미는 방에 살 캐릭터예요. 얼굴·머리·옷은 나중에 집 탭의 꾸미기에서 마음대로 바꿀 수 있어요.")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Gender.entries.forEach { g ->
                                val selected = gender == g
                                Column(
                                    Modifier.weight(1f).card().border(2.dp, if (selected) DonColors.Real else Color.Transparent, RoundedCornerShape(16.dp))
                                        .clickable { gender = g }.padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CharacterSprite(Mood.HAPPY, 108.dp, look = CharacterLook.default(g), animate = selected)
                                    Spacer(Modifier.height(8.dp))
                                    Text(g.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (selected) DonColors.Real else DonColors.Ink)
                                }
                            }
                        }
                    }
                    Step.SALARY -> {
                        StepTitle("월급을 알려주세요", "처음 한 번만 물어볼게요. 다음 달부터는 월급날이 되면 저절로 새 달이 시작되고, 월급 입금 알림으로 금액도 맞춰요.")
                        FormCard {
                            MoneyField("월급 (세금 뗀 실수령액)", salary, { salary = it })
                            OutlinedTextField(
                                value = payDay,
                                onValueChange = { payDay = it.filter(Char::isDigit).take(2) },
                                label = { Text("월급날 (매달 며칠)") },
                                supportingText = { Text("주말이면 그 전 금요일로 봐요. 말일이면 31.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = payDayValue == null,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                listOf(10, 15, 20, 21, 25, 31).forEach { d ->
                                    FilterChip(selected = payDayValue == d, onClick = { payDay = d.toString() }, label = { Text(if (d == 31) "말일" else "${d}일") })
                                }
                            }
                        }
                        if (cycle != null) {
                            Text(
                                "이번 달은 ${shortDate(cycle.start)} ~ ${shortDate(cycle.end)}이에요. 가계부의 한 달도 월급날부터 세요.",
                                fontSize = 13.sp,
                                color = DonColors.InkSoft,
                            )
                        }
                    }
                    Step.FIXED -> {
                        StepTitle("매달 나가는 고정비", "월세·통신비·보험·구독료처럼 월급에서 먼저 빠지는 돈이에요. 주 예산에서는 빼 두고, 나가는 날 가계부에 적어요.")
                        FormCard { FixedExpenseEditor(fixed) }
                        Text("고정비 합계 ${won(fixedTotal)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    }
                    Step.SAVINGS -> {
                        StepTitle("먼저 떼어둘 저축", "예산을 딱 맞게 써도 이 돈은 모여요. 실제 이체는 은행 앱 자동이체로 해두세요.")
                        FormCard { MoneyField("매달 저축", savings, { savings = it }) }
                        RealCard("한 달에 쓸 수 있는 생활비") {
                            Text(
                                if (disposable >= 0) won(disposable) else "${won(-disposable)} 모자라요",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (disposable >= 0) DonColors.Ink else DonColors.Warn,
                            )
                            Text("월급 ${won(salaryValue)} − 고정비 ${won(fixedTotal)} − 저축 ${won(savingsValue)}", fontSize = 13.sp, color = DonColors.InkMuted)
                            if (disposable > 0) Text("이 돈을 주마다 나눠서 써요.", fontSize = 13.sp, color = DonColors.Real, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Step.THIS_MONTH -> if (cycle != null) {
                        StepTitle(
                            "이번 달은 이미 시작됐어요",
                            "지난 월급날 ${shortDate(cycle.start)}부터 쓴 돈은 모르니까 오늘부터 ${shortDate(cycle.end)}까지 ${daysLeft}일만 기록해요. " +
                                "그동안 쓸 생활비를 정해 주세요. 다음 월급날부터는 저절로 한 달씩 돌아가요.",
                        )
                        SegmentedToggle(StartMode.entries.map { it.label }, mode.ordinal) { mode = StartMode.entries[it] }
                        FormCard {
                            when (mode) {
                                StartMode.PRORATE -> {
                                    Text("한 달 생활비 ${won(disposable.coerceAtLeast(0))} 중 남은 ${daysLeft}일 몫이에요.", fontSize = 13.sp, color = DonColors.InkSoft)
                                }
                                StartMode.BALANCE -> {
                                    MoneyField("지금 생활비 통장·현금 잔액", balance, { balance = it })
                                    if (remainingFixed + unknownDayFixed > 0) {
                                        KeyValueSmall("아직 나갈 고정비", "−${won(remainingFixed + unknownDayFixed)}")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = savedAlready, onCheckedChange = { savedAlready = it })
                                        Text("이번 달 저축은 이미 했어요", fontSize = 13.sp)
                                    }
                                    if (!savedAlready && savingsValue > 0) KeyValueSmall("이번 달 저축", "−${won(savingsValue)}")
                                    if (unknownDayFixed > 0) Text("나가는 날을 안 적은 고정비는 아직 안 나간 것으로 봤어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                                }
                                StartMode.DIRECT -> MoneyField("${shortDate(cycle.end)}까지 쓸 생활비", direct, { direct = it })
                            }
                        }
                        RealCard("${shortDate(cycle.end)}까지 생활비") {
                            Text(won(firstBudget), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                            Text("하루 ${won(firstBudget / daysLeft.coerceAtLeast(1))}꼴", fontSize = 14.sp, color = DonColors.Real, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Step.ASSETS -> {
                        StepTitle("자산 잔액 (선택)", "현금·통장·카드를 넣어 두면 가계부를 적을 때 어디서 나갔는지 고르고, 잔액이 저절로 맞춰져요. 나중에 자산 탭에서 바꿀 수 있어요.")
                        FormCard {
                            AssetLine(UiIcon.CASH, AccountType.CASH, "현금")
                            MoneyField("지갑 속 현금", cash, { cash = it })
                        }
                        FormCard {
                            AssetLine(UiIcon.BANK, AccountType.BANK, "생활비 통장")
                            MoneyField("통장 잔액", if (bankTouched) bank else balance, { bank = it; bankTouched = true })
                        }
                        FormCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { AssetLine(UiIcon.CARD_LINE, AccountType.CARD, "신용카드") }
                                Switch(checked = useCard, onCheckedChange = { useCard = it })
                            }
                            if (useCard) MoneyField("다음 결제일에 낼 카드값 (모르면 비움)", cardDebt, { cardDebt = it })
                        }
                    }
                    Step.FINISH -> {
                        StepTitle("거의 다 됐어요", "한 주는 결산 요일 아침에 마무리해요. 그 전날이 한 주의 마지막 날이에요.")
                        FormCard("결산 요일") { DayOfWeekPicker(settlementDay) { settlementDay = it } }
                        FormCard("결제 알림 자동 기록") {
                            Text(
                                "카드·페이·은행 앱의 결제·입금 알림을 읽어 가계부에 저절로 적어요. 월급 입금 알림도 여기서 알아봐요. " +
                                    "고른 앱의 알림만 열어보고, 기기 밖으로 보내지 않아요.",
                                fontSize = 13.sp,
                                color = DonColors.InkSoft,
                            )
                            if (listenerEnabled) {
                                Text("켜져 있어요", fontSize = 14.sp, color = DonColors.Good, fontWeight = FontWeight.Bold)
                            } else {
                                SubButton(onClick = onOpenListenerSettings, modifier = Modifier.fillMaxWidth()) { Text("알림 접근 켜기") }
                                Text("나중에 홈이나 설정에서 켜도 돼요.", fontSize = 12.sp, color = DonColors.InkMuted)
                            }
                        }
                        SummaryCard(salaryValue, payDayValue ?: 25, fixedTotal, savingsValue, disposable, if (midCycle) firstBudget else null, cycle)
                    }
                }
            }
        }
        BottomBar {
            when (step) {
                Step.WELCOME -> Button(onClick = ::next, modifier = Modifier.fillMaxWidth()) { Text("시작하기") }
                Step.FINISH -> Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text("이대로 시작하기") }
                else -> Button(
                    onClick = ::next,
                    enabled = when (step) {
                        Step.SALARY -> salaryValue > 0 && payDayValue != null
                        Step.THIS_MONTH -> firstBudget > 0 || mode == StartMode.PRORATE
                        else -> true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("다음") }
            }
        }
    }
}

@Composable
private fun Welcome() {
    Spacer(Modifier.height(24.dp))
    AppName(36.sp)
    Text(TAGLINE, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Real)
    Spacer(Modifier.height(16.dp))
    listOf(
        Triple(UiIcon.LIST, "가계부가 저절로", "결제·입금 알림으로 수입·지출을 적고, 일·주·월별로 보여줘요."),
        Triple(UiIcon.CALENDAR, "월급을 주 예산으로", "월급에서 고정비·저축을 떼고 남은 생활비를 주마다 나눠요. 매주 결산해요."),
        Triple(UiIcon.HOUSE, "참은 돈으로 모의 쇼핑", "사고 싶을 땐 가짜 돈으로 사요. 산 물건이 내 방을 채워요."),
    ).forEach { (icon, title, body) ->
        Row(Modifier.fillMaxWidth().card().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, DonColors.Real, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text(body, fontSize = 13.sp, color = DonColors.InkSoft)
            }
        }
    }
    Text("입력한 내용은 이 폰에만 저장되고 어디로도 보내지 않아요.", fontSize = 12.sp, color = DonColors.InkMuted)
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
    Text(body, fontSize = 14.sp, color = DonColors.InkSoft)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun AssetLine(icon: UiIcon, type: AccountType, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, type.tint(), 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
    }
}

@Composable
private fun KeyValueSmall(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = DonColors.Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryCard(salary: Long, payDay: Int, fixed: Long, savings: Long, disposable: Long, firstBudget: Long?, cycle: PayCycle?) {
    RealCard("이렇게 시작해요") {
        Line("월급 (매달 ${if (payDay == 31) "말일" else "${payDay}일"})", won(salary))
        Line("고정비", "−${won(fixed)}")
        Line("저축", "−${won(savings)}")
        Line("한 달 생활비", won(disposable.coerceAtLeast(0)), bold = true)
        if (firstBudget != null && cycle != null) {
            Line("이번 달 (${shortDate(cycle.end)}까지)", won(firstBudget), bold = true)
        }
        Text("다음 월급날부터는 따로 손대지 않아도 돼요.", fontSize = 12.sp, color = DonColors.InkMuted)
    }
}
