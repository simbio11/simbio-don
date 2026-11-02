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
    /** 결제 알림을 읽을 앱 (null이면 기본 앱 목록) */
    val apps: List<com.simbio.don.notify.AppRule>? = null,
    /** 내 이름 (예금주, 쉼표로 여러 표기) */
    val ownerName: String? = null,
)

/** 첫 설정에서 넣는 자산의 쓰임 */
private enum class AccountRole(val title: String, val type: AccountType, val kind: com.simbio.don.notify.InstitutionKind?, val icon: UiIcon, val suffix: String) {
    LIVING("생활비 통장", AccountType.BANK, com.simbio.don.notify.InstitutionKind.BANK, UiIcon.BANK, "생활비"),
    BANK("다른 통장", AccountType.BANK, com.simbio.don.notify.InstitutionKind.BANK, UiIcon.BANK, "통장"),
    CARD("카드", AccountType.CARD, com.simbio.don.notify.InstitutionKind.CARD, UiIcon.CARD_LINE, ""),
    PAY("페이 머니", AccountType.ETC, com.simbio.don.notify.InstitutionKind.PAY, UiIcon.WALLET, "머니"),
    SAVINGS("적금·예금", AccountType.SAVINGS, com.simbio.don.notify.InstitutionKind.BANK, UiIcon.SAVINGS, "적금"),
    INVEST("주식·투자", AccountType.INVEST, null, UiIcon.CHART, ""),
    CASH("현금", AccountType.CASH, null, UiIcon.CASH, ""),
}

/** 첫 설정 자산 한 줄: 은행·카드사 이름으로 고르고, 이름·잔액 */
private data class AccountRow(val key: Int, val role: AccountRole, val inst: String? = null, val nick: String = "", val amount: String = "", val touched: Boolean = false) {
    val institution get() = com.simbio.don.notify.Institutions.named(inst)
    fun name(): String = nick.trim().ifBlank {
        institution?.let { listOf(it.name, role.suffix).filter { s -> s.isNotBlank() }.joinToString(" ") }
            ?: if (role == AccountRole.CARD) "신용카드" else role.title
    }.take(20)
}

/** 폰에 설치된 앱 (실행 아이콘이 있는 것) */
data class InstalledApp(val packageName: String, val label: String)

/** 이번 달 남은 생활비를 정하는 방법 */
private enum class StartMode(val label: String) { PRORATE("남은 날만큼"), BALANCE("통장 잔액으로"), DIRECT("직접 정하기") }

private enum class Step { WELCOME, NAME, CHARACTER, SALARY, FIXED, SAVINGS, THIS_MONTH, ASSETS, APPS, FINISH }

