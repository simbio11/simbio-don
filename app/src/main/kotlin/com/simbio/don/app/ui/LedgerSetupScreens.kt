package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.RecurringIncomeEntity
import com.simbio.don.app.data.SubcategoryEntity
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CategoryColors
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.app.ui.kit.CategoryIcons
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.Category

/**
 * 분류 관리: 카테고리마다 소분류를 더하고 고치고 지운다. 카테고리를 직접 만들 수도 있다 (이름·아이콘·색).
 * 기본 카테고리는 지우거나 이름을 바꿀 수 없다 (통계·예산이 기대는 기준이라).
 */
@Composable
fun CategoryManageScreen(
    subcategories: Map<String, List<SubcategoryEntity>>,
    /** 직접 만든 카테고리 (바뀌면 다시 그리게) */
    customs: List<Category>,
    onAddCategory: (String, Boolean, String, Long) -> Unit,
    onUpdateCategory: (String, String, String, Long) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onAddSub: (Category, String) -> Unit,
    onRenameSub: (SubcategoryEntity, String) -> Unit,
    onDeleteSub: (SubcategoryEntity) -> Unit,
    onBack: () -> Unit,
) {
    var income by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var subFor by remember { mutableStateOf<Category?>(null) }
    var renaming by remember { mutableStateOf<SubcategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    // customs를 읽어 두면 직접 만든 카테고리가 바뀔 때 목록도 다시 그린다
    val list = remember(customs, income) { if (income) Category.incomes else Category.spending }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("분류 관리", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedToggle(listOf("지출", "수입"), if (income) 1 else 0) { income = it == 1; open = null }
            Text("카테고리를 누르면 소분류를 고칠 수 있어요. 가계부를 쓸 때 카테고리 아래에서 소분류를 골라요.", fontSize = 13.sp, color = DonColors.InkSoft)
            Column(Modifier.fillMaxWidth().card()) {
                list.forEachIndexed { i, c ->
                    val subs = subcategories[c.name].orEmpty()
                    val expanded = open == c.name
                    if (i > 0) com.simbio.don.app.ui.kit.Divider(Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (expanded) null else c.name }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryIcon(c, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                                if (c.custom) {
                                    Spacer(Modifier.width(6.dp))
                                    com.simbio.don.app.ui.kit.Badge("직접 만듦", DonColors.InkMuted)
                                }
                            }
                            Text(
                                if (subs.isEmpty()) "소분류 없음" else subs.joinToString(" · ") { it.name },
                                fontSize = 12.sp,
                                color = DonColors.InkMuted,
                                maxLines = 1,
                            )
                        }
                        Icon(UiIcon.EXPAND, 20.dp, DonColors.InkMuted, Modifier.rotate(if (expanded) 180f else 0f))
                    }
                    if (expanded) {
                        Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            subs.forEach { s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        s.name,
                                        fontSize = 14.sp,
                                        color = DonColors.Ink,
                                        modifier = Modifier.weight(1f).clickable { renaming = s }.padding(vertical = 8.dp),
                                    )
                                    Box(
                                        Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { onDeleteSub(s) }
                                            .semantics { contentDescription = "${s.name} 지우기" },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(UiIcon.CLOSE, 18.dp, DonColors.InkMuted) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { subFor = c }, color = DonColors.Real) { Text("+ 소분류") }
                                if (c.custom) {
                                    TextButton(onClick = { editing = c }) { Text("이름·아이콘") }
                                    TextButton(onClick = { deleting = c }, color = DonColors.Warn) { Text("지우기") }
                                }
                            }
                        }
                    }
                }
            }
            SubButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text(if (income) "+ 수입 분류 만들기" else "+ 지출 분류 만들기") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (adding) {
        CategoryDialog(
            title = if (income) "수입 분류 만들기" else "지출 분류 만들기",
            initialLabel = "",
            initialIcon = CategoryIcons.first().first,
            initialColor = CategoryColors.first(),
            onDone = { l, ic, col -> onAddCategory(l, income, ic, col); adding = false },
            onDismiss = { adding = false },
        )
    }
    editing?.let { c ->
        CategoryDialog(
            title = "분류 고치기",
            initialLabel = c.label,
            initialIcon = c.icon ?: CategoryIcons.first().first,
            initialColor = c.color ?: CategoryColors.first(),
            onDone = { l, ic, col -> onUpdateCategory(c.name, l, ic, col); editing = null },
            onDismiss = { editing = null },
        )
    }
    subFor?.let { c ->
        NameDialog("${c.label} 소분류 더하기", "", { onAddSub(c, it); subFor = null }, { subFor = null })
    }
    renaming?.let { s ->
        NameDialog("소분류 이름 바꾸기", s.name, { onRenameSub(s, it); renaming = null }, { renaming = null })
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("'${c.label}'을 지울까요?") },
            text = { Text("이 분류로 적은 기록은 '${if (c.income) Category.OTHER_INCOME.label else Category.ETC.label}'로 옮겨요.") },
            confirmButton = { TextButton(onClick = { onDeleteCategory(c.name); deleting = null; open = null }, color = DonColors.Warn) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(12) }, label = { Text("이름") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onDone(text.trim()) }, color = DonColors.Real) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 직접 만든 분류: 이름 + 아이콘 + 색 */
