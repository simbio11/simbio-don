package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.AccountBalance
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.AppRuleEntity
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.enumOr
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.IconBubble
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import java.time.LocalDate
import java.time.YearMonth

fun AccountType.icon(): UiIcon = when (this) {
    AccountType.CASH -> UiIcon.CASH
    AccountType.BANK -> UiIcon.BANK
    AccountType.CARD -> UiIcon.CARD_LINE
    AccountType.ETC -> UiIcon.WALLET_LINE
}

fun AccountType.tint() = when (this) {
    AccountType.CASH -> DonColors.Good
    AccountType.BANK -> DonColors.Real
    AccountType.CARD -> DonColors.Game
    AccountType.ETC -> DonColors.InkSoft
}

/** 잔액 글자: 카드 빚은 "갚을 돈" */
fun balanceText(b: AccountBalance): String =
    if (b.type == AccountType.CARD && b.balance < 0) "갚을 돈 ${won(-b.balance)}" else (if (b.balance < 0) "−" else "") + won(kotlin.math.abs(b.balance))

/** 자산 탭: 순자산 · 종류별 잔액 */
@Composable
fun AssetsScreen(
    balances: List<AccountBalance>,
    onOpen: (AccountEntity) -> Unit,
    onAdd: () -> Unit,
    onTransfer: () -> Unit,
) {
    val shown = balances.filter { !it.account.hidden }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("자산", trailing = { SmallButton(onClick = onAdd) { Text("+ 자산 추가") } })
        RealCard("순자산") {
            val net = AccountBalances.net(balances)
            Text((if (net < 0) "−" else "") + won(kotlin.math.abs(net)), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Row(Modifier.fillMaxWidth().card(DonColors.SurfaceAlt, 12.dp).padding(vertical = 12.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("자산", fontSize = 12.sp, color = DonColors.InkMuted)
                    Text(won(AccountBalances.assets(balances)), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Income)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("부채 (카드값 등)", fontSize = 12.sp, color = DonColors.InkMuted)
                    Text(won(AccountBalances.debts(balances)), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Expense)
                }
            }
            if (shown.size >= 2) SubButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) { Text("이체 적기 (통장 → 현금, 카드값 내기)") }
        }
        if (shown.isEmpty()) {
            SectionCard("자산이 없어요") {
                Text(
                    "현금·통장·카드를 추가하면 수입·지출을 적을 때 어디서 나갔는지 고를 수 있고, 잔액이 저절로 맞춰져요. " +
                        "카드 앱을 연결하면 그 앱의 결제 알림은 그 카드로 적어요.",
                    fontSize = 13.sp,
                    color = DonColors.InkSoft,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("자산 추가하기") }
            }
        }
        AccountType.entries.forEach { t ->
            val group = shown.filter { it.type == t }
            if (group.isEmpty()) return@forEach
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                    val sum = group.sumOf { it.balance }
                    Text((if (sum < 0) "−" else "") + won(kotlin.math.abs(sum)), fontSize = 13.sp, color = DonColors.InkMuted)
                }
                group.forEach { b -> AccountRow(b) { onOpen(b.account) } }
            }
        }
        val hidden = balances.filter { it.account.hidden }
        if (hidden.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("숨긴 자산 (합계에서 빠짐)", fontSize = 13.sp, color = DonColors.InkMuted, modifier = Modifier.padding(vertical = 6.dp))
                hidden.forEach { b -> AccountRow(b) { onOpen(b.account) } }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AccountRow(b: AccountBalance, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(b.type.icon(), b.type.tint(), 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.account.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DonColors.Ink)
            if (b.account.packageName != null) Text("결제 알림 연결됨", fontSize = 11.sp, color = DonColors.InkMuted)
        }
        Text(
            balanceText(b),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (b.balance < 0) DonColors.Expense else DonColors.Ink,
        )
    }
}

