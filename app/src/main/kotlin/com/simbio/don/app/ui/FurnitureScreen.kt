package com.simbio.don.app.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.card
import com.simbio.don.game.HouseLayout
import com.simbio.don.game.HouseLevel
import com.simbio.don.game.HouseSetup
import com.simbio.don.game.Shop

private enum class FurnitureTab(val label: String) { MOVE("이사"), STORAGE("수납 가구"), INTERIOR("인테리어") }

/**
 * 가구점: 이사(더 큰 집), 수납 가구(칸 늘리기), 인테리어(벽지·바닥·러그·커튼·이불 색). 모두 마일로 산다.
 * 인테리어 색은 한 번 사면 언제든 다시 고를 수 있고, 기본 색은 공짜.
 *
 * [themes]는 부분마다 고를 수 있는 색 (첫째가 기본), [preview]는 고르는 중인 인테리어로 그린 방.
 */
@Composable
fun FurnitureScreen(
    house: HouseLayout,
    setup: HouseSetup,
    miles: Long,
    themes: Map<Shop.Interior, List<Pair<String, IntArray>>>,
    preview: @Composable (HouseLevel, Map<Shop.Interior, String>) -> Unit,
    onMove: (HouseLevel) -> Unit,
    onStorage: (com.simbio.don.game.Slot) -> Unit,
    onInterior: (Shop.Interior, String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(FurnitureTab.MOVE) }
    // 고르는 중인 인테리어 (사기 전 미리보기)
    var trying by remember(setup) { mutableStateOf(setup.interior) }
    var showLevel by remember(house.level) { mutableStateOf(house.level) }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("가구점", onBack, trailing = { Badge(miles(miles), DonColors.GameDeep, modifier = Modifier.padding(end = 16.dp)) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().card(DonColors.GameSurface).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
                preview(if (tab == FurnitureTab.MOVE) showLevel else house.level, if (tab == FurnitureTab.INTERIOR) trying else setup.interior)
            }
            SegmentedToggle(FurnitureTab.entries.map { it.label }, tab.ordinal) { tab = FurnitureTab.entries[it]; trying = setup.interior; showLevel = house.level }
            when (tab) {
                FurnitureTab.MOVE -> HouseLevel.entries.forEach { level ->
                    val here = level == house.level
                    val passed = level.ordinal < house.level.ordinal
                    val cost = Shop.moveCost(level)
                    Column(
                        Modifier.fillMaxWidth().card()
                            .border(2.dp, if (showLevel == level) DonColors.Game else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .selectable(selected = showLevel == level, role = Role.RadioButton, onClick = { showLevel = level })
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(level.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
                            when {
                                here -> Badge("지금 집", DonColors.Good)
                                passed -> Badge("지나온 집", DonColors.InkMuted)
                                else -> Badge(miles(cost), DonColors.GameDeep)
                            }
                        }
                        Text("수납 ${level.capacity.values.sum()}칸 (옷장 ${level.capacity[com.simbio.don.game.Slot.WARDROBE]} · 신발장 ${level.capacity[com.simbio.don.game.Slot.SHOE_RACK]} · 책상 ${level.capacity[com.simbio.don.game.Slot.DESK]} · 선반 ${level.capacity[com.simbio.don.game.Slot.SHELF]})", fontSize = 12.sp, color = DonColors.InkSoft)
                        if (!here && !passed) {
                            Button(onClick = { onMove(level) }, enabled = miles >= cost, containerColor = DonColors.Game, modifier = Modifier.fillMaxWidth()) {
                                Text(if (miles >= cost) "${ro(level.label)} 이사하기" else "${miles(cost - miles)} 더 모으면 이사")
                            }
                        }
                    }
                }
                FurnitureTab.STORAGE -> {
                    Text("수납 가구를 들이면 칸이 늘어요. 넘쳐서 바닥에 쌓인 물건이 가구 안으로 들어가요. 이사해도 그대로 가져가요.", fontSize = 13.sp, color = DonColors.InkSoft)
                    Shop.storageSlots.forEach { slot ->
                        val lv = setup.storage[slot] ?: 0
                        val next = lv + 1
                        val used = house.slots[slot].orEmpty().size
                        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.simbio.don.app.ui.kit.PixelImage(storageIcon(slot), 32.dp)
                                Spacer(Modifier.size(10.dp))
                                Text(slot.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
                                Text("${lv}/${Shop.MAX_STORAGE_LEVEL}단계", fontSize = 13.sp, color = DonColors.GameDeep, fontWeight = FontWeight.SemiBold)
                            }
                            Text("${used}/${house.capacityOf(slot)}칸 사용 중", fontSize = 12.sp, color = DonColors.InkSoft)
                            if (next <= Shop.MAX_STORAGE_LEVEL) {
                                val cost = Shop.storageCost(next)
                                Button(onClick = { onStorage(slot) }, enabled = miles >= cost, containerColor = DonColors.Game, modifier = Modifier.fillMaxWidth()) {
                                    Text("+${Shop.storageStep(slot)}칸 · ${miles(cost)}")
                                }
                            } else {
                                Text("가장 큰 가구예요", fontSize = 12.sp, color = DonColors.Good, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                FurnitureTab.INTERIOR -> {
                    Text("색을 눌러 위에서 미리 봐요. 한 번 산 색은 언제든 다시 고를 수 있어요. 기본 색은 공짜.", fontSize = 13.sp, color = DonColors.InkSoft)
                    Shop.Interior.entries.forEach { part ->
                        val options = themes[part].orEmpty()
                        if (options.isEmpty()) return@forEach
                        val default = options.first().first
                        val now = setup.interior[part] ?: default
                        val pick = trying[part] ?: default
                        val owned = pick == default || "${part.name}:$pick" in setup.ownedInterior
                        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.simbio.don.app.ui.kit.PixelImage(interiorIcon(part), 32.dp)
                                Spacer(Modifier.size(10.dp))
                                Text(part.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
                                Text(now, fontSize = 13.sp, color = DonColors.InkSoft)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                options.forEach { (name, ramp) ->
                                    val on = name == pick
                                    val have = name == default || "${part.name}:$name" in setup.ownedInterior
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(40.dp).clip(CircleShape)
                                                .border(if (on) 3.dp else 1.dp, if (on) DonColors.Ink else DonColors.Line, CircleShape)
                                                .padding(if (on) 5.dp else 1.dp).clip(CircleShape)
                                                .background(Color(ramp[ramp.size / 2]))
                                                .selectable(selected = on, role = Role.RadioButton, onClick = { trying = trying + (part to name) })
                                                .semantics { contentDescription = "${part.label} $name" },
                                        )
                                        Text(name, fontSize = 11.sp, color = if (on) DonColors.Ink else DonColors.InkMuted)
                                        if (!have) Text("%,d".format(part.cost), fontSize = 10.sp, color = DonColors.Game)
                                    }
                                }
                            }
                            if (pick != now) {
                                Button(
                                    onClick = { onInterior(part, pick, pick == default) },
                                    enabled = owned || miles >= part.cost,
                                    containerColor = DonColors.Game,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (owned) "${ro(pick)} 바꾸기" else "${ro(miles(part.cost))} $pick 사기") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun storageIcon(slot: com.simbio.don.game.Slot): Int = when (slot) {
    com.simbio.don.game.Slot.WARDROBE -> com.simbio.don.app.R.drawable.px16_wardrobe
    com.simbio.don.game.Slot.SHOE_RACK -> com.simbio.don.app.R.drawable.px16_shoe_rack
    com.simbio.don.game.Slot.DESK -> com.simbio.don.app.R.drawable.px16_desk
    else -> com.simbio.don.app.R.drawable.px16_shelf
}

private fun interiorIcon(part: Shop.Interior): Int = when (part) {
    Shop.Interior.WALL -> com.simbio.don.app.R.drawable.px16_wallpaper
    Shop.Interior.FLOOR -> com.simbio.don.app.R.drawable.px16_floor
    Shop.Interior.RUG -> com.simbio.don.app.R.drawable.px16_rug
    Shop.Interior.CURTAIN -> com.simbio.don.app.R.drawable.px16_curtain
    Shop.Interior.BEDDING -> com.simbio.don.app.R.drawable.px16_bedding
}
