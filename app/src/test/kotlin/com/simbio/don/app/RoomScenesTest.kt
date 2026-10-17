package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.simbio.don.app.ui.DayPhase
import com.simbio.don.app.ui.PixelRoom
import com.simbio.don.app.weather.Weather
import com.simbio.don.game.HouseLayout
import com.simbio.don.game.HouseLevel
import com.simbio.don.game.Mood
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 시간대·날씨별 방 그림 (스크린샷 확인용) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class RoomScenesTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 날씨와_시간대() {
        val house = HouseLayout.of(HouseLevel.ONE_ROOM, emptyList())
        val scenes = listOf(
            Triple(DayPhase.DAY, Weather.RAIN, Mood.SAD),
            Triple(DayPhase.SUNSET, Weather.CLOUDY, Mood.WORRIED),
            Triple(DayPhase.NIGHT, Weather.SNOW, Mood.JOYFUL),
            Triple(DayPhase.DAY, Weather.STORM, Mood.ANGRY),
        )
        rule.setContent {
            Column {
                scenes.chunked(2).forEach { row ->
                    Row {
                        row.forEach { (phase, weather, mood) ->
                            Column(Modifier.width(196.dp).padding(2.dp)) {
                                PixelRoom(house, mood, Modifier.fillMaxWidth(), phase, weather)
                                Text("${phase.name} ${weather.label} ${mood.label}")
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        val dir = System.getProperty("screenshotDir") ?: return
        val bmp = rule.runOnUiThread {
            val view = rule.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        File(dir).apply { mkdirs() }.resolve("40_room_weather.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