/**
 * 처음 설정 (한 번만). 월급은 여기서만 묻고, 다음 달부터는 월급날·입금 알림으로 저절로 돌아간다.
 * 월급날이 이미 지난 채로 시작하는 게 보통이라, 이번 달은 남은 기간 생활비만 정해서 시작한다.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    today: LocalDate,
    listenerEnabled: Boolean = false,
    onOpenListenerSettings: () -> Unit = {},
    installedApps: List<InstalledApp> = emptyList(),
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
    var gender by remember { mutableStateOf(Gender.MALE) }
    var species by remember { mutableStateOf(com.simbio.don.game.Species.DOG) }
    var fur by remember { mutableStateOf("") }
    var petName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var ownerAlt by remember { mutableStateOf("") }
    // 생활비 통장·현금은 기본, 나머지(카드·다른 통장·페이·저축)는 더해서
    val rows = remember { mutableStateListOf(AccountRow(0, AccountRole.LIVING), AccountRow(1, AccountRole.CARD), AccountRow(2, AccountRole.CASH)) }
    val installedMap = remember(installedApps) { installedApps.associate { it.packageName to it.label } }
    // 자산마다 이을 앱 (같은 앱은 먼저 넣은 자산에만)
    fun linkedApps(): List<Pair<Int, String>> {
        val used = mutableSetOf<String>()
        return rows.mapNotNull { r ->
            val pkg = r.institution?.let { com.simbio.don.notify.Institutions.appFor(it, installedMap) } ?: return@mapNotNull null
            if (used.add(pkg)) r.key to pkg else null
        }
    }
    // 알림을 읽을 앱: 폰에 있는 결제·은행 앱은 미리 골라 둔다 (폰 목록을 못 읽으면 기본 앱 목록)
    val appChoices = remember(installedApps) {
        installedApps.ifEmpty { com.simbio.don.notify.DefaultApps.presets.map { InstalledApp(it.packageName, it.displayName) } }
    }
    val pickedApps = remember(appChoices) {
        androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply {
            appChoices.forEach { a -> if (com.simbio.don.notify.DefaultApps.looksLikePayment(a.packageName, a.label)) put(a.packageName, true) }
        }
    }

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
        // 고른 은행·카드 앱은 알림 읽을 앱으로 미리 골라 둔다
        if (step == Step.ASSETS) linkedApps().forEach { (_, pkg) -> pickedApps[pkg] = true }
        steps.getOrNull(index + 1)?.let { step = it }
    }

    fun finish() {
        val links = linkedApps().toMap()
        val accounts = rows.map { r ->
            val v = (if (r.role == AccountRole.LIVING && !r.touched) balance else r.amount).toLongOrNull() ?: 0
            DonRepository.AccountDraft(r.name(), r.role.type, if (r.role == AccountRole.CARD) -v else v, links[r.key])
        }
        val labels = appChoices.associate { it.packageName to it.label } +
            rows.mapNotNull { r -> links[r.key]?.let { it to (installedMap[it] ?: r.institution!!.name) } }
        // 은행·카드를 고를 때 그 앱을 미리 골라 뒀고, 앱 고르기에서 끈 건 끈 대로
        val apps = pickedApps.filterValues { it }.keys.map { com.simbio.don.notify.AppRule(it, labels[it] ?: it) }
        onDone(
            OnboardingResult(
                salaryValue, payDayValue!!, fixedList, savingsValue, settlementDay,
                if (midCycle) firstBudget else null, accounts,
                if (species.isPet) CharacterLook.pet(species, fur, petName, CharacterLook.default(gender)) else CharacterLook.default(gender), apps,
                listOf(ownerName, ownerAlt).map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
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
        ScreenBackHandler(step != Step.WELCOME) { steps.getOrNull(index - 1)?.let { step = it } }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { stepTransition(targetState.ordinal > initialState.ordinal) }, label = "step") { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (s) {
                    Step.WELCOME -> Welcome()
                    Step.NAME -> {
                        StepTitle(
                            "내 이름",
                            "통장에 적힌 예금주 이름이에요. 입금·송금 알림에서 보낸 사람이 나면 내 통장끼리 옮긴 돈으로 알아보고, 수입이나 지출로 세지 않아요.",
                        )
                        FormCard {
                            OutlinedTextField(
                                value = ownerName,
                                onValueChange = { ownerName = it.take(20) },
                                label = { Text("이름 (예: 홍길동)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = ownerAlt,
                                onValueChange = { ownerAlt = it.take(20) },
                                label = { Text("다른 표기 (선택, 예: HONG GILDONG)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("이 폰에만 저장되고, 알림을 읽을 때만 써요. 나중에 설정에서 바꿀 수 있어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                        }
                    }
                    Step.CHARACTER -> {
                        StepTitle(
                            "함께할 친구",
                            "아낀 돈으로 꾸미는 방에서 같이 사는 친구예요. 돈을 잘 지키면 신나 하고, 너무 쓰면 시무룩해져요. " +
                                "토끼·햄스터·여우·펭귄·판다는 마일을 모아 데려올 수 있어요.",
                        )
                        val options = listOf(com.simbio.don.game.Species.DOG, com.simbio.don.game.Species.CAT, com.simbio.don.game.Species.HUMAN)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            options.forEach { sp ->
                                val selected = species == sp
                                val look = if (sp.isPet) CharacterLook.pet(sp, if (selected) fur else "") else CharacterLook.default(gender)
                                Column(
                                    Modifier.weight(1f).card().border(2.dp, if (selected) DonColors.Real else Color.Transparent, RoundedCornerShape(16.dp))
                                        .clickable { if (species != sp) { species = sp; fur = "" } }.padding(vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CharacterSprite(Mood.HAPPY, 84.dp, look = look, animate = selected)
                                    Spacer(Modifier.height(6.dp))
                                    Text(sp.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (selected) DonColors.Real else DonColors.Ink)
                                }
                            }
                        }
                        if (species.isPet) {
                            FormCard("털색") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    com.simbio.don.app.ui.kit.CharacterRenderer.furColors(species.id).forEachIndexed { i, (name, _) ->
                                        FilterChip(selected = fur == name || (fur.isBlank() && i == 0), onClick = { fur = name }, label = { Text(name) })
                                    }
                                }
                            }
                            FormCard("이름 지어 주기") {
                                OutlinedTextField(
                                    value = petName,
                                    onValueChange = { petName = it.take(10) },
                                    label = { Text("이름 (예: 콩이, 나비)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            FormCard("사람") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Gender.entries.forEach { g -> FilterChip(selected = gender == g, onClick = { gender = g }, label = { Text(g.label) }) }
                                }
                                Text("얼굴·머리·옷은 나중에 집 탭의 꾸미기에서 바꿀 수 있어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                            }
                        }
                    }
                    Step.SALARY -> {
                        StepTitle(
                            "월급을 알려주세요",
                            "처음 한 번만 물어볼게요. 월급은 달마다 조금씩 다를 수 있어서, 월급날 통장에 들어온 금액(입금 알림)으로 그달 월급을 저절로 바꾸고 알려 드려요.",
                        )
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
                        StepTitle(
                            "내 통장·카드",
                            "계좌번호 없이 은행·카드사 이름만 골라요. 고르면 그 앱의 알림을 이 통장·카드에 적고, 잔액이 저절로 맞춰져요. " +
                                "생활비 통장은 생활비가 나가는 통장이에요. 나중에 자산 탭에서 바꿀 수 있어요.",
                        )
                        rows.forEachIndexed { i, r ->
                            AccountRowCard(
                                r,
                                shownAmount = if (r.role == AccountRole.LIVING && !r.touched) balance else r.amount,
                                app = r.institution?.let { com.simbio.don.notify.Institutions.appFor(it, installedMap) }?.let { installedMap[it] ?: r.institution!!.name },
                                appsKnown = installedMap.isNotEmpty(),
                                onChange = { rows[i] = it },
                                onRemove = if (r.role == AccountRole.LIVING) null else ({ rows.removeAt(i) }),
                            )
                        }
                        FormCard("더 넣기") {
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(AccountRole.BANK, AccountRole.CARD, AccountRole.PAY, AccountRole.SAVINGS, AccountRole.INVEST).forEach { role ->
                                    SubButton(onClick = { rows.add(AccountRow((rows.maxOfOrNull { it.key } ?: 0) + 1, role)) }) { Text("+ ${role.title}") }
                                }
                            }
                        }
                    }
                    Step.APPS -> AppsStep(appChoices, installedApps.isEmpty(), pickedApps)
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

/** 알림을 자동으로 적을 앱 고르기: 결제·은행 앱은 위에 미리 골라 두고, 나머지는 찾아서 고른다 */
@Composable
private fun AppsStep(apps: List<InstalledApp>, fallback: Boolean, picked: MutableMap<String, Boolean>) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    StepTitle(
        "자동으로 적을 앱",
        "결제·입금 알림이 오는 앱을 골라요. 고른 앱의 알림만 읽어 가계부에 적고, 내용은 이 폰 밖으로 보내지 않아요. 나중에 설정에서 바꿀 수 있어요.",
    )
    val (suggested, others) = apps.partition { com.simbio.don.notify.DefaultApps.looksLikePayment(it.packageName, it.label) }
    FormCard(if (fallback) "자주 쓰는 결제 앱" else "이 폰의 결제·은행·카드 앱") {
        if (suggested.isEmpty()) Text("찾지 못했어요. 아래에서 직접 골라 주세요.", fontSize = 13.sp, color = DonColors.InkMuted)
        suggested.forEach { a -> AppPickRow(a, picked[a.packageName] == true) { picked[a.packageName] = it } }
    }
    if (others.isNotEmpty()) {
        FormCard("다른 앱") {
            if (!showAll) {
                SubButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth()) { Text("다른 앱 ${others.size}개에서 고르기") }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("앱 이름 찾기") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                others.filter { query.isBlank() || query.trim() in it.label }.take(40).forEach { a ->
                    AppPickRow(a, picked[a.packageName] == true) { picked[a.packageName] = it }
                }
            }
        }
    }
    val count = picked.count { it.value }
    Text("${count}개 골랐어요", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (count > 0) DonColors.Real else DonColors.InkMuted)
    if (picked.filterValues { it }.keys.any { it in com.simbio.don.notify.DefaultApps.messagingPackages }) {
        com.simbio.don.app.ui.kit.WarnLine("문자 앱은 스미싱 문자도 섞여 와요. 결제 문자만 적지만, 낯선 링크는 누르지 마세요.")
    }
}

