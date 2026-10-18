package com.simbio.don.app.ui

import com.simbio.don.game.Shop
import com.simbio.don.game.Miles
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CharacterSprite
import com.simbio.don.app.ui.kit.CharacterThumb
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.pixel.CharacterParts
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.Gender
import com.simbio.don.game.Mood

private enum class DressTab(val label: String) {
    FACE("얼굴"), EYES("눈"), BROWS("눈썹"), NOSE_MOUTH("코·입"), HAIR("머리"), CLOTHES("옷"), HAT("모자·소품"),
}

/** 고를 칸 하나: 이름과 그 부위를 바꾼 모습, 옷장에서 사야 하면 마일 값 */
private data class Choice(val label: String, val look: CharacterLook, val miles: Long = 0)

/**
 * 캐릭터 꾸미기 = 옷장 + 뷰티샵. 얼굴형·눈·눈썹·코·입·머리(뷰티샵), 옷·모자·소품(옷장)을 고르고 색을 바꾼다.
 * 위 큰 그림으로 표정마다 어떻게 보이는지 바로 본다.
 *
 * - 옷장: 가진 옷은 공짜로 갈아입고, 없는 옷은 마일로 산다. 색은 언제나 공짜.
 * - 뷰티샵: 얼굴·머리를 바꾸면 시술 값. 모의 지갑으로 예약하면 이번 주 결산 때 예산을 지켜야 바뀌고,
 *   마일로 내면 바로 바뀐다. 첫 꾸미기는 공짜.
 */
