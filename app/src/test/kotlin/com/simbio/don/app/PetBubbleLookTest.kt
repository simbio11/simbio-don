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
    fun `잘 때는 엎드려 제자리, 깨어 있으면 걷고 앉고 두리번거린다`() {
        val sleep = (0L..60_000L step 500).map { petPoseAt(it, PetEnergy.SLEEP) }
        assertTrue(sleep.all { it.act == PetAct.LIE && it.x == sleep.first().x })
        val day = (0L..60_000L step 100).map { petPoseAt(it, PetEnergy.NORMAL) }
        assertTrue(day.any { it.act == PetAct.WALK })
        assertTrue(day.any { it.act == PetAct.SIT })
        assertTrue(day.any { it.act == PetAct.LOOK })
        assertTrue(day.any { it.faceLeft } && day.any { !it.faceLeft })
        // 걸을 때만 움직이고, 한 바퀴 돌아도 끊김 없이 이어진다 (순간 이동 없음)
        day.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.x} → ${b.x}", kotlin.math.abs(a.x - b.x) < 3f)
            if (a.x != b.x) assertTrue(a.act == PetAct.WALK || b.act == PetAct.WALK)
        }
        // 신나면 놀고, 기운 없으면 엎드려 쉰다
        assertTrue((0L..40_000L step 100).any { petPoseAt(it, PetEnergy.HIGH).act == PetAct.PLAY })
        assertTrue((0L..40_000L step 100).any { petPoseAt(it, PetEnergy.LOW).act == PetAct.LIE })
        // 걷는 그림이 없으면 미끄러지지 않고 제자리
        assertEquals(1, (0L..30_000L step 100).map { petPoseAt(it, PetEnergy.NORMAL, canWalk = false).x }.toSet().size)
        // 서 있을 땐 대부분 1번 그림, 가끔 깜빡이고 하품
        val idle = (0L until 6_400L * 3 step 50).map { com.simbio.don.app.ui.idleFrame(it, 4) }
        assertTrue(idle.count { it == 0 } > idle.size / 2)
        assertTrue(2 in idle && 3 in idle)
        assertEquals(1f, bubbleAlpha(123, PetBubble.ZZZ))
        assertEquals(0f, bubbleAlpha(500, PetBubble.HEART))
        assertEquals(1f, bubbleAlpha(3_000, PetBubble.HEART))
    }

    @Test
    fun `모든 품종 동작 그림 읽기 — 걷기는 다 있고, 같은 크기 단위`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Species.pets.forEach { sp ->
            val f = com.simbio.don.app.ui.kit.PetFrames.load(ctx, CharacterLook.pet(sp))
            assertTrue(sp.id, f.canWalk)
            // 앉은 키(표정 그림)와 서 있는 동작 그림의 키가 크게 다르지 않다
            val calm = f.faces.getValue("calm").height
            f.anims["walk"]!!.forEach { assertTrue("${sp.id} ${it.height} vs $calm", kotlin.math.abs(it.height - calm) < calm * 0.15) }
            // 엎드린 그림은 낮다
            f.anims["sleep"]?.forEach { assertTrue(sp.id, it.height < calm * 0.85) }
        }
        val dir = System.getProperty("screenshotDir") ?: return
        // 눈으로 보기: 검정 푸들·코기·치즈냥의 모든 동작 칸
        val looks = listOf(CharacterLook.pet(Species.POODLE, "검정"), CharacterLook.pet(Species.CORGI), CharacterLook.pet(Species.TABBY_CAT, "흰색"))
        val sets = looks.map { com.simbio.don.app.ui.kit.PetFrames.load(ctx, it) }
        val cols = sets.maxOf { s -> s.anims.values.sumOf { it.size } + 1 }
        val img = ImageBitmap(cols * 130, sets.size * 140)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) {
            drawRect(Color(0xFF2C2A3A))
            sets.forEachIndexed { r, s ->
                val all = listOf(s.faces.getValue("calm")) + listOf("idle", "walk", "sit", "sleep", "play", "eat").flatMap { s.anims[it].orEmpty() }
                all.forEachIndexed { c, b ->
                    val u = 120f / com.simbio.don.app.ui.kit.PetFrames.UNIT_H
                    drawImage(b, IntOffset.Zero, IntSize(b.width, b.height), IntOffset(c * 130 + 65 - (b.width * u / 2).toInt(), r * 140 + 130 - (b.height * u).toInt()), IntSize((b.width * u).toInt(), (b.height * u).toInt()))
                }
            }
        }
        File(dir).apply { mkdirs() }.resolve("p5_pet_motion.png").outputStream().use {
            img.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
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
