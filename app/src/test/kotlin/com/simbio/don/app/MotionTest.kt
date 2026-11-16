package com.simbio.don.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.DonColors
import com.simbio.don.app.ui.kit.Burst
import com.simbio.don.app.ui.kit.Motion
import com.simbio.don.app.ui.kit.PillTabs
import com.simbio.don.app.ui.kit.RollingText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 0.33 움직임: 굴러가는 금액은 글자 전체로 읽히고, 알약 탭은 눌러 고르고, 동전 터짐은 애니메이션을 끄면 안 나온다 */
@RunWith(RobolectricTestRunner::class)
class MotionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `굴러가는 금액은 바뀐 뒤에도 전체 글자로 찾힌다`() {
        var v by mutableStateOf("12,000원")
        rule.setContent { RollingText(v, 24.sp, DonColors.Ink) }
        rule.onNodeWithText("12,000원").assertExists()
        v = "9,500원"
        rule.waitForIdle()
        rule.onNodeWithText("9,500원").assertExists()
    }

    @Test
    fun `알약 탭 — 누른 칸이 골라진다`() {
        rule.setContent {
            var s by remember { mutableIntStateOf(0) }
            PillTabs(listOf("수입", "지출", "이체"), s, { s = it })
        }
        rule.onNodeWithText("이체").performClick()
        rule.onNodeWithText("이체").assertIsSelected()
    }

    @Test
    fun `동전 터짐은 폰 애니메이션을 끄면 안 나온다`() {
        val before = Burst.count
        Motion.reduced = true
        Burst.play()
        assertEquals(before, Burst.count)
        Motion.reduced = false
        Burst.play()
        assertEquals(before + 1, Burst.count)
    }
}
