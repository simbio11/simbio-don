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

/** Claude 모델 (그대로 둔 이름). [fallback]: 안전 판단으로 거절되면 서버가 다른 모델로 다시 해 보는지 */
enum class AiModel(val id: String, val label: String, val note: String, val fallback: Boolean) {
    OPUS("claude-opus-5-5", "Opus 5.5", "기본 · 똑똑하고 값도 적당해요", true),
    FABLE("claude-fable-5-2", "Fable 5.2", "가장 똑똑해요 · Opus의 2배 값", true),
    SONNET("claude-sonnet-5-5", "Sonnet 5.5", "빠르고 더 싸요", true),
    HAIKU("claude-haiku-5-5", "Haiku 5.5", "가장 빠르고 싸요", false),
}

/** 고를 수 있는 모델 하나 (회사마다) */
data class AiModelOption(val id: String, val label: String, val note: String)

/**
 * AI 회사. 사용자가 자기 키를 넣은 곳으로만 보낸다.
 * [COMPATIBLE]은 OpenAI와 같은 모양(/chat/completions)으로 받는 곳 — OpenRouter·Groq·DeepSeek·Mistral·내 컴퓨터의 Ollama 등. 주소와 모델 이름을 직접 넣는다.
 */
enum class AiProvider(
    val label: String,
    val short: String,
    val keyHint: String,
    val keyWhere: String,
    val models: List<AiModelOption>,
    /** 기본 주소 (호환은 사용자가 넣는다) */
    val baseUrl: String,
) {
    CLAUDE(
        "Claude (Anthropic)", "Claude", "sk-ant-…", "console.anthropic.com › API Keys",
        AiModel.entries.map { AiModelOption(it.id, it.label, it.note) }, "https://api.anthropic.com",
    ),
    GEMINI(
        "Gemini (Google)", "Gemini", "AIza…", "aistudio.google.com › Get API key",
        listOf(
            AiModelOption("gemini-4-hydrogen", "4 Hydrogen", "기본 · 균형"),
            AiModelOption("gemini-4-carbon", "4 Carbon", "가장 똑똑해요"),
            AiModelOption("gemini-3.5-flash-lite", "3.5 Flash-Lite", "가장 빠르고 싸요"),
        ),
        "https://generativelanguage.googleapis.com",
    ),
    OPENAI(
        "ChatGPT (OpenAI)", "OpenAI", "sk-…", "platform.openai.com › API keys",
        listOf(
            AiModelOption("gpt-6.2-sol", "GPT-6.2 Sol", "기본 · 균형"),
            AiModelOption("gpt-6.2-astra", "GPT-6.2 Astra", "가장 똑똑해요"),
            AiModelOption("gpt-6-luna", "GPT-6 Luna", "가장 빠르고 싸요"),
        ),
        "https://api.openai.com/v1",
    ),
    COMPATIBLE("OpenAI 호환 (OpenRouter·Groq·DeepSeek 등)", "호환", "API 키", "쓰는 서비스의 API 키 화면", emptyList(), ""),
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

/** 요청을 실제로 보내는 곳 (테스트는 가짜로 바꾼다). [turn]은 도구를 쓰는 대화 한 걸음 */
fun interface AiBackend {
    suspend fun send(model: String, req: AiRequest): AiReply

    suspend fun turn(model: String, req: AgentRequest): AgentReply = AgentReply.Failed("이 AI는 대화를 아직 못 해요")

    /** 이 키로 지금 쓸 수 있는 글 모델 (회사에서 받아 온 목록, 새것부터). 실패하면 오류 글 */
    suspend fun models(): Result<List<AiModelOption>> = Result.failure(IllegalStateException("목록을 못 불러와요"))
}

/**
 * AI 도우미 설정 (이 폰에만). 켜려면 사용자가 고른 회사의 자기 API 키를 넣는다.
 * 키는 회사마다 폰의 키 저장소로 암호화해 두고 백업에도 넣지 않는다.
 */
object Ai {
    private const val PREFS = "ai"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @VisibleForTesting
    @Volatile var backendForTests: AiBackend? = null

    fun provider(c: Context): AiProvider =
        prefs(c).getString("provider", null)?.let { n -> AiProvider.entries.firstOrNull { it.name == n } } ?: AiProvider.CLAUDE

    fun setProvider(c: Context, p: AiProvider) = prefs(c).edit().putString("provider", p.name).apply()

    /** 예전(Claude만 있던 때) 키 이름은 "key" */
    private fun keyName(p: AiProvider) = if (p == AiProvider.CLAUDE) "key" else "key_${p.name}"

    fun isOn(c: Context): Boolean {
        val p = provider(c)
        return key(c, p) != null && model(c, p).isNotBlank() && (p != AiProvider.COMPATIBLE || baseUrl(c).isNotBlank())
    }

    fun key(c: Context, p: AiProvider = provider(c)): String? = prefs(c).getString(keyName(p), null)?.let(KeyVault::open)?.takeIf { it.isNotBlank() }

    fun setKey(c: Context, key: String, p: AiProvider = provider(c)) = prefs(c).edit().putString(keyName(p), KeyVault.seal(key.trim())).apply()

    fun clear(c: Context, p: AiProvider = provider(c)) = prefs(c).edit().remove(keyName(p)).apply()

    /** 보여 줄 때는 끝 네 글자만 */
    fun maskedKey(c: Context, p: AiProvider = provider(c)): String? = key(c, p)?.let { "••••" + it.takeLast(4) }

    /** 회사가 그만 받는(곧 끄는) 모델 → 같은 자리의 새 모델 */
    val RETIRED = mapOf(
        "gemini-2.5-pro" to "gemini-4-carbon",
        "gemini-2.5-flash" to "gemini-4-hydrogen",
        "gemini-2.5-flash-lite" to "gemini-3.5-flash-lite",
        "gpt-5" to "gpt-6.2-sol",
        "gpt-5-mini" to "gpt-6.2-sol",
        "gpt-5-nano" to "gpt-6-luna",
    )

    /** 고른 모델 이름 (회사마다). 예전 Claude 설정은 "model"에 AiModel 이름으로 있다. 끝난 모델은 새 모델로 */
    fun model(c: Context, p: AiProvider = provider(c)): String {
        prefs(c).getString("model_${p.name}", null)?.takeIf { it.isNotBlank() }?.let { return RETIRED[it] ?: it }
        if (p == AiProvider.CLAUDE) prefs(c).getString("model", null)?.let { n -> AiModel.entries.firstOrNull { it.name == n }?.let { return it.id } }
        return p.models.firstOrNull()?.id.orEmpty()
    }

    fun setModel(c: Context, id: String, p: AiProvider = provider(c)) = prefs(c).edit().putString("model_${p.name}", id.trim()).apply()

    /** 호환 서비스 주소 (예: https://openrouter.ai/api/v1) */
    fun baseUrl(c: Context): String = prefs(c).getString("base", null).orEmpty()

    fun setBaseUrl(c: Context, url: String) = prefs(c).edit().putString("base", url.trim().trimEnd('/')).apply()

    /** "Claude · Opus 5.5" */
    fun label(c: Context): String {
        val p = provider(c)
        val m = model(c, p)
        return "${p.short} · " + (p.models.firstOrNull { it.id == m }?.label ?: m)
    }

    private fun backend(c: Context): AiBackend? {
        backendForTests?.let { return it }
        val p = provider(c)
        val key = key(c, p) ?: return null
        return when (p) {
            AiProvider.CLAUDE -> ClaudeBackend(key)
            AiProvider.GEMINI -> GeminiBackend(key)
            AiProvider.OPENAI -> OpenAiBackend(key, p.baseUrl, official = true)
            AiProvider.COMPATIBLE -> baseUrl(c).takeIf { it.isNotBlank() }?.let { OpenAiBackend(key, it, official = false) }
        }
    }

    suspend fun send(c: Context, req: AiRequest): AiReply {
        val b = backend(c) ?: return AiReply.Failed("AI 키가 없어요. 설정 › AI 도우미에서 넣어 주세요")
        return b.send(model(c), req)
    }

    /** 내 키로 회사에서 모델 목록 받아 오기 */
    suspend fun models(c: Context): Result<List<AiModelOption>> {
        val b = backend(c) ?: return Result.failure(IllegalStateException("먼저 API 키를 넣어 주세요"))
        return b.models()
    }

    suspend fun turn(c: Context, req: AgentRequest): AgentReply {
        val b = backend(c) ?: return AgentReply.Failed("AI 키가 없어요. 설정 › AI 도우미에서 넣어 주세요")
        return b.turn(model(c), req)
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
