package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** 이번 달 월급 고치기 (보통은 입금 알림으로 저절로 맞춰진다) */
@Composable
fun SalaryScreen(home: HomeState, onSave: (Long) -> Unit, onBack: () -> Unit) {
    var amount by remember { mutableStateOf(home.salary.toString()) }
    val value = amount.toLongOrNull() ?: 0
    val diff = value - home.salary
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("이번 달 월급", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Text(
                "월급날은 따로 누를 필요 없어요. 월급날이 되면 새 달이 저절로 시작되고, 월급이 들어오는 은행 앱의 알림을 켜 두면 금액도 맞춰요.",
                fontSize = 12.sp,
                color = DonColors.InkMuted,
            )
        }
        BottomBar {
            Button(onClick = { onSave(value); onBack() }, enabled = value > 0, modifier = Modifier.fillMaxWidth()) { Text("저장") }
        }
    }
}
