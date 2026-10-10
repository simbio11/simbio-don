package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.MonthView
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.Stats
import java.time.LocalDate

/** 달(주기) 넘기기: ‹ 10월 (10/23~11/24) › */
@Composable
fun MonthSwitcher(view: MonthView, hasPrev: Boolean, hasNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev, enabled = hasPrev) { Text("‹", fontSize = 22.sp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            // 월급날 기준 한 달은 두 달에 걸치므로 "9월"처럼 부르지 않고 기간으로 보여준다
            Text("${shortDate(view.cycle.start)} ~ ${shortDate(view.cycle.end)}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(if (view.isCurrent) "이번 달 · 월급날 기준" else "월급날 기준", fontSize = 12.sp, color = ChartColors.InkSecondary)
        }
        TextButton(onClick = onNext, enabled = hasNext) { Text("›", fontSize = 22.sp) }
    }
}

@Composable
fun HistoryScreen(
    view: MonthView,
    weekStart: java.time.DayOfWeek,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate?) -> Unit,
    category: Category?,
    onCategory: (Category?) -> Unit,
    onOpen: (EntryEntity) -> Unit,
) {
    val daily = Stats.daily(view.entries)
    val filtered = view.entities.filter { e ->
        (selectedDate == null || e.epochDay == selectedDate.toEpochDay()) &&
            (category == null || e.category == category.name)
    }
    val present = view.entries.map { it.category }.toSet()
    val groups = filtered.groupBy { it.epochDay }.toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { MonthSwitcher(view, hasPrev, hasNext, onPrev, onNext) }
        item {
            RealCard("이번 달 ${won(Stats.total(view.entries))} 썼어요") {
                Spacer(Modifier.height(8.dp))
                MonthCalendar(view.cycle.start, view.cycle.end, weekStart, daily, view.today, selectedDate) { d ->
                    onSelectDate(if (d == selectedDate) null else d)
                }
                Text("칸 색이 진할수록 그날 많이 썼어요. 날짜를 누르면 그날 내역만 봐요.", fontSize = 11.sp, color = ChartColors.InkMuted)
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = category == null && selectedDate == null, onClick = { onCategory(null); onSelectDate(null) }, label = { Text("전체") })
                selectedDate?.let { d -> FilterChip(selected = true, onClick = { onSelectDate(null) }, label = { Text("${shortDate(d)} ✕") }) }
                Category.entries.filter { it in present }.forEach { c ->
                    FilterChip(selected = c == category, onClick = { onCategory(if (c == category) null else c) }, label = { Text("${c.emoji} ${c.label}") })
                }
            }
        }
        if (filtered.isEmpty()) item { Text("기록이 없어요.", modifier = Modifier.padding(vertical = 24.dp), color = ChartColors.InkSecondary) }
        groups.forEach { (day, list) ->
            item(key = "h$day") {
                val d = LocalDate.ofEpochDay(day)
                val sum = list.sumOf { if (it.type == EntryType.REFUND.name) -it.amount else it.amount }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                    Text(shortDate(d), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(won(sum), fontSize = 13.sp, color = ChartColors.InkSecondary)
                }
                HorizontalDivider()
            }
            items(list, key = { it.id }) { e -> EntryRow(e, onOpen) }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
fun EntryRow(e: EntryEntity, onOpen: (EntryEntity) -> Unit) {
    val c = Category.of(e.category)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.label, fontSize = 15.sp, maxLines = 1)
            Text(
                buildString {
                    append(c.label)
                    append(if (e.source == EntrySource.NOTIFICATION.name) " · 알림 자동" else " · 직접 입력")
                    if (e.type == EntryType.EXCEPTION.name) append(" · 예외 지출")
                    if (e.memo.isNotBlank()) append(" · ${e.memo}")
                },
                fontSize = 12.sp,
                color = ChartColors.InkMuted,
                maxLines = 1,
            )
        }
        Text(
            if (e.type == EntryType.REFUND.name) "+${won(e.amount)}" else "−${won(e.amount)}",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (e.type == EntryType.REFUND.name) DonColors.Good else ChartColors.InkPrimary,
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(ChartColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        content()
    }
}
