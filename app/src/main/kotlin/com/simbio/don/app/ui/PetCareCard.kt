package com.simbio.don.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.PetPortrait
import com.simbio.don.app.ui.kit.PixelImage
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.kit.pressable
import com.simbio.don.app.ui.pixel.ProductIcons
import com.simbio.don.game.CareAction
import com.simbio.don.game.CareAnim
import com.simbio.don.game.CareGroup
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.PetCare
import com.simbio.don.game.PetState
import java.time.LocalDateTime

/**
 * 집 친구 돌보기: 배부름·기분·청결, 그리고 밥·간식·놀이·돌봄 네 묶음 버튼.
 * 묶음을 누르면 그 안의 행동(가진 사료·간식·장난감·미용 용품마다 하나씩)이 펼쳐지고, 누르면 바로 해 준다.
 * 용품은 모의 쇼핑(반려동물 용품)에서 산다 — 진짜 돈은 안 나간다.
 */
@Composable
fun PetCareCard(
    look: CharacterLook,
    pet: PetState,
    now: LocalDateTime,
    onAct: (CareAction) -> Unit,
    onShop: () -> Unit,
    onChange: () -> Unit = {},
    /** 방금 해 준 것 (초상화가 먹기·놀기·기뻐하기로 반응) */
    reaction: Pair<CareAnim, Int>? = null,
) {
    val name = look.callName
    val kind = look.kind.kind
    var group by rememberSaveable { mutableStateOf<CareGroup?>(null) }
    val actions = remember(pet, kind) { PetCare.actions(pet, kind) }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$name 돌보기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
            Text(
                PetCare.line(name, pet),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (pet.hungry || pet.bored || pet.dirty) DonColors.Warn else DonColors.Good,
            )
        }
        // 초상화: 평소엔 기본 얼굴로 숨 쉬고 꼬리 치고 눈 깜빡, 돌봐 주면 먹거나 놀거나 기뻐한다
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PetPortrait(look, 104.dp, reaction)
        }
        Meter("배부름", pet.full, if (pet.hungry) DonColors.Warn else DonColors.Good)
        Meter("기분", pet.joy, if (pet.bored) DonColors.Warn else DonColors.Game)
        Meter("청결", pet.clean, if (pet.dirty) DonColors.Warn else Color(0xFF3FA7D6))

        // 묶음 버튼 (도트 아이콘)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CareGroup.entries.forEach { g ->
                val list = actions.filter { it.group == g }
                val ready = list.count { PetCare.blocked(pet, it, now) == null }
                GroupButton(g, selected = group == g, ready = ready, modifier = Modifier.weight(1f)) { group = if (group == g) null else g }
            }
        }
        AnimatedContent(group, label = "careGroup") { g ->
            if (g == null) {
                Text("묶음을 누르면 해 줄 수 있는 것들이 나와요.", fontSize = 12.sp, color = DonColors.InkMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.filter { it.group == g }.forEach { a -> ActionRow(a, pet, now) { onAct(a) } }
                    Text(
                        when (g) {
                            CareGroup.FOOD -> "사료마다 든든한 정도가 달라요. 배부르면 안 먹어요."
                            CareGroup.TREAT -> "간식마다 좋아하는 정도가 달라요. 30분에 한 번씩."
                            CareGroup.PLAY -> "장난감마다 다른 놀이가 생겨요. 놀고 나면 배가 조금 고파요."
                            CareGroup.CARE -> "미용 용품마다 다른 돌봄이 생겨요. 목욕은 싫어해도 깨끗해져요."
                        },
                        fontSize = 11.sp,
                        color = DonColors.InkMuted,
                    )
                }
            }
        }
        SubButton(onClick = onShop, modifier = Modifier.fillMaxWidth()) { Text("반려동물 용품 사러 가기") }
        SubButton(onClick = onChange, modifier = Modifier.fillMaxWidth(), containerColor = DonColors.SurfaceAlt) { Text("친구 바꾸기 · 털색·이름") }
        Text(
            "시간이 지나면 배고파지고 심심해지고 꼬질꼬질해져요 (잘 때는 천천히). 용품은 모의 지갑으로 사요 — 진짜 돈은 안 나가요.",
            fontSize = 11.sp,
            color = DonColors.InkMuted,
        )
    }
}

@Composable
private fun GroupButton(g: CareGroup, selected: Boolean, ready: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .card(if (selected) DonColors.GameSurface else DonColors.SurfaceAlt, 14.dp)
            .border(if (selected) 2.dp else 0.dp, if (selected) DonColors.Game else Color.Transparent, shape)
            .pressable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PixelImage(ProductIcons.byId.getValue(g.icon), 36.dp, contentDescription = g.label)
        Text(g.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) DonColors.GameDeep else DonColors.Ink)
        Text(if (ready > 0) "할 수 있는 것 $ready" else "쉬는 중", fontSize = 10.sp, color = DonColors.InkMuted)
    }
}

@Composable
private fun ActionRow(a: CareAction, pet: PetState, now: LocalDateTime, onClick: () -> Unit) {
    val why = PetCare.blocked(pet, a, now)
    val ok = why == null
    Row(
        Modifier.fillMaxWidth()
            .card(if (ok) DonColors.Surface else DonColors.SurfaceAlt, 12.dp)
            .border(1.dp, DonColors.Line, RoundedCornerShape(12.dp))
            .pressable(enabled = ok, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PixelImage(ProductIcons.byId[a.icon] ?: ProductIcons.byId.getValue("care_hand"), 28.dp, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(a.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (ok) DonColors.Ink else DonColors.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(a.effect.ifBlank { null }, a.left(pet)?.let { "남은 $it" }).joinToString(" · "),
                fontSize = 11.sp,
                color = DonColors.InkMuted,
            )
        }
        Text(
            why ?: "해 주기",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) DonColors.Game else DonColors.InkMuted,
        )
    }
}

@Composable
private fun Meter(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = DonColors.InkSoft, modifier = Modifier.width(48.dp))
        Column(Modifier.weight(1f)) { Progress(value / 100f, color, 10.dp) }
        Text("$value", fontSize = 12.sp, color = DonColors.InkMuted, modifier = Modifier.padding(start = 8.dp).width(28.dp))
    }
}
