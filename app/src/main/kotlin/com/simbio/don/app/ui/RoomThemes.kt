package com.simbio.don.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.simbio.don.app.R
import com.simbio.don.app.ui.pixel.RoomColors
import com.simbio.don.game.HouseLevel
import com.simbio.don.game.Shop

/** 인테리어 부분마다 고를 수 있는 색 (첫째가 기본). 방 그림 생성기가 만든 [RoomColors]에서 가져온다 */
object RoomThemes {
    val all: Map<Shop.Interior, List<Pair<String, IntArray>>> = mapOf(
        Shop.Interior.WALL to RoomColors.walls,
        Shop.Interior.FLOOR to RoomColors.floors,
        Shop.Interior.RUG to RoomColors.rugs,
        Shop.Interior.CURTAIN to RoomColors.curtains,
        Shop.Interior.BEDDING to RoomColors.beddings,
    )

    private val markers: Map<Shop.Interior, IntArray> = mapOf(
        Shop.Interior.WALL to RoomColors.WALL,
        Shop.Interior.FLOOR to RoomColors.FLOOR,
        Shop.Interior.RUG to RoomColors.RUG,
        Shop.Interior.CURTAIN to RoomColors.CURTAIN,
        Shop.Interior.BEDDING to RoomColors.BEDDING,
    )

    /** 표식 색 → 고른 색 (고르지 않았거나 모르는 이름이면 그대로) */
    fun colorMap(interior: Map<Shop.Interior, String>): Map<Int, Int> = buildMap {
        interior.forEach { (part, name) ->
            val ramp = all[part]?.firstOrNull { it.first == name }?.second ?: return@forEach
            markers.getValue(part).forEachIndexed { i, m -> put(m, ramp[i.coerceAtMost(ramp.lastIndex)]) }
        }
    }
}

/** 집 단계마다 다른 방 그림 */
object RoomArt {
    @DrawableRes
    fun base(level: HouseLevel): Int = when (level) {
        HouseLevel.ONE_ROOM -> R.drawable.px_room_one_room
        HouseLevel.TWO_ROOM -> R.drawable.px_room_two_room
        HouseLevel.APARTMENT -> R.drawable.px_room_apartment
    }

    @DrawableRes
    fun lightDay(level: HouseLevel): Int = when (level) {
        HouseLevel.ONE_ROOM -> R.drawable.px_light_day_one_room
        HouseLevel.TWO_ROOM -> R.drawable.px_light_day_two_room
        HouseLevel.APARTMENT -> R.drawable.px_light_day_apartment
    }

    @DrawableRes
    fun lightSunset(level: HouseLevel): Int = when (level) {
        HouseLevel.ONE_ROOM -> R.drawable.px_light_sunset_one_room
        HouseLevel.TWO_ROOM -> R.drawable.px_light_sunset_two_room
        HouseLevel.APARTMENT -> R.drawable.px_light_sunset_apartment
    }

    @DrawableRes
    fun nightShade(level: HouseLevel): Int = when (level) {
        HouseLevel.ONE_ROOM -> R.drawable.px_night_shade_one_room
        HouseLevel.TWO_ROOM -> R.drawable.px_night_shade_two_room
        HouseLevel.APARTMENT -> R.drawable.px_night_shade_apartment
    }

    @DrawableRes
    fun nightLights(level: HouseLevel): Int = when (level) {
        HouseLevel.ONE_ROOM -> R.drawable.px_night_lights_one_room
        HouseLevel.TWO_ROOM -> R.drawable.px_night_lights_two_room
        HouseLevel.APARTMENT -> R.drawable.px_night_lights_apartment
    }

    private val cache = LruCache<String, ImageBitmap>(12)

    /** 고른 인테리어 색으로 다시 칠한 방 바탕 (집·색 조합마다 기억) */
    fun base(context: Context, level: HouseLevel, interior: Map<Shop.Interior, String>): ImageBitmap {
        val key = level.name + "|" + interior.entries.sortedBy { it.key.ordinal }.joinToString { "${it.key}=${it.value}" }
        cache.get(key)?.let { return it }
        val bmp = BitmapFactory.decodeResource(
            context.resources,
            base(level),
            BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )
        val map = RoomThemes.colorMap(interior)
        val out = if (map.isEmpty()) {
            bmp
        } else {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            for (i in px.indices) map[px[i]]?.let { px[i] = it }
            Bitmap.createBitmap(px, bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        }
        return out.asImageBitmap().also { cache.put(key, it) }
    }
}
