package com.simbio.don.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card

/** 홈 카드 (알림 띠는 늘 위에, 이 카드들은 순서를 바꾸고 숨길 수 있다) */
enum class HomeCard(val label: String, val description: String) {
    WEEK("이번 주 남은 돈", "이번 주 남은 돈과 오늘 쓸 수 있는 돈"),
    FORECAST("이대로 쓰면", "월말에 얼마 남는지 예측"),
    CALENDAR("이번 달 달력", "날마다 쓴 돈·무지출 도장·계획"),
    UPCOMING("일주일 안에", "곧 나갈 고정비·계획"),
    TILES("이번 달 요약", "지출·수입·아낀 돈 세 칸"),
    SAVED("모은 돈", "모은 돈·목표·자산"),
    ROOM("내 방", "캐릭터와 모의 지갑"),
    INSIGHTS("한 줄 분석", "지난주와 견준 씀씀이"),
    SUBSCRIPTIONS("정기 결제 찾기", "매달 나가는 것 같은 결제"),
    CATEGORIES("카테고리", "예산 주의·많이 쓴 카테고리"),
    RECENT("최근 내역", "최근 기록 다섯 건"),
}

/** 홈 카드 차례와 숨긴 카드 */
data class HomeLayout(val order: List<HomeCard>, val hidden: Set<HomeCard>) {
    val shown: List<HomeCard> get() = order.filter { it !in hidden }

    fun move(card: HomeCard, by: Int): HomeLayout {
        val i = order.indexOf(card)
        val j = (i + by).coerceIn(0, order.lastIndex)
        if (i < 0 || i == j) return this
        val list = order.toMutableList().apply { add(j, removeAt(i)) }
        return copy(order = list)
    }

    fun toggle(card: HomeCard): HomeLayout = copy(hidden = if (card in hidden) hidden - card else hidden + card)

    fun encode(): String = order.joinToString(",") { (if (it in hidden) "-" else "") + it.name }

    companion object {
        val Default = HomeLayout(HomeCard.entries, emptySet())

        /** 저장된 글 → 차례. 모르는 이름은 빼고, 새로 생긴 카드는 끝에 보이게 */
        fun decode(s: String?): HomeLayout {
            if (s.isNullOrBlank()) return Default
            val order = mutableListOf<HomeCard>()
            val hidden = mutableSetOf<HomeCard>()
            s.split(',').forEach { t ->
                val off = t.startsWith("-")
                val c = HomeCard.entries.firstOrNull { it.name == t.removePrefix("-") } ?: return@forEach
                if (c in order) return@forEach
                order += c
                if (off) hidden += c
            }
            HomeCard.entries.filter { it !in order }.forEach { order += it }
            return HomeLayout(order, hidden)
        }
    }
}

/** 지금 홈 카드 차례 (앱이 켜질 때 읽고, 홈 꾸미기에서 바꾸면 저장한다) */
object HomeCards {
    var layout by mutableStateOf(HomeLayout.Default)

    private fun prefs(c: Context) = c.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun load(c: Context) {
        layout = HomeLayout.decode(prefs(c).getString("home_cards", null))
    }

    fun save(c: Context, l: HomeLayout) {
        layout = l
        prefs(c).edit().putString("home_cards", l.encode()).apply()
    }
}

/** 홈 꾸미기: 카드마다 보이기 스위치와 위·아래 */
@Composable
fun HomeCardsScreen(layout: HomeLayout, onChange: (HomeLayout) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("홈 꾸미기", onBack, trailing = {
            TextButton(onClick = { onChange(HomeLayout.Default) }) { Text("처음대로") }
        })
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("보고 싶은 카드만 켜고, 화살표로 차례를 바꿔요. 알림 띠(결산·월급·점검)는 늘 맨 위에 나와요.", fontSize = 13.sp, color = DonColors.InkSoft)
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 12.dp, vertical = 6.dp)) {
                layout.order.forEachIndexed { i, c ->
                    if (i > 0) Divider()
                    val on = c !in layout.hidden
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (on) DonColors.Ink else DonColors.InkFaint)
                            Text(c.description, fontSize = 12.sp, color = DonColors.InkMuted)
                        }
                        RoundIconButton(UiIcon.CHEVRON_UP, "${c.label} 위로", i > 0) { onChange(layout.move(c, -1)) }
                        RoundIconButton(UiIcon.CHEVRON_DOWN, "${c.label} 아래로", i < layout.order.lastIndex) { onChange(layout.move(c, 1)) }
                        Spacer(Modifier.width(4.dp))
                        Switch(checked = on, onCheckedChange = { onChange(layout.toggle(c)) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
