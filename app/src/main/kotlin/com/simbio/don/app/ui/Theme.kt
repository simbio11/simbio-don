package com.simbio.don.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 현실 돈(원)과 게임(마일)을 색으로 나눈다.
 * 현실 = 남색 계열, 게임 = 보라 계열. 두 영역이 섞여 보이면 안 된다.
 */
object DonColors {
    val Real = Color(0xFF2B3A67)
    val RealSurface = Color(0xFFEEF1F8)
    val Game = Color(0xFF6B4FA0)
    val GameSurface = Color(0xFFF3EEFA)
    val Warn = Color(0xFFB3541E)
    val Good = Color(0xFF2E7D4F)
}

@Composable
fun DonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = DonColors.Real, secondary = DonColors.Game),
        content = content,
    )
}

/** 실제 내 돈 영역. */
@Composable
fun RealCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DonColors.RealSurface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = DonColors.Real, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            content()
        }
    }
}

/** 게임 영역 (M1부터 채워진다). */
@Composable
fun GameCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DonColors.GameSurface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = DonColors.Game, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            content()
        }
    }
}
