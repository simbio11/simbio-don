package com.simbio.don.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.HomeState
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Composable
fun AddEntryScreen(
    home: HomeState,
    onSave: (LocalDate, Long, String, EntryType) -> Unit,
    onBack: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(EntryType.SPEND) }
    // 오늘부터 이번 주기 시작까지 최근 7일 중에서 고른다
    val days = (0L..6L).map { home.today.minusDays(it) }.filter { it in home.cycle }
    var date by remember { mutableStateOf(days.firstOrNull() ?: home.today) }
    val canException = home.ledger.canAddException

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 돌아가기") }
        Text("지출 입력", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("현금·계좌이체처럼 알림이 안 오는 지출을 넣어요.", fontSize = 13.sp)
        MoneyField("금액", amount, { amount = it })
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(40) },
            label = { Text("어디에 썼나요 (선택)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("날짜", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            days.forEach { d -> FilterChip(selected = d == date, onClick = { date = d }, label = { Text(shortDate(d)) }) }
        }
        Text("종류", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(selected = type == EntryType.SPEND, onClick = { type = EntryType.SPEND }, label = { Text("일반 지출") })
            FilterChip(selected = type == EntryType.REFUND, onClick = { type = EntryType.REFUND }, label = { Text("환불") })
            FilterChip(
                selected = type == EntryType.EXCEPTION,
                onClick = { if (canException) type = EntryType.EXCEPTION },
                enabled = canException,
                label = { Text("예외 지출 ${home.ledger.exceptionCount}/2") },
            )
        }
        if (type == EntryType.EXCEPTION) {
            Text(
                "병원비·경조사비처럼 피할 수 없는 큰 지출이에요. 이번 주 결산에는 영향이 없고, 남은 주 예산을 줄여서 다시 나눠요.",
                fontSize = 13.sp,
                color = DonColors.Warn,
            )
        }
        Button(
            onClick = { onSave(date, amount.toLong(), label, type); onBack() },
            enabled = (amount.toLongOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("저장") }
    }
}

@Composable
fun EntriesScreen(entries: Flow<List<EntryEntity>>, onDelete: (EntryEntity) -> Unit, onBack: () -> Unit) {
    val list by entries.collectAsState(initial = emptyList())
    var confirm by remember { mutableStateOf<EntryEntity?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← 돌아가기") }
        Text("이번 달 내역", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (list.isEmpty()) Text("아직 기록이 없어요.")
        LazyColumn {
            items(list, key = { it.id }) { e ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${shortDate(LocalDate.ofEpochDay(e.epochDay))} ${e.label}", modifier = Modifier.weight(1f))
                        Text(
                            when (e.type) {
                                EntryType.REFUND.name -> "+${won(e.amount)}"
                                else -> "−${won(e.amount)}"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            buildString {
                                append(if (e.source == EntrySource.NOTIFICATION.name) "알림 자동" else "직접 입력")
                                if (e.type == EntryType.EXCEPTION.name) append(" · 예외 지출")
                                if (e.type == EntryType.REFUND.name) append(" · 환불")
                            },
                            fontSize = 12.sp,
                        )
                        if (confirm == e) {
                            TextButton(onClick = { onDelete(e); confirm = null }) { Text("정말 지우기", color = DonColors.Warn) }
                        } else {
                            TextButton(onClick = { confirm = e }) { Text("지우기") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
