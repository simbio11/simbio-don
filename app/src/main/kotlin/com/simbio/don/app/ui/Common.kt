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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.simbio.don.budget.Won
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun won(amount: Long) = Won.format(amount)

fun dayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

fun shortDate(d: LocalDate): String = "${d.monthValue}/${d.dayOfMonth}(${dayName(d.dayOfWeek)})"

/** 입력칸 안에서 세 자리마다 쉼표 (값은 숫자만 그대로) */
internal object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val len = raw.length
        fun commaAfter(i: Int) = (len - 1 - i).let { it > 0 && it % 3 == 0 }
        val out = buildString {
            raw.forEachIndexed { i, c ->
                append(c)
                if (commaAfter(i)) append(',')
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + (0 until offset.coerceAtMost(len)).count(::commaAfter)
            override fun transformedToOriginal(offset: Int): Int = out.take(offset).count { it != ',' }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/** 숫자만 받는 금액 입력. 값은 원 단위 문자열(쉼표 없음), 보이는 건 1,234,000 */
@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.trimStart('0').take(12)) },
        label = { Text(label) },
        suffix = { Text("원") },
        visualTransformation = ThousandsTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 금액 빠르게 더하기: +1천 +5천 +1만 +5만 */
@Composable
fun AmountQuickAdd(value: String, steps: List<Long> = listOf(1_000, 5_000, 10_000, 50_000), onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        steps.forEach { step ->
            FilterChip(
                selected = false,
                onClick = { onChange(((value.toLongOrNull() ?: 0) + step).coerceAtMost(999_999_999_999).toString()) },
                label = { Text("+" + if (step % 10_000 == 0L) "${step / 10_000}만" else "${step / 1_000}천") },
            )
        }
        if (value.isNotEmpty()) FilterChip(selected = false, onClick = { onChange("") }, label = { Text("지우기") })
    }
}

@Composable
fun DayOfWeekPicker(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(selected = d == selected, onClick = { onSelect(d) }, label = { Text(dayName(d)) })
        }
    }
}
