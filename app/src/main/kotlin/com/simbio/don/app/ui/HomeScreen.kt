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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.HomeState
import com.simbio.don.budget.CycleLedger

@Composable
fun HomeScreen(
    home: HomeState,
    listenerEnabled: Boolean,
    onOpenListenerSettings: () -> Unit,
    onAddEntry: () -> Unit,
    onEntries: () -> Unit,
    onReview: () -> Unit,
    onSettle: () -> Unit,
    onSalary: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!listenerEnabled) {
            Banner(
                "결제 알림 자동 기록이 꺼져 있어요",
                "알림 접근을 허용하면 결제할 때마다 자동으로 기록돼요.",
                "켜러 가기",
                onOpenListenerSettings,
            )
        }
        if (home.needsSalaryConfirm) {
            Banner("월급날이에요", "이번 달 월급을 확인하면 새 달 예산을 짜요.", "월급 확인", onSalary)
        }
        if (home.settleable.isNotEmpty()) {
            Banner("지난주 결산이 준비됐어요", home.settleable.first().let { "${shortDate(it.week.start)}~${shortDate(it.week.end)}" }, "결산 보기", onSettle)
        } else if (home.waitingForInbox) {
            Banner("확인함을 정리하면 결산해요", "오늘 정오가 지나면 자동으로 결산해요.", "확인함", onReview)
        }

        val week = home.currentWeek
        RealCard("실제 내 돈") {
            if (week != null) {
                Text("이번 주 쓸 수 있는 돈", fontSize = 14.sp)
                Text(
                    won(week.available),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (week.available >= 0) DonColors.Real else DonColors.Warn,
                )
                Text("${shortDate(week.week.start)} ~ ${shortDate(week.week.end)} · ${week.week.days}일", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Line("기본 주 예산", won(week.budget))
                if (week.carryIn != 0L) Line("지난주에서 넘어온 돈", won(week.carryIn))
                Line("이번 주 쓴 돈", won(week.spent))
                if (week.overspent > 0) {
                    Text("기본 예산보다 ${won(week.overspent)} 더 썼어요", color = DonColors.Warn, fontSize = 13.sp)
                }
            } else {
                Text("이번 주기 밖의 날짜예요. 월급을 확인해 주세요.")
            }
            Spacer(Modifier.height(8.dp))
            Line("이번 달 아낀 돈", won(home.savedSoFar), bold = true)
            Line("먼저 뗀 저축", won(home.plan.savings))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddEntry, modifier = Modifier.weight(1f)) { Text("지출 입력") }
            OutlinedButton(onClick = onReview, modifier = Modifier.weight(1f)) {
                Text(if (home.reviewCount > 0) "확인함 ${home.reviewCount}" else "확인함")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEntries, modifier = Modifier.weight(1f)) { Text("이번 달 내역") }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("설정") }
        }

        RealCard("이번 달 주 예산") {
            Text(
                "${shortDate(home.cycle.start)} ~ ${shortDate(home.cycle.end)} · 쓸 수 있는 돈 ${won(home.plan.disposable)}",
                fontSize = 13.sp,
            )
            Text("예외 지출 ${home.ledger.exceptionCount}/${CycleLedger.MAX_EXCEPTIONS}회 사용", fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            home.results.forEach { r ->
                val now = home.today in r.week
                val past = home.today.isAfter(r.week.end)
                Line(
                    "${r.week.index + 1}주 ${shortDate(r.week.start)}~",
                    when {
                        past -> "${won(r.spent)} / ${won(r.budget)}"
                        now -> "진행 중 · ${won(r.budget)}"
                        else -> won(r.budget)
                    },
                    bold = now,
                )
            }
        }

        GameCard("게임") {
            Text("모의 쇼핑 · 창고 · 마일리지는 다음 버전에서 열려요.", fontSize = 13.sp)
        }
    }
}

@Composable
fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun Banner(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E0)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = DonColors.Warn)) { Text(action) }
        }
    }
}
