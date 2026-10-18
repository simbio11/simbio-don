package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.mall.NaverItem
import com.simbio.don.app.mall.NaverShopping
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.ItemThumb
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.game.CartLine
import com.simbio.don.game.Cards
import com.simbio.don.game.Catalog
import com.simbio.don.game.ItemKind
import com.simbio.don.game.Miles
import com.simbio.don.game.MockWallet
import com.simbio.don.game.PriceTier
import com.simbio.don.game.Product
import kotlinx.coroutines.delay

/** 돈't 몰에서 산 물건의 가게 이름 */
const val MALL_NAME = "돈't 몰"

private sealed interface MallPage {
    data object Home : MallPage
    data class Detail(val product: Product, val store: String) : MallPage
    data object Cart : MallPage
    data object Paying : MallPage
    data class Done(val count: Int, val total: Long) : MallPage
}

/**
 * 돈't 몰: 모의 쇼핑 전용 쇼핑몰. 진짜 쇼핑몰처럼 고르고 담고 결제하지만 돈은 모의 지갑(가짜 돈)에서만 빠진다.
 * 상품은 직접 만든 목록이고, 네이버 쇼핑 검색 키를 넣었으면 실제 상품 이름·값도 찾아볼 수 있다 (검색어만 보냄).
 */
@Composable
fun MallScreen(
    wallet: Long,
    salary: Long,
    cart: List<CartLine>,
    naverOn: Boolean,
    onSearchNaver: suspend (String) -> NaverShopping.Result,
    onCart: (List<CartLine>) -> Unit,
    onBuy: (name: String, price: Long, kind: ItemKind, store: String?) -> Unit,
    onWish: (name: String, price: Long, kind: ItemKind, store: String?) -> Unit,
    onManual: () -> Unit,
    onHouse: () -> Unit,
    onBack: () -> Unit,
) {
    var page by remember { mutableStateOf<MallPage>(MallPage.Home) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<ItemKind?>(null) }
    var naver by remember { mutableStateOf<NaverShopping.Result?>(null) }
    var naverQuery by remember { mutableStateOf("") }
    val cartCount = cart.sumOf { it.qty }

    LaunchedEffect(naverQuery) {
        if (naverQuery.isNotBlank()) naver = onSearchNaver(naverQuery)
    }
    LaunchedEffect(page) {
        if (page == MallPage.Paying) {
            delay(1_200)
            cart.forEach { l ->
                repeat(l.qty) { onBuy(l.product.name + (l.option?.let { " ($it)" } ?: ""), l.product.price, l.product.kind, MALL_NAME) }
            }
            page = MallPage.Done(cartCount, cart.sumOf { it.total })
            onCart(emptyList())
        }
    }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        when (val p = page) {
            MallPage.Home -> BackBar(MALL_NAME, onBack, trailing = { CartButton(cartCount) { page = MallPage.Cart } })
            is MallPage.Detail -> BackBar("", { page = MallPage.Home }, trailing = { CartButton(cartCount) { page = MallPage.Cart } })
            MallPage.Cart -> BackBar("장바구니", { page = MallPage.Home })
            else -> Spacer(Modifier.height(8.dp))
        }
        MockBadge()
        when (val p = page) {
            MallPage.Home -> MallHome(
                query, { query = it }, kind, { kind = it }, salary, naverOn, naver,
                onNaver = { naverQuery = query },
                onOpen = { product, store -> page = MallPage.Detail(product, store) },
                onManual = onManual,
            )
            is MallPage.Detail -> ProductDetail(
                p.product, p.store, wallet, salary,
                onAdd = { option -> onCart(Catalog.add(cart, p.product, option)) },
                onBuyNow = { option -> onCart(Catalog.add(emptyList(), p.product, option)); page = MallPage.Cart },
                onWish = { onWish(p.product.name, p.product.price, p.product.kind, p.store); page = MallPage.Home },
            )
            MallPage.Cart -> CartPage(cart, wallet, onCart, onPay = { page = MallPage.Paying })
            MallPage.Paying -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("모의 결제 중…", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DonColors.GameDeep)
            }
            is MallPage.Done -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Icon(UiIcon.CHECK, 56.dp, DonColors.Good)
                Text("모의 결제 완료!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text("${p.count}개 · ${won(p.total)}", fontSize = 16.sp, color = DonColors.InkSoft)
                Text("이번 주 실제 예산을 지키면 주말 결산 때 집에 도착해요. 실제 돈은 그대로예요.", fontSize = 13.sp, color = DonColors.InkMuted, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubButton(onClick = { page = MallPage.Home }, modifier = Modifier.weight(1f)) { Text("계속 둘러보기") }
                    Button(onClick = onHouse, containerColor = DonColors.Game, modifier = Modifier.weight(1f)) { Text("내 집 보기") }
                }
            }
        }
    }
}