@Composable
fun DressScreen(
    current: CharacterLook,
    owned: Set<String> = emptySet(),
    miles: Long = 0,
    wallet: Long = 0,
    freeMakeover: Boolean = true,
    onApply: (CharacterLook, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    var tab by rememberSaveable { mutableStateOf(DressTab.FACE) }
    var mood by remember { mutableStateOf(Mood.CALM) }
    var payWithMiles by remember { mutableStateOf(false) }
    val changed = draft != current
    val procedures = Shop.procedures(current, draft)
    val won = procedures.sumOf { it.won }
    val clothesMiles = Shop.wardrobeNeeded(draft, owned).sumOf { Shop.wardrobeCost(it) }
    val beautyMiles = if (procedures.isEmpty() || freeMakeover) 0 else Miles.ofWon(won)
    val needMiles = clothesMiles + if (payWithMiles) beautyMiles else 0
    val canPay = needMiles <= miles && (procedures.isEmpty() || freeMakeover || payWithMiles || won <= wallet)
    fun cost(key: String) = if (key in owned) 0 else Shop.wardrobeCost(key)

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("옷장 · 뷰티샵", onBack, trailing = { com.simbio.don.app.ui.kit.Badge(miles(miles), DonColors.GameDeep, modifier = Modifier.padding(end = 16.dp)) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().card(DonColors.GameSurface).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CharacterSprite(mood, 168.dp, look = draft)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Mood.entries.forEach { m -> FilterChip(selected = mood == m, onClick = { mood = m }, label = { Text(m.label) }) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DressTab.entries.forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }) }
            }
            when (tab) {
                DressTab.FACE -> {
                    Section("처음 모습") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Gender.entries.forEach { g ->
                                SubButton(onClick = { draft = CharacterLook.default(g) }, modifier = Modifier.weight(1f)) { Text("${g.label} 기본") }
                            }
                        }
                        Text("누르면 그 모습으로 돌아가요. 부위는 남녀 상관없이 고를 수 있어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    }
                    Section("얼굴형") { Choices(CharacterParts.faceShapes.map { Choice(it.label, draft.copy(face = it.id)) }, draft, head = true) { draft = it } }
                    Section("피부색") { Swatches(CharacterParts.skinTones, draft.skin) { draft = draft.copy(skin = it) } }
                }
                DressTab.EYES -> {
                    Section("눈 모양") { Choices(CharacterParts.eyeStyles.map { Choice(it.label, draft.copy(eyes = it.id)) }, draft, head = true) { draft = it } }
                    Section("눈동자 색") { Swatches(CharacterParts.eyeColors, draft.eyeColor, pick = 0) { draft = draft.copy(eyeColor = it) } }
                    Section("속눈썹") {
                        Choices(listOf(Choice("없음", draft.copy(lashes = false)), Choice("있음", draft.copy(lashes = true))), draft, head = true) { draft = it }
                    }
                }
                DressTab.BROWS -> Section("눈썹") {
                    Choices(CharacterParts.browStyles.map { Choice(it.label, draft.copy(brow = it.id)) }, draft, head = true) { draft = it }
                }
                DressTab.NOSE_MOUTH -> {
                    Section("코") { Choices(CharacterParts.noses.map { Choice(it.label, draft.copy(nose = it.id)) }, draft, head = true) { draft = it } }
                    Section("입") { Choices(CharacterParts.mouths.map { Choice(it.label, draft.copy(mouth = it.id)) }, draft, head = true) { draft = it } }
                }
                DressTab.HAIR -> {
                    Section("머리 모양") { Choices(CharacterParts.hairs.map { Choice(it.label, draft.copy(hair = it.id)) }, draft, head = true) { draft = it } }
                    Section("머리 색") { Swatches(CharacterParts.hairColors, draft.hairColor) { draft = draft.copy(hairColor = it) } }
                }
                DressTab.CLOTHES -> {
                    val dress = CharacterParts.tops.firstOrNull { it.id == draft.top }?.flag == true
                    Section("윗옷") { Choices(CharacterParts.tops.map { Choice(it.label, draft.copy(top = it.id), cost("top:${it.id}")) }, draft, head = false) { draft = it } }
                    Section("윗옷 색") { Swatches(CharacterParts.clothColors, draft.topColor) { draft = draft.copy(topColor = it) } }
                    Section("아래옷") {
                        if (dress) {
                            Text("원피스를 입으면 아래옷은 안 보여요.", fontSize = 13.sp, color = DonColors.InkMuted)
                        } else {
                            Choices(CharacterParts.bottoms.map { Choice(it.label, draft.copy(bottom = it.id), cost("bottom:${it.id}")) }, draft, head = false) { draft = it }
                        }
                    }
                    if (!dress) Section("아래옷 색") { Swatches(CharacterParts.clothColors, draft.bottomColor) { draft = draft.copy(bottomColor = it) } }
                    Section("신발") { Choices(CharacterParts.shoes.map { Choice(it.label, draft.copy(shoes = it.id), cost("shoes:${it.id}")) }, draft, head = false) { draft = it } }
                    Section("신발 색") { Swatches(CharacterParts.clothColors, draft.shoesColor) { draft = draft.copy(shoesColor = it) } }
                }
                DressTab.HAT -> {
                    Section("모자") {
                        val hats = listOf(Choice("없음", draft.copy(hat = null))) + CharacterParts.hats.map { Choice(it.label, draft.copy(hat = it.id), cost("hat:${it.id}")) }
                        Choices(hats, draft, head = true) { draft = it }
                    }
                    if (draft.hat != null) Section("모자 색") { Swatches(CharacterParts.hatColors, draft.hatColor) { draft = draft.copy(hatColor = it) } }
                    Section("소품") {
                        val accs = listOf(Choice("없음", draft.copy(acc = null))) + CharacterParts.accs.map { Choice(it.label, draft.copy(acc = it.id), cost("acc:${it.id}")) }
                        Choices(accs, draft, head = true) { draft = it }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.fillMaxWidth().background(DonColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (procedures.isNotEmpty()) {
                Text(
                    "뷰티샵: " + procedures.joinToString(" · ") { "${it.label} ${won(it.won)}" },
                    fontSize = 12.sp,
                    color = DonColors.InkSoft,
                )
                if (freeMakeover) {
                    Text("첫 꾸미기라 공짜예요!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DonColors.Good)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !payWithMiles, onClick = { payWithMiles = false }, label = { Text("모의 지갑 ${won(won)}") })
                        FilterChip(selected = payWithMiles, onClick = { payWithMiles = true }, label = { Text(miles(beautyMiles)) })
                    }
                    Text(
                        if (payWithMiles) "마일로 내면 바로 바뀌어요." else "모의 지갑으로 예약하면 이번 주 결산 때 예산을 지켜야 바뀌어요. 넘기면 취소돼요.",
                        fontSize = 12.sp,
                        color = DonColors.InkMuted,
                    )
                }
            }
            if (clothesMiles > 0) Text("옷장에서 새로 사는 것 ${miles(clothesMiles)}", fontSize = 12.sp, color = DonColors.InkSoft)
            if (changed && !canPay) {
                Text(if (needMiles > miles) "마일이 모자라요 (${miles(miles)} 있음)" else "모의 지갑이 모자라요", fontSize = 12.sp, color = DonColors.Warn)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (changed) SubButton(onClick = { draft = current }) { Text("되돌리기") }
                Button(onClick = { onApply(draft, payWithMiles) }, enabled = changed && canPay, containerColor = DonColors.Game, modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            !changed -> "바뀐 게 없어요"
                            procedures.isNotEmpty() && !freeMakeover && !payWithMiles -> "예약하기"
                            needMiles > 0 -> "${ro(miles(needMiles))} 바꾸기"
                            else -> "이 모습으로 저장"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        content()
    }
}

/** 고를 칸들: 그 부위만 바꾼 미리보기를 4개씩 한 줄로 */
@Composable
private fun Choices(choices: List<Choice>, draft: CharacterLook, head: Boolean, onPick: (CharacterLook) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val selected = c.look == draft
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .background(if (selected) DonColors.GameSurface else DonColors.SurfaceAlt)
                            .border(2.dp, if (selected) DonColors.Game else Color.Transparent, shape)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(c.look) })
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.height(if (head) 56.dp else 72.dp), contentAlignment = Alignment.Center) {
                            CharacterThumb(c.look, if (head) 52.dp else 70.dp, head = head)
                        }
                        Text(
                            c.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) DonColors.GameDeep else DonColors.InkSoft,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        if (c.miles > 0) Text("%,d마일".format(c.miles), fontSize = 10.sp, color = DonColors.Game, fontWeight = FontWeight.SemiBold)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 색 고르기: 동그라미 (묶음의 [pick]번째 색으로 보여준다) */
@Composable
private fun Swatches(colors: List<Pair<String, IntArray>>, selected: String, pick: Int = 1, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { (name, ramp) ->
            val on = name == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .border(if (on) 3.dp else 1.dp, if (on) DonColors.Ink else DonColors.Line, CircleShape)
                        .padding(if (on) 5.dp else 1.dp).clip(CircleShape)
                        .background(Color(ramp[pick.coerceAtMost(ramp.lastIndex)]))
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(name) })
                        .semantics { contentDescription = name },
                )
                Text(name, fontSize = 11.sp, color = if (on) DonColors.Ink else DonColors.InkMuted, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
