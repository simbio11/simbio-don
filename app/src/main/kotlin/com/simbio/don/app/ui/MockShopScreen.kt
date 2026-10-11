package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.game.ItemKind
import com.simbio.don.game.MockWallet
import com.simbio.don.game.SharedProduct
import kotlinx.coroutines.delay

private enum class ShopStep { FORM, CHECKOUT, PAYING, DONE }

/**
 * 모의 쇼핑: 물건 정보 → 진짜 같은 결제 화면 → 결제 완료.
 * 어느 단계에서든 "모의 결제" 표시를 보여준다 (실제 돈과 헷갈리지 않게).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MockShopScreen(
    wallet: Long,
    prefill: SharedProduct?,
    onBuy: (name: String, price: Long, kind: ItemKind, store: String?) -> Unit,
    onHouse: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(prefill?.name.orEmpty()) }
    var price by remember { mutableStateOf(prefill?.price?.toString().orEmpty()) }
    var store by remember { mutableStateOf(prefill?.store.orEmpty()) }
    var picked by remember { mutableStateOf<ItemKind?>(null) }
    var step by remember { mutableStateOf(ShopStep.FORM) }
    val kind = picked ?: ItemKind.guess(name)
    val amount = price.toLongOrNull() ?: 0
    val affordable = MockWallet.canAfford(wallet, amount)

    LaunchedEffect(step) {
        if (step == ShopStep.PAYING) {
            delay(1_200)
            onBuy(name.trim(), amount, kind, store.ifBlank { null })
            step = ShopStep.DONE
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (step == ShopStep.FORM || step == ShopStep.CHECKOUT) TextButton(onClick = if (step == ShopStep.FORM) onBack else ({ step = ShopStep.FORM })) { Text("← 돌아가기") }
        MockBadge()

        when (step) {
            ShopStep.FORM -> {
                Text("뭘 사고 싶어요?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (prefill != null) Text("공유해 온 내용을 채웠어요. 틀린 건 고쳐주세요.", fontSize = 12.sp, color = ChartColors.InkSecondary)
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("물건 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField("가격", price, { price = it })
                OutlinedTextField(store, { store = it.take(20) }, label = { Text("쇼핑몰 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("종류 (집에서 놓일 자리)", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemKind.entries.forEach { k ->
                        FilterChip(selected = k == kind, onClick = { picked = k }, label = { Text("${k.emoji()} ${k.label}") })
                    }
                }
                Text(
                    if (kind.isFood) "먹을거리는 먹고 나면 쓰레기가 남아요." else "${kind.slot.label}에 들어가요.",
                    fontSize = 12.sp,
                    color = ChartColors.InkSecondary,
                )
                Button(
                    onClick = { step = ShopStep.CHECKOUT },
                    enabled = amount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = DonColors.Game),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("결제하러 가기") }
            }

            ShopStep.CHECKOUT -> {
                Text("주문 / 결제", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemIcon(kind, 36.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name.ifBlank { kind.label }, fontWeight = FontWeight.Bold)
                            if (store.isNotBlank()) Text(store, fontSize = 12.sp, color = ChartColors.InkSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Line("상품 금액", won(amount))
                    Line("배송비", "무료")
                    Line("결제 수단", "모의 카드 •••• 0000")
                    Spacer(Modifier.height(8.dp))
                    Line("총 결제 금액", won(amount), bold = true)
                }
                Column(Modifier.fillMaxWidth().background(DonColors.GameSurface, RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Line("지금 잔고", won(wallet))
                    Line("결제 후 잔고", won(wallet - amount), bold = true)
                }
                if (!affordable) {
                    Text("⚠ 잔고가 부족해요. 이번 달 쓸 수 있는 돈보다 비싼 물건은 다음 버전에서 마일리지로 살 수 있어요.", fontSize = 13.sp, color = DonColors.Warn)
                }
                Button(
                    onClick = { step = ShopStep.PAYING },
                    enabled = affordable,
                    colors = ButtonDefaults.buttonColors(containerColor = DonColors.Game),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("${won(amount)} 결제하기", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }

            ShopStep.PAYING -> Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DonColors.Game)
                    Spacer(Modifier.height(16.dp))
                    Text("결제하는 중…")
                }
            }

            ShopStep.DONE -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(24.dp))
                Text("✓", fontSize = 56.sp, color = DonColors.Game)
                Text("결제 완료!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${name.ifBlank { kind.label }} · ${won(amount)}", fontSize = 15.sp)
                Text(
                    "실제로는 한 푼도 나가지 않았어요.\n주말 결산 때 돌려받고, 이번 주 예산을 지키면 집으로 배송돼요.",
                    fontSize = 13.sp,
                    color = ChartColors.InkSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onHouse, colors = ButtonDefaults.buttonColors(containerColor = DonColors.Game), modifier = Modifier.fillMaxWidth()) { Text("내 집 보기") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
            }
        }
    }
}

@Composable
fun MockBadge() {
    Text(
        "모의 결제 · 실제 돈은 나가지 않아요",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.background(DonColors.Game, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
