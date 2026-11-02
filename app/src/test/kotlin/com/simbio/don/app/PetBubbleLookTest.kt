package com.simbio.don.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.ui.PetAct
import com.simbio.don.app.ui.bubbleAlpha
import com.simbio.don.app.ui.drawPetBubble
import com.simbio.don.app.ui.kit.PetRenderer
import com.simbio.don.app.ui.petPoseAt
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.PetBubble
import com.simbio.don.game.PetEnergy
import com.simbio.don.game.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 0.25: 방 안 동물 — 튀지 않고 걷기, 말풍선 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PetBubbleLookTest {
    @Test
    fun `잘 때는 제자리, 깨어 있으면 걷고 멈추고 두리번거린다`() {
        val sleep = (0L..60_000L step 500).map { petPoseAt(it, PetEnergy.SLEEP) }.toSet()
        assertEquals(1, sleep.size)
        val day = (0L..24_000L step 100).map { petPoseAt(it, PetEnergy.NORMAL) }
        assertTrue(day.any { it.act == PetAct.TROT })
        assertTrue(day.any { it.act == PetAct.SNIFF })
        assertTrue(day.any { it.faceLeft } && day.any { !it.faceLeft })
        // 한 바퀴 돌면 끊김 없이 이어진다 (순간 이동 없음)
        day.zipWithNext().forEach { (a, b) -> assertTrue("${a.x} → ${b.x}", kotlin.math.abs(a.x - b.x) < 3f) }
        assertEquals(1f, bubbleAlpha(123, PetBubble.ZZZ))
        assertEquals(0f, bubbleAlpha(500, PetBubble.HEART))
        assertEquals(1f, bubbleAlpha(3_000, PetBubble.HEART))
    }

    @Test
    fun `말풍선 그림`() {
        val u = 8f
        val cell = 200
        val kinds = PetBubble.entries
        val img = ImageBitmap(cell * kinds.size, cell)
        val pet = PetRenderer.bitmap(ApplicationProvider.getApplicationContext(), CharacterLook.pet(Species.POODLE, "검정", "콩이"), "calm")
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) {
            drawRect(Color(0xFFF3EBDD))
            kinds.forEachIndexed { i, k ->
                val x = i * cell
                drawImage(pet, IntOffset.Zero, IntSize(pet.width, pet.height), IntOffset(x + 40, 80), IntSize(110, 110 * pet.height / pet.width))
                drawPetBubble(k, Offset(x + 100f, 90f), u, 1f, 1_200)
            }
        }
        val dir = System.getProperty("screenshotDir") ?: return
        File(dir).apply { mkdirs() }.resolve("p4_pet_bubbles.png").outputStream().use {
            img.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
