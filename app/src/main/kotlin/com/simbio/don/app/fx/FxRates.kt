package com.simbio.don.app.fx

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.simbio.don.app.data.DonRepository
import com.simbio.don.notify.Currencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 공개 환율 (유럽중앙은행 기준, frankfurter.app). 해외 결제 기록이 있을 때만 하루 한 번, 통화 이름만 보내 받는다.
 * 설정에서 끌 수 있고, 끄면 내 환전 알림의 환율과 직접 정한 환율, 어림만 쓴다.
 */
object FxRates {
    private fun prefs(c: Context) = c.getSharedPreferences("fx", Context.MODE_PRIVATE)

    @VisibleForTesting
    @Volatile var baseUrl = "https://api.frankfurter.app"

    /** 공개 환율로 받을 수 있는 통화 (유럽중앙은행이 안 주는 것은 빼고) */
    private val PUBLIC = Currencies.KNOWN - setOf("TWD", "VND")

    fun isAuto(c: Context): Boolean = prefs(c).getBoolean("auto", true)

    fun setAuto(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto", on).apply()

    /** 받아야 하면 받아서 저장 (어림으로 적었던 기록도 다시 계산된다). 받은 통화 수 */
    suspend fun refreshIfNeeded(c: Context, today: LocalDate = LocalDate.now()): Int = withContext(Dispatchers.IO) {
        if (!isAuto(c)) return@withContext 0
        if (prefs(c).getLong("last", -1) == today.toEpochDay()) return@withContext 0
        val repo = DonRepository.get(c)
        val need = repo.needsFxRefresh(today).filter { it in PUBLIC }
        if (need.isEmpty()) return@withContext 0
        val rates = fetch(need) ?: return@withContext 0
        prefs(c).edit().putLong("last", today.toEpochDay()).apply()
        rates.forEach { (cur, rate) -> repo.saveFxRate(cur, rate, DonRepository.FX_PUBLIC, today) }
        rates.size
    }

    private fun fetch(codes: Collection<String>): Map<String, Double>? = runCatching {
        val conn = URL("$baseUrl/latest?from=KRW&to=${codes.sorted().joinToString(",")}").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            if (conn.responseCode != 200) return@runCatching null
            parse(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** {"base":"KRW","rates":{"USD":0.00072}} → 1단위에 원 (USD → 1388.9) */
    fun parse(json: String): Map<String, Double> {
        val r = JSONObject(json).getJSONObject("rates")
        return r.keys().asSequence().mapNotNull { k -> r.optDouble(k).takeIf { it > 0 }?.let { k to 1.0 / it } }
            .filter { (k, v) -> Currencies.plausible(k, v) }.toMap()
    }
}
