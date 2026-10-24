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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.card

/** 한 벌의 색 (밝게 · 어둡게) */
class DonPalette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val line: Color,
    val onInk: Color,
    val real: Color,
    val realDeep: Color,
    val realSurface: Color,
    val game: Color,
    val gameDeep: Color,
    val gameSurface: Color,
    val highlight: Color,
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warn: Color,
    val warnSurface: Color,
    val good: Color,
    val goodSurface: Color,
    val fire: Color,
    val fireSurface: Color,
    val track: Color,
) {
    companion object {
        val Light = DonPalette(
            dark = false,
            bg = Color(0xFFF2F4F6), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF2F4F6),
            ink = Color(0xFF191F28), inkSoft = Color(0xFF4E5968), inkMuted = Color(0xFF8B95A1), inkFaint = Color(0xFFB0B8C1),
            line = Color(0xFFE5E8EB), onInk = Color(0xFFFFFFFF),
            real = Color(0xFF346AFF), realDeep = Color(0xFF1F4FD8), realSurface = Color(0xFFEDF2FF),
            game = Color(0xFF7B61FF), gameDeep = Color(0xFF5A3FE0), gameSurface = Color(0xFFF4F1FF),
            highlight = Color(0xFFFFB800),
            income = Color(0xFF346AFF), expense = Color(0xFFE5484D), transfer = Color(0xFF8B95A1),
            warn = Color(0xFFF04452), warnSurface = Color(0xFFFFF0F1), good = Color(0xFF14A06A), goodSurface = Color(0xFFE8F7F0),
            fire = Color(0xFFE8590C), fireSurface = Color(0xFFFFF1E6), track = Color(0xFFF0F2F4),
        )

        /** 어두운 화면: 바탕은 거의 검정, 카드는 한 단계 밝게. 글자는 흰색 대신 살짝 회색으로 눈부심을 줄인다 */
        val Dark = DonPalette(
            dark = true,
            bg = Color(0xFF101114), surface = Color(0xFF1C1E23), surfaceAlt = Color(0xFF272A30),
            ink = Color(0xFFECEEF1), inkSoft = Color(0xFFB9C0C9), inkMuted = Color(0xFF8B95A1), inkFaint = Color(0xFF5D6570),
            line = Color(0xFF30343B), onInk = Color(0xFF191F28),
            real = Color(0xFF4F83FF), realDeep = Color(0xFF9CB9FF), realSurface = Color(0xFF1A2442),
            game = Color(0xFF8E78FF), gameDeep = Color(0xFFBBAEFF), gameSurface = Color(0xFF221E36),
            highlight = Color(0xFFFFC533),
            income = Color(0xFF5B8DFF), expense = Color(0xFFFF6369), transfer = Color(0xFF8B95A1),
            warn = Color(0xFFFF5C68), warnSurface = Color(0xFF3A1D22), good = Color(0xFF2DBE84), goodSurface = Color(0xFF14302A),
            fire = Color(0xFFFF8A4C), fireSurface = Color(0xFF3A2516), track = Color(0xFF2A2D33),
        )
    }
}

/** 화면 색 모드 (설정) */
enum class ThemeMode(val label: String) { SYSTEM("폰 설정 따라"), LIGHT("밝게"), DARK("어둡게") }

/**
 * 색. 밝은 회색 바탕 위에 흰 카드를 올리는 쇼핑앱식 화면 (어둡게 모드에서는 검정 바탕 위 짙은 회색 카드).
 * 현실 돈(원)은 파랑, 게임(모의 지갑·집)은 보라 카드로 나눈다. 두 영역이 섞여 보이면 안 된다.
 * 지금 색 벌은 [palette] — 앱이 켜질 때와 설정을 바꿀 때 바꾸고, 읽는 화면은 저절로 다시 그려진다.
 */
object DonColors {
    var palette by mutableStateOf(DonPalette.Light)

    val dark: Boolean get() = palette.dark

    /** 화면 바탕 */
    val Bg: Color get() = palette.bg

    /** 카드 */
    val Surface: Color get() = palette.surface

    /** 입력칸·보조 버튼 바탕 */
    val SurfaceAlt: Color get() = palette.surfaceAlt

    val Ink: Color get() = palette.ink
    val InkSoft: Color get() = palette.inkSoft
    val InkMuted: Color get() = palette.inkMuted
    val InkFaint: Color get() = palette.inkFaint
    val Line: Color get() = palette.line

    /** [Ink]를 바탕으로 쓸 때 그 위 글자 (밝게: 흰색, 어둡게: 검정) */
    val OnInk: Color get() = palette.onInk

    /** 현실 돈 */
    val Real: Color get() = palette.real
    val RealDeep: Color get() = palette.realDeep
    val RealSurface: Color get() = palette.realSurface

    /** 게임 */
    val Game: Color get() = palette.game
    val GameDeep: Color get() = palette.gameDeep
    val GameSurface: Color get() = palette.gameSurface

    /** 별·뱃지 */
    val Highlight: Color get() = palette.highlight

    /** 가계부 금액: 수입 파랑, 지출 빨강, 이체 회색 (가계부 앱 관례) */
    val Income: Color get() = palette.income
    val Expense: Color get() = palette.expense
    val Transfer: Color get() = palette.transfer

    val Warn: Color get() = palette.warn
    val WarnSurface: Color get() = palette.warnSurface
    val Good: Color get() = palette.good
    val GoodSurface: Color get() = palette.goodSurface

    /** 연속 기록 불꽃 */
    val Fire: Color get() = palette.fire
    val FireSurface: Color get() = palette.fireSurface

    /** 막대 바탕·눈금 */
    val Track: Color get() = palette.track

    /** 스낵바 (두 모드 모두 짙은 바탕) */
    val Toast: Color get() = if (palette.dark) Color(0xFF3A3F47) else Color(0xFF191F28)
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
    val scheme = if (DonColors.dark) darkColorScheme(
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
        surfaceContainerHigh = DonColors.SurfaceAlt,
        surfaceContainerHighest = DonColors.SurfaceAlt,
        surfaceContainerLowest = DonColors.Bg,
        outline = DonColors.InkFaint,
        outlineVariant = DonColors.Line,
        error = DonColors.Warn,
        inverseSurface = DonColors.Ink,
        inverseOnSurface = DonColors.OnInk,
    ) else lightColorScheme(
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
        )
    MaterialTheme(
        colorScheme = scheme,
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
