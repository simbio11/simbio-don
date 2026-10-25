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
            // 월급날 기준 한 달: 월급이 들어온 달로 부르고 기간을 같이 보여준다
            val ym = java.time.YearMonth.parse(view.nominal)
            Text("${ym.year}년 ${ym.monthValue}월", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text("${shortDate(view.cycle.start)} ~ ${shortDate(view.cycle.end)} · 월급날 기준", fontSize = 11.sp, color = DonColors.InkMuted)
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

/** 지출 쪽 금액 (환불은 빼고 수입은 0) */
fun spendSigned(e: EntryEntity): Long = when (e.type) {
    EntryType.REFUND.name -> -e.amount
    EntryType.INCOME.name, EntryType.TRANSFER.name -> 0
    // 나눠 낸 지출은 내 몫만
    else -> e.share ?: e.amount
}

/** 수입 · 지출 · 남은 돈 세 칸 */
@Composable
fun MonthSummaryRow(income: Long, spent: Long) {
    Row(Modifier.fillMaxWidth().card(DonColors.SurfaceAlt, 12.dp).padding(vertical = 12.dp)) {
        listOf(
            Triple("수입", "+${won(income)}", DonColors.Income),
            Triple("지출", "−${won(spent)}", DonColors.Expense),
            Triple("합계", (if (income - spent >= 0) "+" else "−") + won(kotlin.math.abs(income - spent)), DonColors.Ink),
        ).forEach { (label, value, color) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 12.sp, color = DonColors.InkMuted)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            }
        }
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
