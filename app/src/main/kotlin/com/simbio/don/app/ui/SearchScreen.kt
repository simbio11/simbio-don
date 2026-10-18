package com.simbio.don.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.EntryType
import java.time.LocalDate
import java.time.YearMonth

/** 전체 기간 검색: 내용·메모·금액 */
@Composable
fun SearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    results: List<EntryEntity>,
    accountNames: Map<Long, String>,
    onOpen: (EntryEntity) -> Unit,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = results.filter { e ->
        when (filter) {
            null -> true
            "INCOME" -> e.type == EntryType.INCOME.name
            "TRANSFER" -> e.type == EntryType.TRANSFER.name
            else -> e.type != EntryType.INCOME.name && e.type != EntryType.TRANSFER.name
        }
    }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("검색", onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item {
                SearchBox(query, onQuery, "내용·메모·금액으로 찾기")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "전체", "SPEND" to "지출", "INCOME" to "수입", "TRANSFER" to "이체").forEach { (k, label) ->
                        FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (query.isBlank()) item {
                Text("모든 달의 기록에서 찾아요. 예: 스타벅스, 4500", color = DonColors.InkMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                item {
                    val spend = shown.sumOf { spendSigned(it) }
                    val income = shown.filter { it.type == EntryType.INCOME.name }.sumOf { it.amount }
                    Text(
                        "${shown.size}건" + (if (spend != 0L) " · 지출 ${won(spend)}" else "") + (if (income > 0) " · 수입 ${won(income)}" else ""),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DonColors.Ink,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    if (results.size >= 500) Text("최근 500건까지만 보여요.", fontSize = 12.sp, color = DonColors.InkMuted)
                }
                val months = shown.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }.toSortedMap(compareByDescending { it })
                months.forEach { (ym, list) ->
                    item(key = "s$ym") {
                        Column(Modifier.padding(top = 8.dp).fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text("${ym.year}년 ${ym.monthValue}월", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
                                Text("${list.size}건", fontSize = 13.sp, color = DonColors.InkMuted)
                            }
                            list.forEach { e ->
                                Text(shortDate(LocalDate.ofEpochDay(e.epochDay)), fontSize = 11.sp, color = DonColors.InkMuted, modifier = Modifier.padding(top = 4.dp))
                                LedgerRow(e, accountNames, onOpen)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
