package com.simbio.don.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.budget.BudgetEngine
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.PayCycle
import java.time.DayOfWeek
import java.time.LocalDate

/** 고정비 편집용 한 줄 */
class FixedRow(name: String = "", amount: String = "") {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
    fun toFixed(): FixedExpense? = amount.toLongOrNull()?.takeIf { it > 0 }?.let { FixedExpense(name.ifBlank { "고정비" }, it) }
}

@Composable
fun FixedExpenseEditor(rows: MutableList<FixedRow>) {
    rows.forEachIndexed { i, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.name,
                onValueChange = { row.name = it.take(20) },
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            MoneyField("금액", row.amount, { row.amount = it }, Modifier.weight(1.3f))
        }
        if (rows.size > 1) TextButton(onClick = { rows.removeAt(i) }) { Text("이 줄 지우기") }
    }
    TextButton(onClick = { rows.add(FixedRow()) }) { Text("+ 고정비 추가") }
}

@Composable
fun OnboardingScreen(
    today: LocalDate,
    onDone: (
        salary: Long,
        payDay: Int,
        fixed: List<FixedExpense>,
        savings: Long,
        settlementDay: DayOfWeek,
        firstCycleBudget: Long?,
    ) -> Unit,
) {
    var salary by remember { mutableStateOf("") }
    var payDay by remember { mutableStateOf("25") }
    val fixed = remember { mutableStateListOf(FixedRow("월세"), FixedRow("통신비"), FixedRow("보험")) }
    var savings by remember { mutableStateOf("") }
    var settlementDay by remember { mutableStateOf(DayOfWeek.MONDAY) }

    val salaryValue = salary.toLongOrNull() ?: 0
    val fixedTotal = fixed.mapNotNull { it.toFixed() }.sumOf { it.amount }
    val savingsValue = savings.toLongOrNull() ?: 0
    val disposable = salaryValue - fixedTotal - savingsValue
    val payDayValue = payDay.toIntOrNull()?.takeIf { it in 1..31 }

    // 한 달 중간에 시작하면 오늘부터 다음 월급날 전날까지만 기록한다
    val cycle = payDayValue?.let { PayCycle.containing(today, it) }
    val midCycle = cycle?.let { today.isAfter(it.start) } ?: false
    var firstBudget by remember { mutableStateOf("") }
    var firstBudgetTouched by remember { mutableStateOf(false) }
    val prorated = cycle?.let { BudgetEngine.proratedDisposable(disposable.coerceAtLeast(0), it, today) } ?: 0
    val firstBudgetText = if (firstBudgetTouched) firstBudget else prorated.toString()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("처음 설정", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("입력한 내용은 이 폰에만 저장되고 어디로도 보내지 않아요.")

        RealCard("1. 월급") {
            MoneyField("이번 달 월급 (실수령액)", salary, { salary = it })
            OutlinedTextField(
                value = payDay,
                onValueChange = { payDay = it.filter(Char::isDigit).take(2) },
                label = { Text("월급날 (매달 며칠)") },
                supportingText = { Text("주말이면 그 전 평일로 봐요. 실제 입금일은 월급날마다 확인해요.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = payDayValue == null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        RealCard("2. 고정비 (월세·통신비·보험·구독 등)") {
            Text("월급에서 먼저 빠지는 돈이에요. 결제 알림이 와도 주 예산에서 또 빼지 않아요.", fontSize = 13.sp)
            FixedExpenseEditor(fixed)
        }

        RealCard("3. 먼저 떼어둘 저축") {
            Text("예산을 딱 맞게 써도 이 돈은 모여요. 실제 이체는 은행 앱 자동이체로 해두세요.", fontSize = 13.sp)
            MoneyField("매달 저축", savings, { savings = it })
        }

        RealCard("4. 결산 요일") {
            Text("이 요일 아침에 지난 한 주를 결산해요. 그 전날이 한 주의 마지막 날이에요.", fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            DayOfWeekPicker(settlementDay) { settlementDay = it }
        }

        if (cycle != null && midCycle) {
            RealCard("이번 달은 이미 시작됐어요") {
                val left = cycle.end.toEpochDay() - today.toEpochDay() + 1
                Text(
                    "지난 월급날(${shortDate(cycle.start)})부터 오늘까지 쓴 돈은 모르니까, 오늘부터 ${shortDate(cycle.end)}까지 " +
                        "${left}일만 기록해요. 그동안 쓸 생활비를 정해 주세요.",
                    fontSize = 13.sp,
                )
                MoneyField("${shortDate(cycle.end)}까지 쓸 생활비", firstBudgetText, { firstBudget = it; firstBudgetTouched = true })
                Text("남은 날 몫으로 계산하면 ${won(prorated)}이에요. 통장 사정에 맞게 고치세요.", fontSize = 12.sp)
            }
        }

        RealCard("한 달에 쓸 수 있는 돈") {
            Text(
                if (disposable >= 0) won(disposable) else "${won(-disposable)} 모자라요",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (disposable >= 0) DonColors.Real else DonColors.Warn,
            )
            Text("월급 − 고정비 ${won(fixedTotal)} − 저축 ${won(savingsValue)}", fontSize = 13.sp)
        }

        Button(
            onClick = {
                onDone(
                    salaryValue,
                    payDayValue!!,
                    fixed.mapNotNull { it.toFixed() },
                    savingsValue,
                    settlementDay,
                    if (midCycle) firstBudgetText.toLongOrNull() ?: 0 else null,
                )
            },
            enabled = salaryValue > 0 && payDayValue != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("이대로 예산 짜기") }
    }
}
