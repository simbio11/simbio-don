package com.simbio.don.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.VisibleForTesting
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 고를 수 있는 모델. [fallback]: 안전 판단으로 거절되면 서버가 다른 모델로 다시 해 보는지 */
enum class AiModel(val id: String, val label: String, val note: String, val fallback: Boolean) {
    OPUS("claude-opus-5-5", "Opus 5.5", "가장 똑똑해요", true),
    SONNET("claude-sonnet-5-5", "Sonnet 5.5", "빠르고 더 싸요", true),
    HAIKU("claude-haiku-5-5", "Haiku 5.5", "가장 빠르고 싸요", false),
}

/** 보낼 요청 하나 — 보내기 전에 [system]·[user]를 그대로 보여 준다 */
data class AiRequest(
    val system: String,
    val user: String,
    /** 답을 이 JSON 모양으로 받는다 (없으면 글) */
    val schema: Map<String, Any>? = null,
    val maxTokens: Long = 4_000,
    /** 짧은 일(말로 적기): 생각을 덜 하고 빨리 */
    val quick: Boolean = false,
)

sealed interface AiReply {
    data class Ok(val text: String, val inputTokens: Long = 0, val outputTokens: Long = 0) : AiReply
    data class Refused(val reason: String) : AiReply
    data class Failed(val message: String) : AiReply
}

/** 요청을 실제로 보내는 곳 (테스트는 가짜로 바꾼다) */
fun interface AiBackend {
    suspend fun send(model: AiModel, req: AiRequest): AiReply
}

/**
 * AI 도우미 설정 (이 폰에만). 켜려면 사용자가 자기 Anthropic API 키를 넣는다.
 * 키는 폰의 키 저장소로 암호화해 두고 백업에도 넣지 않는다.
 */
object Ai {
    private const val PREFS = "ai"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @VisibleForTesting
    @Volatile var backendForTests: AiBackend? = null

    fun isOn(c: Context): Boolean = key(c) != null

    fun key(c: Context): String? = prefs(c).getString("key", null)?.let(KeyVault::open)?.takeIf { it.isNotBlank() }

    fun setKey(c: Context, key: String) = prefs(c).edit().putString("key", KeyVault.seal(key.trim())).apply()

    fun clear(c: Context) = prefs(c).edit().remove("key").apply()

    /** 보여 줄 때는 끝 네 글자만 */
    fun maskedKey(c: Context): String? = key(c)?.let { "••••" + it.takeLast(4) }

    fun model(c: Context): AiModel =
        prefs(c).getString("model", null)?.let { n -> AiModel.entries.firstOrNull { it.name == n } } ?: AiModel.OPUS

    fun setModel(c: Context, m: AiModel) = prefs(c).edit().putString("model", m.name).apply()

    suspend fun send(c: Context, req: AiRequest): AiReply {
        val key = key(c) ?: return AiReply.Failed("AI 키가 없어요. 설정 › AI 도우미에서 넣어 주세요")
        return (backendForTests ?: ClaudeBackend(key)).send(model(c), req)
    }
}

/** 폰의 키 저장소(AndroidKeyStore)로 API 키를 암호화한다. 키 저장소가 없는 곳(테스트)에서는 그대로 둔다 */
private object KeyVault {
    private const val ALIAS = "dont_ai_key"
    private const val GCM_TAG_BITS = 128

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    fun seal(plain: String): String = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        "k1:" + b64(cipher.iv) + ":" + b64(cipher.doFinal(plain.toByteArray()))
    }.getOrElse { "p:$plain" }

    fun open(stored: String): String? = when {
        stored.startsWith("p:") -> stored.removePrefix("p:")
        stored.startsWith("k1:") -> runCatching {
            val (iv, data) = stored.removePrefix("k1:").split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(GCM_TAG_BITS, iv)) }
            String(cipher.doFinal(data))
        }.getOrNull()
        else -> null
    }
}