@Composable
private fun CartButton(count: Int, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp).size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "장바구니 ${count}개" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(UiIcon.STORE, 24.dp, DonColors.Ink)
        if (count > 0) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(DonColors.Warn), contentAlignment = Alignment.Center) {
                Text("$count", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

@Composable
private fun MallHome(
    query: String,
    onQuery: (String) -> Unit,
    kind: ItemKind?,
    onKind: (ItemKind?) -> Unit,
    salary: Long,
    naverOn: Boolean,
    naver: NaverShopping.Result?,
    onNaver: () -> Unit,
    onOpen: (Product, String) -> Unit,
    onManual: () -> Unit,
) {
    val found = Catalog.search(query).filter { kind == null || it.kind == kind }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        SearchBox(query, onQuery, "무엇을 사고 싶어요?")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(selected = kind == null, onClick = { onKind(null) }, label = { Text("전체") })
            ItemKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { onKind(k) }, label = { Text(k.label) }) }
        }
        if (query.isBlank() && kind == null) {
            Text("많이 담는 물건", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            ProductGrid(Catalog.popular(), salary) { onOpen(it, MALL_NAME) }
            Text("모든 물건", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        }
        if (found.isEmpty()) Text("'$query'은(는) 돈't 몰에 없어요.", fontSize = 13.sp, color = DonColors.InkMuted)
        ProductGrid(found, salary) { onOpen(it, MALL_NAME) }

        if (query.isNotBlank()) {
            SectionCard("네이버 쇼핑에서 찾기") {
                if (!naverOn) {
                    Text("설정 → 네이버 쇼핑 검색에 내 API 키를 넣으면 실제 상품 이름·값으로 모의 쇼핑할 수 있어요. 검색어만 보내요.", fontSize = 12.sp, color = DonColors.InkMuted)
                } else {
                    SubButton(onClick = onNaver, modifier = Modifier.fillMaxWidth()) { Text("'$query' 네이버 쇼핑 검색") }
                    when (naver) {
                        is NaverShopping.Result.Failed -> Text(naver.message, fontSize = 12.sp, color = DonColors.Warn)
                        is NaverShopping.Result.Ok -> naver.items.forEach { n -> NaverRow(n) { onOpen(n.toProduct(), n.mall) } }
                        null -> Unit
                    }
                }
            }
        }
        TextButton(onClick = onManual, color = DonColors.Game) { Text("찾는 게 없으면 직접 적어서 모의로 사기") }
        Spacer(Modifier.height(24.dp))
    }
}

private fun NaverItem.toProduct() = Product("naver:$title", title.take(40), price, ItemKind.guess("$title $category"))

