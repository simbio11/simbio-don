package com.simbio.don.app.weather

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 창밖 날씨 */
enum class Weather(val label: String) {
    CLEAR("맑음"),
    CLOUDY("흐림"),
    RAIN("비"),
    SNOW("눈"),
    STORM("천둥번개"),
    FOG("안개"),
    ;

    /** 하늘이 회색인 날 */
    val overcast: Boolean get() = this != CLEAR

    companion object {
        /** WMO 날씨 코드 → 창밖 날씨 */
        fun fromWmo(code: Int): Weather = when (code) {
            0, 1 -> CLEAR
            2, 3 -> CLOUDY
            45, 48 -> FOG
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDY
        }
    }
}

data class City(val name: String, val lat: Double, val lon: Double)

/**
 * 고른 도시의 지금 날씨를 Open-Meteo(무료, 키 없음)에서 받아온다. 한 시간 동안은 다시 묻지 않는다.
 * 보내는 건 도시 좌표뿐이고, 가계부 기록·알림 내용은 보내지 않는다. 도시를 끄면 아무것도 보내지 않는다.
 */
object WeatherService {
    val CITIES = listOf(
        City("서울", 37.5665, 126.9780), City("인천", 37.4563, 126.7052), City("수원", 37.2636, 127.0286),
        City("춘천", 37.8813, 127.7298), City("강릉", 37.7519, 128.8761), City("대전", 36.3504, 127.3845),
        City("세종", 36.4800, 127.2890), City("청주", 36.6424, 127.4890), City("천안", 36.8151, 127.1139),
        City("대구", 35.8714, 128.6014), City("포항", 36.0190, 129.3435), City("광주", 35.1595, 126.8526),
        City("전주", 35.8242, 127.1480), City("부산", 35.1796, 129.0756), City("울산", 35.5384, 129.3114),
        City("창원", 35.2279, 128.6811), City("제주", 33.4996, 126.5312),
    )
    private const val PREFS = "don_prefs"
    private const val KEY_CITY = "weather_city"
    private const val KEY_CODE = "weather_code"
    private const val KEY_AT = "weather_at"
    private const val FRESH_MS = 60 * 60 * 1000L

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 고른 도시 (기본 서울). null이면 날씨 끔 → 늘 맑음 */
    fun city(c: Context): City? {
        val name = prefs(c).getString(KEY_CITY, "서울") ?: return null
        return CITIES.firstOrNull { it.name == name }
    }

    fun setCity(c: Context, name: String?) {
        prefs(c).edit().putString(KEY_CITY, name ?: "").remove(KEY_AT).apply()
    }

    /** 마지막으로 받은 날씨 (오래돼도) */
    fun cached(c: Context): Weather? {
        if (city(c) == null) return null
        val code = prefs(c).getInt(KEY_CODE, -1).takeIf { it >= 0 } ?: return null
        return Weather.fromWmo(code)
    }

    /** 한 시간이 지났으면 새로 받아온다. 실패하면 마지막 값. */
    suspend fun refresh(c: Context): Weather? = withContext(Dispatchers.IO) {
        val city = city(c) ?: return@withContext null
        val p = prefs(c)
        if (System.currentTimeMillis() - p.getLong(KEY_AT, 0) < FRESH_MS) return@withContext cached(c)
        val code = runCatching { fetch(city) }.getOrNull() ?: return@withContext cached(c)
        p.edit().putInt(KEY_CODE, code).putLong(KEY_AT, System.currentTimeMillis()).apply()
        Weather.fromWmo(code)
    }

    private fun fetch(city: City): Int {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}&current=weather_code&timezone=auto")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body).getJSONObject("current").getInt("weather_code")
        } finally {
            conn.disconnect()
        }
    }
}