@Composable
private fun CategoryDialog(
    title: String,
    initialLabel: String,
    initialIcon: String,
    initialColor: Long,
    onDone: (String, String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var icon by remember { mutableStateOf(initialIcon) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it.take(12) }, label = { Text("이름 (예: 반려동물)") }, singleLine = true)
                Text("아이콘", fontSize = 13.sp, color = DonColors.InkMuted)
                CategoryIcons.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (key, res) ->
                            val on = key == icon
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (on) Color(color).copy(alpha = 0.18f) else DonColors.SurfaceAlt)
                                    .selectable(selected = on, role = Role.RadioButton, onClick = { icon = key }),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Icon(painterResource(res), contentDescription = key, modifier = Modifier.size(20.dp), tint = if (on) Color(color) else DonColors.InkMuted)
                            }
                        }
                    }
                }
                Text("색", fontSize = 13.sp, color = DonColors.InkMuted)
                // 대화상자 안에서는 가로 스크롤을 쓰지 않는다 (크기 재기가 끝나지 않음)
                CategoryColors.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { c ->
                            val on = c == color
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                    .border(if (on) 3.dp else 0.dp, if (on) DonColors.Ink else Color.Transparent, CircleShape)
                                    .selectable(selected = on, role = Role.RadioButton, onClick = { color = c }),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (label.isNotBlank()) onDone(label.trim(), icon, color) }, color = DonColors.Real) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 반복 수입 편집용 한 줄 */
private class IncomeRow(r: RecurringIncomeEntity?) {
    val id = r?.id ?: 0L
    val accountId = r?.accountId
    var name by mutableStateOf(r?.name ?: "")
    var amount by mutableStateOf(r?.amount?.toString() ?: "")
    var day by mutableStateOf(r?.day?.toString() ?: "")
    var category by mutableStateOf(r?.category ?: Category.SIDE_INCOME.name)
    fun dayValue(): Int? = day.toIntOrNull()?.takeIf { it in 1..31 }
}

/**
 * 반복 수입 (월급 말고 매달 들어오는 돈): 부수입·용돈·이자 같은 것.
 * 정한 날 가계부에 수입으로 저절로 적고, 입금 알림이 오면 실제 금액·날짜로 고친다. 주 예산은 건드리지 않는다.
 */
@Composable
fun RecurringIncomeScreen(items: List<RecurringIncomeEntity>, onSave: (List<RecurringIncomeEntity>) -> Unit, onBack: () -> Unit) {
    val rows = remember(items) { mutableStateListOf<IncomeRow>().apply { addAll(items.map { IncomeRow(it) }); if (isEmpty()) add(IncomeRow(null)) } }
    val incomeCats = Category.incomes.filter { it != Category.SALARY }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("반복 수입", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "월급 말고 매달 들어오는 돈이에요 (과외비·용돈·적금 이자 등). 정한 날 가계부에 수입으로 적고, 입금 알림이 오면 실제 금액으로 고쳐요. 주 예산은 바뀌지 않아요.",
                fontSize = 13.sp,
                color = DonColors.InkSoft,
            )
            rows.forEachIndexed { i, row ->
                FormCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = row.name, onValueChange = { row.name = it.take(20) }, label = { Text("이름") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = row.day,
                            onValueChange = { row.day = it.filter(Char::isDigit).take(2) },
                            label = { Text("날") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = row.day.isNotEmpty() && row.dayValue() == null,
                            singleLine = true,
                            modifier = Modifier.width(62.dp),
                        )
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { rows.removeAt(i) }
                                .semantics { contentDescription = "이 줄 지우기" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(UiIcon.CLOSE, 18.dp, DonColors.InkMuted) }
                    }
                    MoneyField("금액", row.amount, { row.amount = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        incomeCats.forEach { c -> FilterChip(selected = row.category == c.name, onClick = { row.category = c.name }, label = { Text(c.label) }) }
                    }
                }
            }
            TextButton(onClick = { rows.add(IncomeRow(null)) }, color = DonColors.Real) { Text("+ 반복 수입 추가") }
            Spacer(Modifier.height(24.dp))
        }
        Row(Modifier.fillMaxWidth().background(DonColors.Surface).padding(16.dp)) {
            Button(
                onClick = {
                    onSave(
                        rows.mapNotNull { r ->
                            val a = r.amount.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                            val d = r.dayValue() ?: return@mapNotNull null
                            RecurringIncomeEntity(id = r.id, name = r.name.ifBlank { "반복 수입" }, amount = a, day = d, accountId = r.accountId, category = r.category)
                        },
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }
}