@Composable
private fun NaverRow(n: NaverItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemThumb(ItemKind.guess("${n.title} ${n.category}"), 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, fontSize = 13.sp, color = DonColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(n.mall, fontSize = 11.sp, color = DonColors.InkMuted)
        }
        Text(won(n.price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
    }
}

@Composable
private fun ProductGrid(products: List<Product>, salary: Long, onOpen: (Product) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        products.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    Column(Modifier.weight(1f).card().clickable { onOpen(p) }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(10.dp)).background(DonColors.SurfaceAlt), contentAlignment = Alignment.Center) {
                            com.simbio.don.app.ui.kit.ItemIcon16(p.kind, 56.dp)
                        }
                        Text(p.name, fontSize = 13.sp, color = DonColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(won(p.price), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                        Cards.salaryShareBps(p.price, salary)?.let { Text("월급의 ${"%.1f".format(it / 100.0)}%", fontSize = 11.sp, color = DonColors.InkMuted) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProductDetail(product: Product, store: String, wallet: Long, salary: Long, onAdd: (String?) -> Unit, onBuyNow: (String?) -> Unit, onWish: () -> Unit) {
    var option by remember(product) { mutableStateOf(product.options.firstOrNull()) }
    var added by remember(product) { mutableStateOf(false) }
    val affordable = MockWallet.canAfford(wallet, product.price)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.4f).card(DonColors.SurfaceAlt), contentAlignment = Alignment.Center) {
                com.simbio.don.app.ui.kit.ItemIcon16(product.kind, 128.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(store, fontSize = 13.sp, color = DonColors.InkMuted)
                    Badge(PriceTier.of(product.price).label, DonColors.GameDeep)
                }
                Text(product.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text(won(product.price), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            }
            RealCard("실제로 산다면") {
                val share = Cards.salaryShareBps(product.price, salary)
                val hours = Cards.workHours(product.price, salary)
                if (share != null && hours != null) {
                    Text("월급의 ${"%.1f".format(share / 100.0)}%", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                    Text("${if (hours < 1) "${(hours * 60).toInt()}분" else "${hours}시간"} 일한 돈이에요 (한 달 209시간 기준)", fontSize = 13.sp, color = DonColors.InkSoft)
                }
                Text("마일로 치면 ${miles(Miles.ofWon(product.price))}", fontSize = 12.sp, color = DonColors.InkMuted)
            }
            product.optionLabel?.let { label ->
                SectionCard(label) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        product.options.forEach { o -> FilterChip(selected = option == o, onClick = { option = o }, label = { Text(o) }) }
                    }
                }
            }
            Text(
                "모의 상품이에요. 사고 싶을 때 여기서 사면 실제 돈은 그대로고, 이번 주 예산을 지키면 주말에 내 방으로 와요.",
                fontSize = 12.sp,
                color = DonColors.InkMuted,
            )
            if (!affordable) WarnLineSmall("모의 지갑(${won(wallet)})보다 비싸요. 찜해 두고 하루 고민해 봐요.")
            if (added) Text("장바구니에 담았어요", fontSize = 13.sp, color = DonColors.Good, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
        }
        BottomBar {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubButton(onClick = onWish) { Icon(UiIcon.HEART, 18.dp, DonColors.InkSoft) }
                SubButton(onClick = { onAdd(option); added = true }, modifier = Modifier.weight(1f)) { Text("장바구니") }
                Button(onClick = { onBuyNow(option) }, enabled = affordable, containerColor = DonColors.Game, modifier = Modifier.weight(1f)) { Text("바로 사기") }
            }
        }
    }
}

@Composable
private fun WarnLineSmall(text: String) = com.simbio.don.app.ui.kit.WarnLine(text)

@Composable
private fun CartPage(cart: List<CartLine>, wallet: Long, onCart: (List<CartLine>) -> Unit, onPay: () -> Unit) {
    val total = cart.sumOf { it.total }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (cart.isEmpty()) Text("장바구니가 비었어요.", fontSize = 14.sp, color = DonColors.InkMuted, modifier = Modifier.padding(vertical = 24.dp))
            cart.forEachIndexed { i, l ->
                Row(Modifier.fillMaxWidth().card().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemThumb(l.product.kind, 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.product.name, fontSize = 14.sp, color = DonColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        l.option?.let { Text("${l.product.optionLabel}: $it", fontSize = 12.sp, color = DonColors.InkMuted) }
                        Text(won(l.total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QtyButton("−", "하나 빼기") { onCart(if (l.qty <= 1) cart.filterIndexed { j, _ -> j != i } else cart.mapIndexed { j, x -> if (j == i) x.copy(qty = x.qty - 1) else x }) }
                        Text("${l.qty}", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                        QtyButton("+", "하나 더") { onCart(cart.mapIndexed { j, x -> if (j == i) x.copy(qty = (x.qty + 1).coerceAtMost(Catalog.MAX_QTY)) else x }) }
                    }
                }
            }
            if (cart.isNotEmpty()) {
                RealCard("결제 금액") {
                    KeyValue("상품 ${cart.sumOf { it.qty }}개", won(total))
                    KeyValue("배송비", "무료")
                    KeyValue("모의 지갑", won(wallet))
                    KeyValue("결제 후 모의 지갑", won(wallet - total), bold = true, valueColor = if (wallet - total < 0) DonColors.Warn else DonColors.Ink)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        BottomBar {
            Button(onClick = onPay, enabled = cart.isNotEmpty() && total <= wallet, containerColor = DonColors.Game, modifier = Modifier.fillMaxWidth()) {
                Text(if (total > wallet) "모의 지갑이 모자라요" else "${won(total)} 모의 결제하기")
            }
        }
    }
}

@Composable
private fun QtyButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(DonColors.SurfaceAlt).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.InkSoft) }
}

@Composable
private fun KeyValue(label: String, value: String, bold: Boolean = false, valueColor: androidx.compose.ui.graphics.Color = DonColors.Ink) =
    com.simbio.don.app.ui.kit.KeyValue(label, value, bold, valueColor)
