package com.simbio.don.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.toEntry
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CharacterSprite
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.OutlinedButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.WarnLine
import com.simbio.don.app.ui.kit.pixelPanel
import com.simbio.don.budget.Stats

@Composable
fun HomeScreen(
    home: HomeState,
    listenerEnabled: Boolean,
    onOpenListenerSettings: () -> Unit,
    onReview: () -> Unit,
    onSettle: () -> Unit,
    onSalary: () -> Unit,
    onOpenEntry: (EntryEntity) -> Unit,
    onAllEntries: () -> Unit,
    onStats: () -> Unit,
    onHouse: () -> Unit,
    onShop: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(shortDate(home.today), fontSize = 13.sp, color = DonColors.InkSoft)
                Text("돈 아끼는 게임", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            }
            CharacterSprite(home.mood, 48.dp)
        }

        if (!listenerEnabled) {
            Banner("결제 알림 자동 기록이 꺼져 있어요", "알림 접근을 허용하면 결제할 때마다 자동으로 기록돼요.", "켜러 가기", onOpenListenerSettings)
        }
        if (home.needsSalaryConfirm) {
            Banner("월급날이에요", "이번 달 월급을 확인하면 새 달 예산을 짜요.", "월급 확인", onSalary, UiIcon.COIN)
        }
        if (home.settleable.isNotEmpty()) {
            Banner(
                "지난주 결산이 준비됐어요",
                home.settleable.first().let { "${shortDate(it.week.start)} ~ ${shortDate(it.week.end)}" },
                "결산 보기",
                onSettle,
                UiIcon.STAR,
            )
        } else if (home.waitingForInbox) {
            Banner("확인함을 정리하면 결산해요", "오늘 정오가 지나면 자동으로 결산해요.", "확인함", onReview)
        }
        if (home.reviewCount > 0 && !home.waitingForInbox) {
            OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("확인할 결제 알림 ${home.reviewCount}건") }
        }

        val week = home.currentWeek
        RealCard("실제 내 돈 · 이번 주", icon = { Icon(UiIcon.COIN, 16.dp) }) {
            if (week != null) {
                Text(
                    won(week.available),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (week.available >= 0) DonColors.RealDeep else DonColors.Warn,
                )
                Text(
                    if (week.available > 0) "오늘은 ${won(home.todayAllowance)}까지 쓰면 딱 맞아요" else "이번 주 예산을 다 썼어요",
                    fontSize = 13.sp,
                    color = DonColors.InkSoft,
                )
                Spacer(Modifier.height(12.dp))
                Meter(week.spent, week.budget)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("쓴 돈 ${won(week.spent)}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("기본 예산 ${won(week.budget)}", fontSize = 12.sp, color = DonColors.InkSoft)
                }
                if (week.overspent > 0) WarnLine("기본 예산보다 ${won(week.overspent)} 더 썼어요", fontSize = 12)
                if (week.carryIn != 0L) Text("지난주에서 넘어온 돈 ${won(week.carryIn)} 포함", fontSize = 12.sp, color = DonColors.InkMuted)
                Text("${shortDate(week.week.start)} ~ ${shortDate(week.week.end)} · ${week.week.days}일", fontSize = 12.sp, color = DonColors.InkMuted)
            } else {
                Text("이번 주기 밖의 날짜예요. 월급을 확인해 주세요.")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("이번 달 쓴 돈", won(home.monthSpent), null, Modifier.weight(1f))
            StatTile("아낀 돈", won(home.savedSoFar), "지난 주들 합계", Modifier.weight(1f))
            StatTile("모은 돈", won(home.plan.savings), "먼저 뗀 저축", Modifier.weight(1f))
        }

        HomeGameCard(home, onHouse, onShop)

        val categories = Stats.byCategory(home.entries.map { it.toEntry() })
        if (categories.isNotEmpty()) {
            SectionCard("이번 달 많이 쓴 카테고리") {
                CategoryBars(categories, limit = 3)
                TextButton(onClick = onStats) { Text("통계 자세히 →") }
            }
        }

        SectionCard("최근 내역") {
            if (home.entries.isEmpty()) {
                Text("아직 기록이 없어요. 결제하면 알림으로 자동 기록되고, + 버튼으로 직접 넣을 수도 있어요.", fontSize = 13.sp, color = DonColors.InkSoft)
            }
            home.entries.take(5).forEach { EntryRow(it, onOpenEntry) }
            if (home.entries.size > 5) TextButton(onClick = onAllEntries) { Text("전체 ${home.entries.size}건 보기 →") }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

/** 알림 띠: 도트 아이콘 + 제목·설명 + 버튼 */
@Composable
fun Banner(title: String, body: String, action: String, onAction: () -> Unit, icon: UiIcon = UiIcon.WARN) {
    Row(
        Modifier.fillMaxWidth().pixelPanel(DonColors.WarnSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(body, fontSize = 12.sp, color = DonColors.InkSoft)
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAction, containerColor = DonColors.Warn) { Text(action, fontSize = 13.sp) }
    }
}
