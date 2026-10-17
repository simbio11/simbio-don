package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.ItemThumb
import com.simbio.don.app.ui.kit.KeyValue
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.WarnLine
import com.simbio.don.app.ui.kit.card
import com.simbio.don.game.ItemKind
import com.simbio.don.game.MockWallet
import com.simbio.don.game.SharedProduct
import kotlinx.coroutines.delay

private enum class ShopStep { FORM, CHECKOUT, PAYING, DONE }

/**
 * 모의 쇼핑: 상품 정보 → 쇼핑몰 같은 주문/결제 화면 → 주문 완료.
 * 어느 단계에서든 "모의 결제" 띠를 보여준다 (실제 돈과 헷갈리지 않게).
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
    val title = name.ifBlank { kind.label }

    LaunchedEffect(step) {
        if (step == ShopStep.PAYING) {
            delay(1_200)
            onBuy(name.trim(), amount, kind, store.ifBlank { null })
            step = ShopStep.DONE
        }
    }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        when (step) {
            ShopStep.FORM -> BackBar("모의 쇼핑", onBack)
            ShopStep.CHECKOUT -> BackBar("주문 / 결제", { step = ShopStep.FORM })
            else -> Spacer(Modifier.height(8.dp))
        }
        MockBadge()

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                ShopStep.FORM -> {
                    // 상품 미리보기 (쇼핑몰 상품 카드처럼)
                    Row(Modifier.fillMaxWidth().card().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ItemThumb(kind, 88.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (store.isNotBlank()) Text(store, fontSize = 12.sp, color = DonColors.InkMuted)
                            Text(
                                name.ifBlank { "뭘 사고 싶어요?" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (name.isBlank()) DonColors.InkMuted else DonColors.Ink,
                                maxLines = 2,
                            )
                            Text(if (amount > 0) won(amount) else "가격을 넣어 주세요", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Badge("무료배송", DonColors.Real)
                                Badge(if (kind.isFood) "먹고 나면 쓰레기" else kind.slot.label, DonColors.Game)
                            }
                        }
                    }
                    if (prefill != null) Text("공유해 온 내용을 채웠어요. 틀린 건 고쳐주세요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    Section("상품 정보") {
                        OutlinedTextField(name, { name = it.take(40) }, label = { Text("물건 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        MoneyField("가격", price, { price = it })
                        OutlinedTextField(store, { store = it.take(20) }, label = { Text("쇼핑몰 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Section("종류 · 집에서 놓일 자리") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ItemKind.entries.forEach { k ->
                                FilterChip(selected = k == kind, onClick = { picked = k }, label = { Text(k.label) })
                            }
                        }
                    }
                }

                ShopStep.CHECKOUT -> {
                    Section("배송지") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("게임 속 내 집", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DonColors.Ink)
                            Spacer(Modifier.width(6.dp))
                            Badge("기본 배송지", DonColors.InkSoft, DonColors.SurfaceAlt)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(UiIcon.TRUCK, 18.dp, DonColors.Game)
                            Text("주말 결산 때 도착 · 이번 주 예산을 지키면", fontSize = 13.sp, color = DonColors.GameDeep, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Section("주문 상품 1개") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemThumb(kind, 64.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                if (store.isNotBlank()) Text(store, fontSize = 12.sp, color = DonColors.InkMuted)
                                Text(title, fontSize = 15.sp, color = DonColors.Ink, maxLines = 2)
                                Text("수량 1개", fontSize = 12.sp, color = DonColors.InkMuted)
                            }
                            Text(won(amount), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DonColors.Ink)
                        }
                    }
                    Section("결제 수단") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.5.dp, DonColors.Game, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(DonColors.Game), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("모의 지갑", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DonColors.Ink)
                                Text("잔고 ${won(wallet)}", fontSize = 12.sp, color = DonColors.InkMuted)
                            }
                            Icon(UiIcon.WALLET, 22.dp, DonColors.Game)
                        }
                    }
                    Section("결제 금액") {
                        KeyValue("상품 금액", won(amount))
                        KeyValue("배송비", "0원")
                        Divider(Modifier.padding(vertical = 6.dp))
                        KeyValue("총 결제 금액", won(amount), bold = true, valueColor = DonColors.Game)
                        KeyValue("결제 후 잔고", won(wallet - amount))
                    }
                    if (!affordable) {
                        WarnLine("잔고가 부족해요. 이번 달 쓸 수 있는 돈보다 비싼 물건은 다음 버전에서 마일리지로 살 수 있어요.")
                    }
                    Text(
                        "실제 카드·계좌와 연결되어 있지 않아요. 결제해도 진짜 돈은 나가지 않아요.",
                        fontSize = 12.sp,
                        color = DonColors.InkMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                ShopStep.PAYING -> Box(Modifier.fillMaxWidth().padding(vertical = 120.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = DonColors.Game, strokeWidth = 3.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("결제하는 중…", color = DonColors.InkSoft)
                    }
                }

                ShopStep.DONE -> {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(DonColors.GameSurface), contentAlignment = Alignment.Center) {
                            Icon(UiIcon.CHECK, 40.dp, DonColors.Game)
                        }
                        Text("결제 완료!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                        Text(
                            "실제로는 한 푼도 나가지 않았어요.\n주말 결산 때 돌려받고, 이번 주 예산을 지키면 집으로 배송돼요.",
                            fontSize = 14.sp,
                            color = DonColors.InkSoft,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Section("주문 내역") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemThumb(kind, 64.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, fontSize = 15.sp, color = DonColors.Ink, maxLines = 2)
                                Text(won(amount), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DonColors.Ink)
                            }
                            Badge("배송 준비 중", DonColors.Game)
                        }
                    }
                }
            }
        }

        // 아래 고정 버튼 (쇼핑몰 결제 화면처럼)
        when (step) {
            ShopStep.FORM -> BottomBar {
                Button(onClick = { step = ShopStep.CHECKOUT }, enabled = amount > 0, containerColor = DonColors.Game, modifier = Modifier.fillMaxWidth()) {
                    Text("결제하러 가기")
                }
            }
            ShopStep.CHECKOUT -> BottomBar {
                Button(onClick = { step = ShopStep.PAYING }, enabled = affordable, containerColor = DonColors.Game, modifier = Modifier.fillMaxWidth()) {
                    Text("${won(amount)} 결제하기", fontSize = 17.sp)
                }
            }
            ShopStep.DONE -> BottomBar {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("닫기") }
                    Button(onClick = onHouse, containerColor = DonColors.Game, modifier = Modifier.weight(2f)) { Text("내 집 보기") }
                }
            }
            ShopStep.PAYING -> Unit
        }
    }
}

/** 흰 카드 + 굵은 소제목 */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DonColors.Ink)
        content()
    }
}

/** 화면 아래 고정 버튼 자리 */
@Composable
fun BottomBar(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(DonColors.Surface)) {
        Divider()
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

/** 모의 결제임을 알리는 띠 */
@Composable
fun MockBadge() {
    Row(
        Modifier.fillMaxWidth().background(DonColors.GameSurface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(UiIcon.INFO, 16.dp, DonColors.Game)
        Text("모의 결제 · 실제 돈은 나가지 않아요", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DonColors.GameDeep)
    }
}
