package com.simbio.don.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simbio.don.app.R
import com.simbio.don.app.ui.kit.pixelPanel

/** 갈무리11 — 한글 도트 글꼴 (SIL OFL 1.1, assets/licenses/galmuri_OFL.txt) */
val PixelFont = FontFamily(
    Font(R.font.galmuri11, FontWeight.Normal),
    Font(R.font.galmuri11_bold, FontWeight.Bold),
)

/**
 * 색. 화면 전체는 종이색 바탕 + 짙은 자두색 외곽선의 도트 패널로 통일하고,
 * 현실 돈(원)은 파랑 패널, 게임(마일·집)은 보라 패널로 나눈다. 두 영역이 섞여 보이면 안 된다.
 */
object DonColors {
    val Paper = Color(0xFFF7F1E3)
    val PaperLight = Color(0xFFFFFCF5)
    val Ink = Color(0xFF2B2234)
    val InkSoft = Color(0xFF6B5E66)
    val InkMuted = Color(0xFF9A8E93)
    val Line = Color(0xFFD9CFC0)

    /** 현실 돈 */
    val Real = Color(0xFF3E64B0)
    val RealDeep = Color(0xFF2B3F73)
    val RealSurface = Color(0xFFE6EEF9)

    /** 게임 */
    val Game = Color(0xFF7A54C7)
    val GameDeep = Color(0xFF4E3486)
    val GameSurface = Color(0xFFF1E8FB)

    /** 집 (나무) */
    val Wood = Color(0xFF94603A)
    val WoodSurface = Color(0xFFF6E3C8)

    /** 선택·강조 (칩, 탭) */
    val Highlight = Color(0xFFFFD86B)

    val Warn = Color(0xFFD9573B)
    val WarnSurface = Color(0xFFFFE6D6)
    val Good = Color(0xFF3E9A54)
}

private fun Typography.withFont(f: FontFamily): Typography {
    // 줄 높이를 모두 글자 크기 비례(em)로: 큰 글자가 두 줄이 돼도 겹치지 않고,
    // 머티리얼 입력창이 스타일 사이를 섞을 때 단위가 같아야 한다
    fun TextStyle.g() = copy(fontFamily = f, lineHeight = 1.4.em)
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
            secondaryContainer = DonColors.Highlight,
            onSecondaryContainer = DonColors.Ink,
            tertiary = DonColors.Wood,
            background = DonColors.Paper,
            onBackground = DonColors.Ink,
            surface = DonColors.Paper,
            onSurface = DonColors.Ink,
            surfaceVariant = DonColors.PaperLight,
            onSurfaceVariant = DonColors.InkSoft,
            surfaceContainer = DonColors.PaperLight,
            surfaceContainerLow = DonColors.PaperLight,
            surfaceContainerHigh = DonColors.PaperLight,
            surfaceContainerHighest = DonColors.PaperLight,
            surfaceContainerLowest = Color.White,
            outline = DonColors.InkSoft,
            outlineVariant = DonColors.Line,
            error = DonColors.Warn,
        ),
        typography = Typography().withFont(PixelFont),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(0.dp),
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(2.dp),
            extraLarge = RoundedCornerShape(2.dp),
        ),
        content = content,
    )
}

/** 패널 제목 줄: 작은 도트 아이콘 + 제목 */
@Composable
fun PanelTitle(title: String, color: Color, icon: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon?.invoke()
        Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** 실제 내 돈 영역 (파랑 패널). */
@Composable
fun RealCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().pixelPanel(DonColors.RealSurface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelTitle(title, DonColors.RealDeep, icon)
        content()
    }
}

/** 게임 영역 (보라 패널). */
@Composable
fun GameCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().pixelPanel(DonColors.GameSurface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelTitle(title, DonColors.GameDeep, icon)
        content()
    }
}

/** 화면 제목 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(2.dp))
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
    }
}
