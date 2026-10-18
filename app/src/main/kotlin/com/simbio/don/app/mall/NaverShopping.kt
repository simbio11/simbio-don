package com.simbio.don.app.mall

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 네이버 쇼핑 검색 결과 한 줄 (사진은 받지 않고 도트 아이콘으로 보여준다) */
data class NaverItem(val title: String, val price: Long, val mall: String, val category: String)

/**
 * 네이버 쇼핑 검색 API (선택). 사용자가 직접 발급한 애플리케이션 키가 있을 때만 쓴다.
 * 보내는 것은 **검색어뿐** — 가계부 기록·알림 내용은 보내지 않는다. 웹 페이지를 긁지 않는다 (금지 사항).
 * 키는 이 폰에만 저장하고 백업에도 넣지 않는다.
 */
object NaverShopping {
    private const val PREFS = "naver_shopping"
    private const val KEY_ID = "client_id"
    private const val KEY_SECRET = "client_secret"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun keys(c: Context): Pair<String, String>? {
        val p = prefs(c)
        val id = p.getString(KEY_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val secret = p.getString(KEY_SECRET, null)?.takeIf { it.isNotBlank() } ?: return null
        return id to secret
    }

    fun isOn(c: Context): Boolean = keys(c) != null

    fun save(c: Context, id: String, secret: String) {
        prefs(c).edit().putString(KEY_ID, id.trim()).putString(KEY_SECRET, secret.trim()).apply()
    }

    fun clear(c: Context) = prefs(c).edit().clear().apply()

    sealed interface Result {
        data class Ok(val items: List<NaverItem>) : Result
        data class Failed(val message: String) : Result
    }

    /** 검색 (최대 20개, 정확도순). 키가 없거나 실패하면 Failed */
    suspend fun search(c: Context, query: String): Result = withContext(Dispatchers.IO) {
        val (id, secret) = keys(c) ?: return@withContext Result.Failed("네이버 검색 키가 없어요")
        if (query.isBlank()) return@withContext Result.Ok(emptyList())
        val url = URL("https://openapi.naver.com/v1/search/shop.json?display=20&query=" + URLEncoder.encode(query.trim(), "UTF-8"))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.setRequestProperty("X-Naver-Client-Id", id)
        conn.setRequestProperty("X-Naver-Client-Secret", secret)
        try {
            when (val code = conn.responseCode) {
                200 -> Result.Ok(parse(conn.inputStream.bufferedReader().use { it.readText() }))
                401, 403 -> Result.Failed("키가 맞지 않아요 (설정에서 확인)")
                429 -> Result.Failed("오늘 검색을 너무 많이 했어요")
                else -> Result.Failed("검색하지 못했어요 ($code)")
            }
        } catch (e: Exception) {
            Result.Failed("인터넷에 연결하지 못했어요")
        } finally {
            conn.disconnect()
        }
    }

    /** 응답 JSON → 결과. 제목의 <b> 태그와 HTML 글자를 지운다 */
    fun parse(json: String): List<NaverItem> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.getJSONObject(i)
            val price = o.optString("lprice").toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            NaverItem(
                title = clean(o.optString("title")),
                price = price,
                mall = clean(o.optString("mallName")).ifBlank { "네이버 쇼핑" },
                category = listOf("category1", "category2", "category3").map { clean(o.optString(it)) }.filter { it.isNotBlank() }.joinToString(" > "),
            )
        }
    }

    private fun clean(s: String): String = s.replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .trim()
}
