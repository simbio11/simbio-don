package com.simbio.don.app

import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.ui.kit.CharacterRenderer
import com.simbio.don.app.ui.kit.Pose
import com.simbio.don.app.ui.pixel.CharacterParts
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 꾸미기에서 고를 수 있는 모든 부위가 아틀라스에 있고, 색 바꾸기가 되는지. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CharacterRendererTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** 부위 하나씩 바꿔 본 모든 모습 */
    private fun everyLook(): List<CharacterLook> = buildList {
        for (base in listOf(CharacterLook.MALE, CharacterLook.FEMALE)) {
            add(base)
            CharacterParts.faceShapes.forEach { add(base.copy(face = it.id)) }
            CharacterParts.eyeStyles.forEach { add(base.copy(eyes = it.id)); add(base.copy(eyes = it.id, lashes = !base.lashes)) }
            CharacterParts.browStyles.forEach { add(base.copy(brow = it.id)) }
            CharacterParts.noses.forEach { add(base.copy(nose = it.id)) }
            CharacterParts.mouths.forEach { add(base.copy(mouth = it.id)) }
            CharacterParts.hairs.forEach { add(base.copy(hair = it.id)) }
            CharacterParts.tops.forEach { add(base.copy(top = it.id)) }
            CharacterParts.bottoms.forEach { add(base.copy(bottom = it.id)) }
            CharacterParts.shoes.forEach { add(base.copy(shoes = it.id)) }
            CharacterParts.hats.forEach { add(base.copy(hat = it.id)) }
            CharacterParts.accs.forEach { add(base.copy(acc = it.id)) }
        }
    }

    @Test
    fun 모든_부위와_표정이_아틀라스에_있다() {
        val missing = mutableSetOf<String>()
        for (look in everyLook()) {
            for (mood in Mood.entries) {
                for (blink in listOf(false, true)) {
                    CharacterRenderer.layers(look, mood, blink).filter { it !in CharacterParts.index }.forEach { missing += it }
                }
            }
        }
        assertTrue("아틀라스에 없는 부위: $missing", missing.isEmpty())
    }

    @Test
    fun 원피스는_아래옷을_그리지_않는다() {
        val dress = CharacterLook.FEMALE.copy(top = "dress")
        assertFalse(CharacterRenderer.layers(dress, Mood.CALM, false).any { it.startsWith("bottom:") })
        assertTrue(CharacterRenderer.layers(CharacterLook.FEMALE, Mood.CALM, false).contains("bottom:skirt"))
    }

    @Test
    fun 고른_색으로_바꿔_그리고_표식_색은_남지_않는다() {
        val markers = (
            CharacterParts.SKIN + CharacterParts.HAIR + CharacterParts.CLOTH1 + CharacterParts.CLOTH2 +
                CharacterParts.SHOES + CharacterParts.EYE + CharacterParts.HAT
            ).toSet()
        val look = CharacterLook.MALE.copy(skin = "구릿빛", hairColor = "분홍", topColor = "초록", bottomColor = "검정", shoesColor = "빨강", eyeColor = "파랑", hat = "cap", hatColor = "검정")
        val px = CharacterRenderer.pixels(context, look, Mood.CALM)
        assertEquals(CharacterRenderer.W * CharacterRenderer.H, px.size)
        val left = px.filter { it in markers }.toSet()
        assertTrue("표식 색이 남았어요: ${left.map { Integer.toHexString(it) }}", left.isEmpty())
        val skin = CharacterParts.skinTones.first { it.first == "구릿빛" }.second
        assertTrue(px.any { it == skin[1] })
        assertTrue("빈 그림", px.count { it ushr 24 != 0 } > 200)
    }

    @Test
    fun 표정과_동작마다_그림이_다르다() {
        val stand = CharacterRenderer.pixels(context, CharacterLook.FEMALE, Mood.CALM)
        assertNotEquals(stand.toList(), CharacterRenderer.pixels(context, CharacterLook.FEMALE, Mood.CALM, Pose.BLINK).toList())
        assertNotEquals(stand.toList(), CharacterRenderer.pixels(context, CharacterLook.FEMALE, Mood.CALM, Pose.WALK_LEFT).toList())
        assertNotEquals(stand.toList(), CharacterRenderer.pixels(context, CharacterLook.FEMALE, Mood.SAD).toList())
        assertNotEquals(stand.toList(), CharacterRenderer.pixels(context, CharacterLook.MALE, Mood.CALM).toList())
    }
}
