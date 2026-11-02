package com.simbio.don.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.game.CareKind
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.PetCare
import com.simbio.don.game.PetState
import java.time.LocalDate

/**
 * 집 친구 돌보기: 배부름·기분, 남은 사료·간식, 밥 주기·간식·놀아 주기·산책·쓰다듬기.
 * 용품은 모의 쇼핑(반려동물 용품)에서 산다 — 사료·간식은 바로 와서 쌓인다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetCareCard(look: CharacterLook, pet: PetState, today: LocalDate, onAct: (PetCare.Action) -> Unit, onShop: () -> Unit, onChange: () -> Unit = {}) {
    val name = look.callName
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$name 돌보기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
            Text(
                PetCare.line(name, pet),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (pet.hungry || pet.bored) DonColors.Warn else DonColors.Good,
            )
        }
        Meter("배부름", pet.full, if (pet.hungry) DonColors.Warn else DonColors.Good)
        Meter("기분", pet.joy, if (pet.bored) DonColors.Warn else DonColors.Game)
        Text(
            listOf(
                "사료 ${pet.food}번",
                "간식 ${pet.treats}개",
                if (CareKind.TOY in pet.gear) "장난감 있음" else null,
                if (CareKind.BED in pet.gear) "잠자리 있음" else null,
                if (CareKind.HARNESS in pet.gear) "하네스 있음" else null,
                if (CareKind.FEEDER in pet.gear) "자동 급식기" else null,
            ).filterNotNull().joinToString(" · "),
            fontSize = 12.sp,
            color = DonColors.InkSoft,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PetCare.Action.entries.forEach { a ->
                val why = PetCare.blocked(pet, a, today)
                if (why == null) {
                    SmallButton(onClick = { onAct(a) }) { Text(a.label) }
                } else {
                    SubButton(onClick = { onAct(a) }, containerColor = DonColors.SurfaceAlt) { Text("${a.label} · $why", fontSize = 12.sp, color = DonColors.InkMuted) }
                }
            }
        }
        if (pet.food == 0 || pet.treats == 0 || CareKind.TOY !in pet.gear) {
            SubButton(onClick = onShop, modifier = Modifier.fillMaxWidth()) { Text("반려동물 용품 사러 가기") }
        }
        SubButton(onClick = onChange, modifier = Modifier.fillMaxWidth(), containerColor = DonColors.SurfaceAlt) { Text("친구 바꾸기 · 털색·소품·이름") }
        Text(
            "날마다 배고파지고 심심해져요. 용품은 모의 지갑(아낀 돈)으로 사요 — 진짜 돈은 안 나가요.",
            fontSize = 11.sp,
            color = DonColors.InkMuted,
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