@Composable
private fun AppPickRow(app: InstalledApp, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Checkbox) { onChange(!on) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = on, onCheckedChange = null)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontSize = 15.sp, color = DonColors.Ink)
            Text(app.packageName, fontSize = 11.sp, color = DonColors.InkFaint, maxLines = 1)
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
    Text(body, fontSize = 14.sp, color = DonColors.InkSoft)
    Spacer(Modifier.height(4.dp))
}

/** 자산 한 줄 카드: 은행·카드사 고르기, 이름, 잔액 */
@Composable
private fun AccountRowCard(r: AccountRow, shownAmount: String, app: String?, appsKnown: Boolean, onChange: (AccountRow) -> Unit, onRemove: (() -> Unit)?) {
    FormCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { AssetLine(r.role.icon, r.role.type, if (r.inst != null || r.nick.isNotBlank()) "${r.role.title} · ${r.name()}" else r.role.title) }
            if (onRemove != null) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = "${r.role.title} 빼기" },
                    contentAlignment = Alignment.Center,
                ) { Icon(UiIcon.CLOSE, 18.dp, DonColors.InkMuted) }
            }
        }
        r.role.kind?.let { kind ->
            Text(
                when (kind) {
                    com.simbio.don.notify.InstitutionKind.BANK -> "어느 은행이에요?"
                    com.simbio.don.notify.InstitutionKind.CARD -> "어느 카드사예요?"
                    com.simbio.don.notify.InstitutionKind.PAY -> "어느 페이예요?"
                },
                fontSize = 13.sp,
                color = DonColors.InkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                com.simbio.don.notify.Institutions.of(kind).forEach { inst ->
                    FilterChip(selected = r.inst == inst.name, onClick = { onChange(r.copy(inst = if (r.inst == inst.name) null else inst.name)) }, label = { Text(inst.name) })
                }
            }
            if (r.inst != null) {
                Text(
                    when {
                        app != null && appsKnown -> "'$app' 앱 알림을 여기에 적어요"
                        app != null -> "${r.inst} 앱 알림을 여기에 적어요 (앱이 있으면)"
                        else -> "이 폰에서 ${r.inst} 앱을 못 찾았어요. 문자로 오는 알림은 '자동으로 적을 앱'에서 문자 앱을 골라요"
                    },
                    fontSize = 12.sp,
                    color = if (app != null) DonColors.Good else DonColors.InkMuted,
                )
            }
        }
        OutlinedTextField(
            value = r.nick,
            onValueChange = { onChange(r.copy(nick = it.take(20))) },
            label = { Text("이름 (선택, 비우면 '${r.copy(nick = "").name()}')") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        MoneyField(
            when (r.role) {
                AccountRole.CARD -> "다음 결제일에 낼 카드값 (모르면 비움)"
                AccountRole.INVEST -> "지금 금액"
                AccountRole.CASH -> "지갑 속 현금"
                else -> "지금 잔액"
            },
            shownAmount,
            { onChange(r.copy(amount = it, touched = true)) },
        )
    }
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
