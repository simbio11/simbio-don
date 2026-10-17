package com.simbio.don.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import com.simbio.don.app.ui.kit.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.simbio.don.budget.Won
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun won(amount: Long) = Won.format(amount)

fun dayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

fun shortDate(d: LocalDate): String = "${d.monthValue}/${d.dayOfMonth}(${dayName(d.dayOfWeek)})"

/** 숫자만 받는 금액 입력. 값은 원 단위 문자열(쉼표 없음). */
@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(12)) },
        label = { Text(label) },
        suffix = { Text("원") },
        supportingText = value.toLongOrNull()?.let { { Text(won(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun DayOfWeekPicker(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(selected = d == selected, onClick = { onSelect(d) }, label = { Text(dayName(d)) })
        }
    }
}