/** 자산 하나: 잔액과 그 자산으로 적은 기록 (달마다) */
@Composable
fun AccountDetailScreen(
    balance: AccountBalance,
    entries: List<EntryEntity>,
    accountNames: Map<Long, String>,
    onEdit: () -> Unit,
    onTransfer: () -> Unit,
    onOpen: (EntryEntity) -> Unit,
    onBack: () -> Unit,
) {
    val id = balance.account.id
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar(balance.account.name, onBack, trailing = { TextButton(onClick = onEdit) { Text("수정") } })
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item {
                Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(balance.type.icon(), balance.type.tint(), 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(balance.type.label, fontSize = 13.sp, color = DonColors.InkMuted)
                    }
                    Text(balanceText(balance), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (balance.balance < 0) DonColors.Expense else DonColors.Ink)
                    Text("처음 잔액 ${won(balance.account.opening)}에서 시작해 아래 기록으로 계산했어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    SubButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) {
                        Text(if (balance.type == AccountType.CARD) "카드값 내기 (이체)" else "이체 적기")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (entries.isEmpty()) item {
                Text("이 자산으로 적은 기록이 없어요.", color = DonColors.InkMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            }
            val months = entries.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }.toSortedMap(compareByDescending { it })
            months.forEach { (ym, list) ->
                item(key = "m$ym") {
                    Column(Modifier.padding(bottom = 10.dp).fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        val change = list.sumOf { com.simbio.don.app.data.AccountBalances.change(it, id) }
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("${ym.year}년 ${ym.monthValue}월", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
                            Text(
                                (if (change >= 0) "+" else "−") + won(kotlin.math.abs(change)),
                                fontSize = 13.sp,
                                color = if (change >= 0) DonColors.Income else DonColors.Expense,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        list.forEach { e -> LedgerRow(e, accountNames, onOpen) }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

/** 자산 추가·수정 */
@Composable
fun AccountEditScreen(
    existing: AccountEntity?,
    rules: List<AppRuleEntity>,
    onSave: (AccountEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.let { enumOr(it.type, AccountType.ETC) } ?: AccountType.BANK) }
    var opening by remember { mutableStateOf(existing?.opening?.let { kotlin.math.abs(it).toString() }?.takeIf { it != "0" } ?: "") }
    var debt by remember { mutableStateOf((existing?.opening ?: 0) < 0) }
    var pkg by remember { mutableStateOf(existing?.packageName) }
    var hidden by remember { mutableStateOf(existing?.hidden ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar(if (existing == null) "자산 추가" else "자산 수정", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormCard("종류") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AccountType.entries.forEach { t -> FilterChip(selected = t == type, onClick = { type = t }, label = { Text(t.label) }) }
                }
            }
            FormCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("이름 (예: 생활비 통장, 신한카드)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(if (type == AccountType.CARD) "지금 갚을 돈 (없으면 비움)" else "지금 잔액", opening, { opening = it })
                if (type != AccountType.CARD) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("마이너스 통장처럼 빚이에요", fontSize = 13.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                        Switch(checked = debt, onCheckedChange = { debt = it })
                    }
                }
                Text("잔액은 오늘 기준으로 한 번만 넣으면 돼요. 이 자산으로 적는 수입·지출·이체로 저절로 바뀌어요.", fontSize = 12.sp, color = DonColors.InkMuted)
            }
            if (rules.isNotEmpty()) {
                FormCard("결제 알림 연결") {
                    Text("고른 앱의 결제·입금 알림은 이 자산으로 적어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = pkg == null, onClick = { pkg = null }, label = { Text("연결 안 함") })
                        rules.filter { it.enabled }.forEach { r ->
                            FilterChip(selected = pkg == r.packageName, onClick = { pkg = r.packageName }, label = { Text(r.displayName) })
                        }
                    }
                }
            }
            if (existing != null) {
                FormCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("숨기기", fontSize = 15.sp, color = DonColors.Ink)
                            Text("다 쓴 통장·해지한 카드. 기록은 남고 합계에서 빠져요.", fontSize = 12.sp, color = DonColors.InkMuted)
                        }
                        Switch(checked = hidden, onCheckedChange = { hidden = it })
                    }
                }
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }, color = DonColors.Warn) { Text("이 자산 지우기") }
            }
        }
        BottomBar {
            Button(
                onClick = {
                    val v = opening.toLongOrNull() ?: 0
                    val signed = if (type == AccountType.CARD || debt) -v else v
                    onSave(
                        (existing ?: AccountEntity(name = "", type = type.name, opening = 0, sortOrder = 0)).copy(
                            name = name.trim(),
                            type = type.name,
                            opening = signed,
                            packageName = pkg,
                            hidden = hidden,
                        ),
                    )
                    onBack()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("자산을 지울까요?") },
            text = { Text("이 자산으로 적은 기록은 남고, 자산만 '선택 안 함'으로 바뀌어요. 쓰지 않는 자산은 숨기기를 추천해요.") },
            confirmButton = { TextButton(onClick = { onDelete(); onBack() }, color = DonColors.Warn) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
