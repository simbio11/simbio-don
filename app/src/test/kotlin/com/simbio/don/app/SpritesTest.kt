package com.simbio.don.app

import com.simbio.don.app.ui.pixel.Sprites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpritesTest {
    @Test
    fun 도트_격자는_직사각형이고_모르는_색이_없다() {
        Sprites.allGrids().forEach { (name, grid) ->
            val width = grid.first().length
            grid.forEachIndexed { i, row ->
                assertEquals("$name ${i}번째 줄", width, row.length)
                row.filter { it != '.' }.forEach { ch -> assertNotNull("$name '$ch'", Sprites.color(ch)) }
            }
        }
    }
}
