package com.simbio.don.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.OutlinedButton
import androidx.compose.material3.Text
import com.simbio.don.app.ui.kit.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.ReviewItemEntity
import com.simbio.don.app.data.ReviewResolution
import com.simbio.don.notify.ReviewReason
import java.time.Instant
import java.time.ZoneId

@Composable
fun ReviewScreen(
    items: List<ReviewItemEntity>,
    canException: Boolean,
    onResolve: (ReviewItemEntity, ReviewResolution) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← 돌아가기") }
        Text("확인 필요", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("자동으로 넣기 애매한 결제 알림이에요. 하나씩 골라 정리해 주세요.", fontSize = 13.sp)
        if (items.isEmpty()) Text("정리할 게 없어요.", modifier = Modifier.padding(top = 16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(items, key = { it.id }) { item -> ReviewCard(item, canException, onResolve) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    item: ReviewItemEntity,
    canException: Boolean,
    onResolve: (ReviewItemEntity, ReviewResolution) -> Unit,
) {
    val reason = ReviewReason.valueOf(item.reason)
    val time = Instant.ofEpochSecond(item.postedAtEpochSecond).atZone(ZoneId.systemDefault()).toLocalDateTime()
    RealCard(
        when (reason) {
            ReviewReason.POSSIBLE_DUPLICATE -> "중복일 수 있어요"
            ReviewReason.LOOKS_LIKE_FIXED_EXPENSE -> "고정비와 비슷해요"
            ReviewReason.REFUND -> "취소·환불"
            ReviewReason.UNCLASSIFIED -> "어떤 알림인지 모르겠어요"
        },
    ) {
        Text("${item.label} · ${won(item.amount)}", fontWeight = FontWeight.Bold)
        Text("${shortDate(time.toLocalDate())} ${"%02d:%02d".format(time.hour, time.minute)}", fontSize = 12.sp)
        Text(item.rawText, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (reason) {
                ReviewReason.POSSIBLE_DUPLICATE -> {
                    Button(onClick = { onResolve(item, ReviewResolution.DISMISS) }) { Text("하나만 반영") }
                    OutlinedButton(onClick = { onResolve(item, ReviewResolution.APPLY_SPEND) }) { Text("둘 다 반영") }
                }
                ReviewReason.LOOKS_LIKE_FIXED_EXPENSE -> {
                    Button(onClick = { onResolve(item, ReviewResolution.DISMISS) }) { Text("고정비라 제외") }
                    OutlinedButton(onClick = { onResolve(item, ReviewResolution.APPLY_SPEND) }) { Text("지출로 반영") }
                }
                ReviewReason.REFUND -> {
                    Button(onClick = { onResolve(item, ReviewResolution.APPLY_REFUND) }) { Text("환불로 반영") }
                    OutlinedButton(onClick = { onResolve(item, ReviewResolution.DISMISS) }) { Text("무시") }
                }
                ReviewReason.UNCLASSIFIED -> {
                    Button(onClick = { onResolve(item, ReviewResolution.APPLY_SPEND) }) { Text("지출로 반영") }
                    OutlinedButton(onClick = { onResolve(item, ReviewResolution.APPLY_REFUND) }) { Text("환불") }
                    OutlinedButton(onClick = { onResolve(item, ReviewResolution.DISMISS) }) { Text("무시") }
                }
            }
            if (canException && reason != ReviewReason.REFUND) {
                TextButton(onClick = { onResolve(item, ReviewResolution.APPLY_EXCEPTION) }) { Text("예외 지출로") }
            }
        }
    }
}
