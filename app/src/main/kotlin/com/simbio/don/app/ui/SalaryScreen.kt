package com.simbio.don.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import androidx.compose.material3.Text
import com.simbio.don.app.ui.kit.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.HomeState
import java.time.LocalDate

@Composable
fun SalaryScreen(
    home: HomeState,
    suggest: suspend () -> Long,
    onConfirm: (Long, LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    var amount by remember { mutableStateOf(home.lastSalary.toString()) }
    LaunchedEffect(Unit) { suggest().takeIf { it > 0 }?.let { amount = it.toString() } }
    // 예상 월급날 앞뒤로 실제 입금일을 고른다
    val candidates = (-3L..2L).map { home.nextExpectedPayday.plusDays(it) }.filter { !it.isAfter(home.today) }
    var start by remember { mutableStateOf(home.nextExpectedPayday) }
    val diff = (amount.toLongOrNull() ?: 0) - home.lastSalary

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← 나중에") }
        Text("이번 달 월급 확인", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("확인하면 지난달 남은 주를 마무리하고 새 달 예산을 짜요.", fontSize = 13.sp)
        MoneyField("이번 달 월급", amount, { amount = it })
        if (diff != 0L) Text("지난달 ${won(home.lastSalary)}보다 ${if (diff > 0) "+" else "−"}${won(kotlin.math.abs(diff))}", fontSize = 13.sp)
        Text("실제 입금일", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            candidates.forEach { d -> FilterChip(selected = d == start, onClick = { start = d }, label = { Text(shortDate(d)) }) }
        }
        Button(
            onClick = { onConfirm(amount.toLong(), start); onBack() },
            enabled = (amount.toLongOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("이대로 예산 짜기") }
    }
}
