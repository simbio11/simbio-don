package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.SalarySource
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button

/** 월급이 어디서 왔는지 한 줄 */
fun salarySourceText(source: SalarySource): String = when (source) {
    SalarySource.ONBOARDING -> "처음 설정에서 넣은 월급이에요."
    SalarySource.NOTIFICATION -> "월급 입금 알림을 보고 맞췄어요."
    SalarySource.ESTIMATED -> "아직 월급 입금을 못 봐서 지난달 월급으로 짰어요. 입금 알림이 오면 저절로 맞춰요."
    SalarySource.MANUAL -> "직접 고친 월급이에요."
}

/**
 * 이번 달 월급·생활비 고치기 (보통은 입금 알림으로 저절로 맞춰진다).
 * 생활비 = 월급 − 고정비 − 계획한 저축 ± 이번 달에 바뀐 만큼. 생활비를 직접 정하면 오늘부터 남은 날에 다시 나뉜다
 * (지난 주에 쓴 돈은 그대로, 이번 주·남은 주의 쓸 돈만 바뀐다).
 */
@Composable
fun SalaryScreen(home: HomeState, onSave: (Long) -> Unit, onBack: () -> Unit, onBudget: (Long) -> Unit = {}) {
    var amount by remember { mutableStateOf(home.salary.toString()) }
    var budget by remember { mutableStateOf(home.plan.disposable.toString()) }
    val value = amount.toLongOrNull() ?: 0
    val diff = value - home.salary
    val budgetValue = budget.toLongOrNull()
    val budgetDiff = (budgetValue ?: home.plan.disposable) - home.plan.disposable
    val ch = home.budgetChange
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("이번 달 월급·생활비", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormCard {
                Text("${shortDate(home.cycle.start)}부터 시작한 달", fontSize = 13.sp, color = DonColors.InkMuted)
                Text(salarySourceText(home.salarySource), fontSize = 14.sp, color = DonColors.InkSoft)
                MoneyField("이번 달 월급 (실수령액)", amount, { amount = it })
                if (diff != 0L && value > 0) {
                    Text(
                        "생활비가 한 달 ${if (diff > 0) "+" else "−"}${won(kotlin.math.abs(diff))} 바뀌어요. 주 예산을 다시 나눠요.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DonColors.Real,
                    )
                }
            }
            FormCard("이번 달 생활비") {
                // 어떻게 정해졌나: 한 줄씩
                SumLine("월급", won(home.plan.salary))
                SumLine("고정 지출", "−" + won(home.plan.fixedTotal))
                SumLine("계획한 저축", "−" + won(home.plan.savings))
                if (ch.extraSaved > 0) SumLine("계획보다 더 저축함", "−" + won(ch.extraSaved))
                if (ch.withdrawn > 0) SumLine("저축에서 꺼내 씀", "+" + won(ch.withdrawn))
                if (ch.manual != 0L) SumLine("직접 고침·통장에 맞춤", (if (ch.manual > 0) "+" else "−") + won(kotlin.math.abs(ch.manual)))
                SumLine("생활비", won(home.plan.disposable), bold = true)
                MoneyField("생활비 직접 정하기", budget, { budget = it })
                Text(
                    if (budgetDiff == 0L) "바꾸면 오늘부터 남은 날에 다시 나눠요. 이미 쓴 돈은 그대로예요."
                    else "생활비 ${if (budgetDiff > 0) "+" else "−"}${won(kotlin.math.abs(budgetDiff))} · 이번 주와 남은 주에 날 수대로 나눠 반영해요.",
                    fontSize = 12.sp,
                    color = if (budgetDiff == 0L) DonColors.InkMuted else DonColors.Real,
                )
            }
            Text(
                "월급날은 따로 누를 필요 없어요. 월급날이 되면 새 달이 저절로 시작되고, 월급이 들어오는 은행 앱의 알림을 켜 두면 금액도 맞춰요. " +
                    "저축 통장으로 옮긴 돈은 계획한 저축까지는 그대로, 넘게 옮기면 그만큼 생활비를 줄여 다시 나눠요.",
                fontSize = 12.sp,
                color = DonColors.InkMuted,
            )
        }
        BottomBar {
            Button(
                onClick = {
                    if (diff != 0L && value > 0) onSave(value)
                    // 월급을 바꾸면 생활비 계획이 바뀌므로, 생활비는 직접 바꿨을 때만 따로 맞춘다
                    if (budgetDiff != 0L && budgetValue != null && diff == 0L) onBudget(budgetValue)
                    onBack()
                },
                enabled = value > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }
}

/** 생활비 계산 한 줄 */
@Composable
private fun SumLine(label: String, value: String, bold: Boolean = false) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = if (bold) DonColors.Ink else DonColors.InkSoft, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = DonColors.Ink, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}
