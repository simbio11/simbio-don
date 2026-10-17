package com.simbio.don.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.card

/**
 * 색. 밝은 회색 바탕 위에 흰 카드를 올리는 쇼핑앱식 화면.
 * 현실 돈(원)은 파랑, 게임(모의 지갑·집)은 보라 카드로 나눈다. 두 영역이 섞여 보이면 안 된다.
 */
object DonColors {
    /** 화면 바탕 */
    val Bg = Color(0xFFF2F4F6)

    /** 카드 */
    val Surface = Color(0xFFFFFFFF)

    /** 입력칸·보조 버튼 바탕 */
    val SurfaceAlt = Color(0xFFF2F4F6)

    val Ink = Color(0xFF191F28)
    val InkSoft = Color(0xFF4E5968)
    val InkMuted = Color(0xFF8B95A1)
    val InkFaint = Color(0xFFB0B8C1)
    val Line = Color(0xFFE5E8EB)

    /** 현실 돈 */
    val Real = Color(0xFF346AFF)
    val RealDeep = Color(0xFF1F4FD8)
    val RealSurface = Color(0xFFEDF2FF)

    /** 게임 */
    val Game = Color(0xFF7B61FF)
    val GameDeep = Color(0xFF5A3FE0)
    val GameSurface = Color(0xFFF4F1FF)

    /** 별·뱃지 */
    val Highlight = Color(0xFFFFB800)

    val Warn = Color(0xFFF04452)
    val WarnSurface = Color(0xFFFFF0F1)
    val Good = Color(0xFF14A06A)
    val GoodSurface = Color(0xFFE8F7F0)
}

private fun Typography.tuned(): Typography {
    // 줄 높이를 모두 글자 크기 비례(em)로: 큰 글자가 두 줄이 돼도 겹치지 않고,
    // 머티리얼 입력창이 스타일 사이를 섞을 때 단위가 같아야 한다
    fun TextStyle.g() = copy(lineHeight = 1.4.em, letterSpacing = (-0.01).em)
    return copy(
        displayLarge = displayLarge.g(), displayMedium = displayMedium.g(), displaySmall = displaySmall.g(),
        headlineLarge = headlineLarge.g(), headlineMedium = headlineMedium.g(), headlineSmall = headlineSmall.g(),
        titleLarge = titleLarge.g(), titleMedium = titleMedium.g(), titleSmall = titleSmall.g(),
        bodyLarge = bodyLarge.g().copy(fontSize = 15.sp),
        bodyMedium = bodyMedium.g(), bodySmall = bodySmall.g(),
        labelLarge = labelLarge.g(), labelMedium = labelMedium.g(), labelSmall = labelSmall.g(),
    )
}

@Composable
fun DonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DonColors.Real,
            onPrimary = Color.White,
            primaryContainer = DonColors.RealSurface,
            onPrimaryContainer = DonColors.RealDeep,
            secondary = DonColors.Game,
            onSecondary = Color.White,
            secondaryContainer = DonColors.GameSurface,
            onSecondaryContainer = DonColors.GameDeep,
            background = DonColors.Bg,
            onBackground = DonColors.Ink,
            surface = DonColors.Surface,
            onSurface = DonColors.Ink,
            surfaceVariant = DonColors.SurfaceAlt,
            onSurfaceVariant = DonColors.InkSoft,
            surfaceContainer = DonColors.Surface,
            surfaceContainerLow = DonColors.Surface,
            surfaceContainerHigh = DonColors.Surface,
            surfaceContainerHighest = DonColors.SurfaceAlt,
            surfaceContainerLowest = Color.White,
            outline = DonColors.InkFaint,
            outlineVariant = DonColors.Line,
            error = DonColors.Warn,
        ),
        typography = Typography().tuned(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

/** 카드 제목 줄: (선택) 아이콘 + 제목 + 오른쪽 끝 무엇이든 */
@Composable
fun PanelTitle(
    title: String,
    color: Color = DonColors.Ink,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            it()
            Spacer(Modifier.width(6.dp))
        }
        Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 실제 내 돈 영역 (흰 카드 + 파랑 글자) */
@Composable
fun RealCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelTitle(title, DonColors.Ink, icon, trailing)
        content()
    }
}

/** 게임 영역 (옅은 보라 카드) */
@Composable
fun GameCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().card(DonColors.GameSurface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelTitle(title, DonColors.GameDeep, icon, trailing)
        content()
    }
}

/** 화면 제목 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 앱 이름 "돈't" — 't만 파랑 */
@Composable
fun AppName(fontSize: androidx.compose.ui.unit.TextUnit = 22.sp, modifier: Modifier = Modifier) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append("돈")
            pushStyle(androidx.compose.ui.text.SpanStyle(color = DonColors.Real))
            append("'t")
            pop()
        },
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        color = DonColors.Ink,
        modifier = modifier,
    )
}

/** 한 줄 소개 */
const val TAGLINE = "쓰는 척은 마음껏, 진짜 돈은 차곡차곡"
