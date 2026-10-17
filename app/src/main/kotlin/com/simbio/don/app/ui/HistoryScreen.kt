package com.simbio.don.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import com.simbio.don.app.ui.kit.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.MonthView
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.Stats
import java.time.LocalDate

/** 달(주기) 넘기기: ‹ 10/23 ~ 11/24 › */
@Composable
fun MonthSwitcher(view: MonthView, hasPrev: Boolean, hasNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(UiIcon.CHEVRON_LEFT, "이전 달", hasPrev, onPrev)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            // 월급날 기준 한 달은 두 달에 걸치므로 "9월"처럼 부르지 않고 기간으로 보여준다
            Text("${shortDate(view.cycle.start)} ~ ${shortDate(view.cycle.end)}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text(if (view.isCurrent) "이번 달 · 월급날 기준" else "월급날 기준", fontSize = 12.sp, color = DonColors.InkMuted)
        }
        RoundIconButton(UiIcon.CHEVRON_RIGHT, "다음 달", hasNext, onNext)
    }
}

/** 둥근 검색칸 */
@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String) {
    Row(
        Modifier.fillMaxWidth().card(radius = 12.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(UiIcon.SEARCH, 20.dp, DonColors.InkMuted)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = DonColors.InkFaint)
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.take(30)) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = DonColors.Ink),
                cursorBrush = SolidColor(DonColors.Real),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).clickable(role = Role.Button) { onChange("") }
                    .semantics { contentDescription = "검색 지우기" },
                contentAlignment = Alignment.Center,
            ) { Icon(UiIcon.CLOSE, 18.dp, DonColors.InkMuted) }
        }
    }
}

@Composable
fun RoundIconButton(icon: UiIcon, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(DonColors.Surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, 22.dp, if (enabled) DonColors.Ink else DonColors.Line) }
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
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val filtered = view.entities.filter { e ->
        (selectedDate == null || e.epochDay == selectedDate.toEpochDay()) &&
            (category == null || e.category == category.name) &&
            (q.isEmpty() || e.label.contains(q, ignoreCase = true) || e.memo.contains(q, ignoreCase = true))
    }
    val present = view.entries.map { it.category }.toSet()
    val groups = filtered.groupBy { it.epochDay }.toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { MonthSwitcher(view, hasPrev, hasNext, onPrev, onNext) }
        item {
            SearchBox(query, { query = it }, "가게 이름·메모 검색")
            Spacer(Modifier.height(12.dp))
        }
        if (q.isNotEmpty()) item {
            val sum = filtered.sumOf(::spendSigned)
            Text(
                "'$q' ${filtered.size}건 · ${won(sum)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DonColors.Ink,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        } else item {
            RealCard("이번 달 ${won(Stats.total(view.entries))} 썼어요") {
                MonthSummaryRow(Stats.income(view.entries), Stats.total(view.entries))
                Spacer(Modifier.height(4.dp))
                MonthCalendar(view.cycle.start, view.cycle.end, weekStart, daily, view.today, selectedDate) { d ->
                    onSelectDate(if (d == selectedDate) null else d)
                }
                Text("칸 색이 진할수록 그날 많이 썼어요. 날짜를 누르면 그날 내역만 봐요.", fontSize = 11.sp, color = ChartColors.InkMuted)
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = category == null && selectedDate == null, onClick = { onCategory(null); onSelectDate(null) }, label = { Text("전체") })
                selectedDate?.let { d -> FilterChip(selected = true, onClick = { onSelectDate(null) }, label = { Text("${shortDate(d)} ×") }) }
                Category.entries.filter { it in present }.forEach { c ->
                    FilterChip(selected = c == category, onClick = { onCategory(if (c == category) null else c) }, label = { CategoryLabel(c) })
                }
            }
        }
        if (filtered.isEmpty()) item { Text("기록이 없어요.", modifier = Modifier.padding(vertical = 24.dp), color = DonColors.InkMuted) }
        groups.forEach { (day, list) ->
            item(key = "d$day") {
                val d = LocalDate.ofEpochDay(day)
                val sum = list.sumOf(::spendSigned)
                Column(Modifier.padding(top = 10.dp).fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
                        Text(shortDate(d), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                        Text(won(sum), fontSize = 13.sp, color = DonColors.InkMuted)
                    }
                    list.forEach { e -> EntryRow(e, onOpen) }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** 지출 쪽 금액 (환불은 빼고 수입은 0) */
fun spendSigned(e: EntryEntity): Long = when (e.type) {
    EntryType.REFUND.name -> -e.amount
    EntryType.INCOME.name -> 0
    else -> e.amount
}

/** 수입 · 지출 · 남은 돈 세 칸 */
@Composable
fun MonthSummaryRow(income: Long, spent: Long) {
    Row(Modifier.fillMaxWidth().card(DonColors.SurfaceAlt, 12.dp).padding(vertical = 12.dp)) {
        listOf(
            Triple("수입", "+${won(income)}", DonColors.Real),
            Triple("지출", "−${won(spent)}", DonColors.Ink),
            Triple("합계", (if (income - spent >= 0) "+" else "−") + won(kotlin.math.abs(income - spent)), if (income - spent >= 0) DonColors.Good else DonColors.Warn),
        ).forEach { (label, value, color) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 12.sp, color = DonColors.InkMuted)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            }
        }
    }
}

@Composable
fun EntryRow(e: EntryEntity, onOpen: (EntryEntity) -> Unit) {
    val c = Category.of(e.category)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(e) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(c, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DonColors.Ink, maxLines = 1)
            Text(
                buildString {
                    append(c.label)
                    append(if (e.source == EntrySource.NOTIFICATION.name) " · 알림 자동" else " · 직접 입력")
                    if (e.type == EntryType.EXCEPTION.name) append(" · 예외 지출")
                    if (e.type == EntryType.INCOME.name) append(" · 수입")
                    if (e.memo.isNotBlank()) append(" · ${e.memo}")
                },
                fontSize = 12.sp,
                color = DonColors.InkMuted,
                maxLines = 1,
            )
        }
        val plus = e.type == EntryType.REFUND.name || e.type == EntryType.INCOME.name
        Text(
            if (plus) "+${won(e.amount)}" else "−${won(e.amount)}",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (plus) DonColors.Real else DonColors.Ink,
        )
    }
}

/** 흰 카드 + 제목 (+ 오른쪽 더보기) */
@Composable
fun SectionCard(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(horizontal = 20.dp, vertical = 18.dp)) {
        PanelTitle(title, trailing = trailing)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** 칩 안의 카테고리 이름 */
@Composable
fun CategoryLabel(c: Category) {
    Text(c.label)
}
