package com.simbio.don.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.budget.WeekResult
import com.simbio.don.budget.headline

/** 주간 결산. M0는 숫자만 — 배송 도착·등급·마일리지 연출은 M1~M3. */
@Composable
fun SettlementScreen(result: WeekResult, onDone: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 나중에") }
        Text("지난주 결산", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${shortDate(result.week.start)} ~ ${shortDate(result.week.end)} · ${result.week.days}일", fontSize = 13.sp)

        RealCard("실제 내 돈") {
            Text(
                result.headline(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (result.overspent > 0) DonColors.Warn else DonColors.Good,
            )
            Spacer(Modifier.height(8.dp))
            Line("기본 주 예산", won(result.budget))
            Line("실제 쓴 돈", won(result.spent))
            if (result.carryIn != 0L) Line("지난주에서 넘어온 돈", won(result.carryIn))
            Line("다음 주로 넘어가는 돈", won(result.carryOut), bold = true)
            if (result.overspent > 0 && result.carryIn > 0) {
                Text("넘어온 돈을 쓰면 기본 예산 기준으로는 초과로 쳐요.", fontSize = 12.sp)
            }
        }

        GameCard("게임") {
            Text("다음 버전부터 여기서 배송 도착·등급·마일리지를 받아요.", fontSize = 13.sp)
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("결산 완료") }
    }
}
