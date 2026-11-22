package com.simbio.don.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 아주 작은 HTTP: JSON 하나 보내고 (상태, 본문) 받기 */
internal object Http {
    fun postJson(url: String, headers: Map<String, String>, body: JSONObject): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            return code to text
        } finally {
            c.disconnect()
        }
    }

    /** 서버가 준 오류 글 (있으면) */
    fun get(url: String, headers: Map<String, String>): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            return code to text
        } finally {
            c.disconnect()
        }
    }

    /** 글 대화에 못 쓰는 모델 (그림·소리·임베딩 등) */
    private val NOT_CHAT = listOf("embed", "tts", "image", "audio", "realtime", "transcri", "live", "moderation", "whisper", "dall-e", "search", "banana", "omni", "decision", "aqa", "imagen", "veo", "computer")

    fun chatModel(id: String) = NOT_CHAT.none { id.contains(it, ignoreCase = true) }

    fun errorMessage(body: String): String? = runCatching {
        val o = JSONObject(body)
        o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: o.optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    fun problem(code: Int, body: String): String {
        val m = errorMessage(body).orEmpty()
        return when {
            code == 401 || m.contains("API key not valid", true) || m.contains("API_KEY_INVALID") -> "API 키가 맞지 않아요. 설정에서 다시 넣어 주세요"
            code == 403 -> "이 키로는 쓸 수 없어요 (권한·결제 확인)" + if (m.isNotBlank()) ": ${m.take(120)}" else ""
            code == 404 -> "모델을 찾지 못했어요. 모델 이름을 확인해 주세요" + if (m.isNotBlank()) ": ${m.take(120)}" else ""
            code == 429 -> "요청이 많거나 한도를 넘었어요. 잠시 뒤 다시 해 주세요"
            code >= 500 -> "AI 서버가 답하지 못했어요 ($code). 잠시 뒤 다시 해 주세요"
            else -> "AI 요청이 안 됐어요 ($code)" + if (m.isNotBlank()) ": ${m.take(160)}" else ""
        }
    }
}

/**
 * Google Gemini (generativelanguage.googleapis.com, generateContent). 사용자 키로.
 * 생각 서명(thoughtSignature)이 붙은 답은 받은 그대로 다음 요청에 돌려 보낸다.
 */
class GeminiBackend(
    private val apiKey: String,
    private val baseUrl: String = AiProvider.GEMINI.baseUrl,
) : AiBackend {
    private fun url(model: String) = "${baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent"
    private val headers get() = mapOf("x-goog-api-key" to apiKey)

    override suspend fun send(model: String, req: AiRequest): AiReply = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", AiJson.systemWithSchema(req.system, req.schema)))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", req.user)))))
            .put("generationConfig", config(req.maxTokens, json = req.schema != null))
        when (val r = call(model, body)) {
            is Got.Bad -> r.reply
            is Got.Fine -> {
                val text = texts(r.content)
                if (req.schema != null) AiReply.Ok(AiJson.stripFence(text), r.inTok, r.outTok) else AiReply.Ok(text, r.inTok, r.outTok)
            }
        }
    }

    override suspend fun turn(model: String, req: AgentRequest): AgentReply = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        req.turns.forEach { t ->
            when (t) {
                is Turn.User -> contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", t.text))))
                is Turn.Model -> contents.put(
                    (t.native as? JSONObject) ?: JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", t.text.ifBlank { "…" }))),
                )
                is Turn.Results -> contents.put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray().apply {
                            t.results.forEach { r ->
                                val fr = JSONObject().put("name", r.name).put("response", JSONObject().put(if (r.isError) "error" else "result", r.content))
                                if (!r.callId.startsWith("g:")) fr.put("id", r.callId)
                                put(JSONObject().put("functionResponse", fr))
                            }
                        },
                    ),
                )
            }
        }
        val decls = JSONArray()
        req.tools.forEach { decls.put(JSONObject().put("name", it.name).put("description", it.description).put("parameters", AiJson.schema(it))) }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", req.system))))
            .put("contents", contents)
            .put("generationConfig", config(req.maxTokens, json = false))
        if (req.tools.isNotEmpty()) body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
        when (val r = call(model, body)) {
            is Got.Bad -> when (val x = r.reply) {
                is AiReply.Refused -> AgentReply.Refused(x.reason)
                is AiReply.Failed -> AgentReply.Failed(x.message)
                is AiReply.Ok -> AgentReply.Failed("답을 읽지 못했어요")
            }
            is Got.Fine -> {
                val parts = r.content.optJSONArray("parts") ?: JSONArray()
                val calls = (0 until parts.length()).mapNotNull { i ->
                    parts.optJSONObject(i)?.optJSONObject("functionCall")?.let { fc ->
                        ToolCall(fc.optString("id").takeIf { it.isNotBlank() } ?: "g:$i", fc.optString("name"), fc.optJSONObject("args") ?: JSONObject())
                    }
                }
                AgentReply.Ok(Turn.Model(texts(r.content), calls, r.content), r.inTok, r.outTok)
            }
        }
    }

    /** GET /v1beta/models — generateContent를 받는 글 모델만 */
    override suspend fun models(): Result<List<AiModelOption>> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = Http.get("${baseUrl.trimEnd('/')}/v1beta/models?pageSize=200", headers)
            if (code !in 200..299) return@withContext Result.failure(IllegalStateException(Http.problem(code, text)))
            val arr = JSONObject(text).optJSONArray("models") ?: JSONArray()
            val list = (0 until arr.length()).mapNotNull { i ->
                val m = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = m.optString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")
                val ok = methods == null || (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                if (!ok || !id.startsWith("gemini") || !Http.chatModel(id)) null
                else AiModelOption(id, m.optString("displayName").ifBlank { id }.removePrefix("Gemini "), "")
            }
            Result.success(list.sortedWith(compareByDescending<AiModelOption> { versionOf(it.id) }.thenBy { it.id }))
        } catch (e: IOException) {
            Result.failure(IllegalStateException("인터넷 연결을 확인해 주세요"))
        }
    }

    /** "gemini-4-carbon" → 4.0, "gemini-3.5-flash" → 3.5 (새것부터 보이게) */
    private fun versionOf(id: String) = Regex("""gemini-(\d+(?:\.\d+)?)""").find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    // 생각하는 모델은 생각에도 출력 토큰을 쓴다 — 넉넉히
    private fun config(maxTokens: Long, json: Boolean) = JSONObject().put("maxOutputTokens", maxOf(maxTokens, 8_192L)).apply {
        if (json) put("responseMimeType", "application/json")
    }

    private sealed interface Got {
        data class Fine(val content: JSONObject, val inTok: Long, val outTok: Long) : Got
        data class Bad(val reply: AiReply) : Got
    }

    private fun call(model: String, body: JSONObject): Got {
        val (code, text) = try {
            Http.postJson(url(model), headers, body)
        } catch (e: IOException) {
            return Got.Bad(AiReply.Failed("인터넷 연결을 확인해 주세요"))
        }
        if (code !in 200..299) return Got.Bad(AiReply.Failed(Http.problem(code, text)))
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return Got.Bad(AiReply.Failed("답을 읽지 못했어요"))
        o.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let {
            return Got.Bad(AiReply.Refused("Gemini가 이 요청을 막았어요 ($it)"))
        }
        val cand = o.optJSONArray("candidates")?.optJSONObject(0) ?: return Got.Bad(AiReply.Failed("답이 비어 있어요"))
        val finish = cand.optString("finishReason")
        if (finish in REFUSED) return Got.Bad(AiReply.Refused("Gemini가 답하지 않기로 했어요 ($finish)"))
        val content = cand.optJSONObject("content") ?: JSONObject().put("role", "model").put("parts", JSONArray())
        if (!content.has("role")) content.put("role", "model")
        val usage = o.optJSONObject("usageMetadata")
        return Got.Fine(content, usage?.optLong("promptTokenCount") ?: 0, usage?.optLong("candidatesTokenCount") ?: 0)
    }

    /** 생각(thought) 말고 보여 줄 글만 */
    private fun texts(content: JSONObject): String {
        val parts = content.optJSONArray("parts") ?: return ""
        return (0 until parts.length()).mapNotNull { i ->
            parts.optJSONObject(i)?.takeIf { !it.optBoolean("thought") && it.has("text") }?.optString("text")
        }.joinToString("")
    }

    private companion object {
        val REFUSED = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "IMAGE_SAFETY")
    }
}

/**
 * OpenAI Chat Completions 모양 (/chat/completions). [official]이면 api.openai.com, 아니면 같은 모양을 받는 다른 곳
 * (OpenRouter·Groq·DeepSeek·Mistral·Ollama 등 — 사용자가 주소를 넣는다).
 */
class OpenAiBackend(
    private val apiKey: String,
    private val baseUrl: String,
    private val official: Boolean,
) : AiBackend {
    private val url get() = "${baseUrl.trimEnd('/')}/chat/completions"
    private val headers get() = mapOf("Authorization" to "Bearer $apiKey")

    /** GET /models — 글 대화 모델만 (호환 서비스는 받은 그대로) */
    override suspend fun models(): Result<List<AiModelOption>> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = Http.get("${baseUrl.trimEnd('/')}/models", headers)
            if (code !in 200..299) return@withContext Result.failure(IllegalStateException(Http.problem(code, text)))
            val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
            val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i) }
            val list = items.filter { o ->
                val id = o.optString("id")
                id.isNotBlank() && Http.chatModel(id) && (!official || id.startsWith("gpt-") || id.startsWith("o"))
            }.sortedByDescending { it.optLong("created") }.map { AiModelOption(it.optString("id"), it.optString("id"), "") }
            Result.success(list)
        } catch (e: IOException) {
            Result.failure(IllegalStateException("인터넷 연결을 확인해 주세요"))
        }
    }

    private fun base(model: String, maxTokens: Long, messages: JSONArray) = JSONObject()
        .put("model", model)
        .put("messages", messages)
        // OpenAI의 새 모델은 max_completion_tokens만, 다른 곳은 대개 max_tokens
        .put(if (official) "max_completion_tokens" else "max_tokens", maxOf(maxTokens, 4_000L))

    override suspend fun send(model: String, req: AiRequest): AiReply = withContext(Dispatchers.IO) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", AiJson.systemWithSchema(req.system, req.schema)))
            .put(JSONObject().put("role", "user").put("content", req.user))
        val body = base(model, req.maxTokens, messages)
        if (req.schema != null && official) body.put("response_format", JSONObject().put("type", "json_object"))
        when (val r = call(body)) {
            is Got.Bad -> r.reply
            is Got.Fine -> {
                val text = r.message.optString("content").takeUnless { r.message.isNull("content") }.orEmpty()
                AiReply.Ok(if (req.schema != null) AiJson.stripFence(text) else text, r.inTok, r.outTok)
            }
        }
    }

    override suspend fun turn(model: String, req: AgentRequest): AgentReply = withContext(Dispatchers.IO) {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", req.system))
        req.turns.forEach { t ->
            when (t) {
                is Turn.User -> messages.put(JSONObject().put("role", "user").put("content", t.text))
                is Turn.Model -> messages.put((t.native as? JSONObject) ?: JSONObject().put("role", "assistant").put("content", t.text))
                is Turn.Results -> t.results.forEach { r ->
                    messages.put(JSONObject().put("role", "tool").put("tool_call_id", r.callId).put("content", if (r.isError) "오류: ${r.content}" else r.content))
                }
            }
        }
        val body = base(model, req.maxTokens, messages)
        if (req.tools.isNotEmpty()) {
            body.put(
                "tools",
                JSONArray().apply {
                    req.tools.forEach { t ->
                        put(JSONObject().put("type", "function").put("function", JSONObject().put("name", t.name).put("description", t.description).put("parameters", AiJson.schema(t))))
                    }
                },
            )
        }
        when (val r = call(body)) {
            is Got.Bad -> when (val x = r.reply) {
                is AiReply.Refused -> AgentReply.Refused(x.reason)
                is AiReply.Failed -> AgentReply.Failed(x.message)
                is AiReply.Ok -> AgentReply.Failed("답을 읽지 못했어요")
            }
            is Got.Fine -> {
                val m = r.message
                val tc = m.optJSONArray("tool_calls") ?: JSONArray()
                val calls = (0 until tc.length()).mapNotNull { i ->
                    tc.optJSONObject(i)?.let { c ->
                        val f = c.optJSONObject("function") ?: return@let null
                        val args = runCatching { JSONObject(f.optString("arguments").ifBlank { "{}" }) }.getOrElse { JSONObject() }
                        ToolCall(c.optString("id").ifBlank { "call_$i" }, f.optString("name"), args)
                    }
                }
                val text = m.optString("content").takeUnless { m.isNull("content") }.orEmpty()
                // 다시 보낼 때는 꼭 필요한 것만 (role·content·tool_calls)
                val native = JSONObject().put("role", "assistant").put("content", if (text.isEmpty()) JSONObject.NULL else text)
                if (tc.length() > 0) native.put("tool_calls", tc)
                AgentReply.Ok(Turn.Model(text, calls, native), r.inTok, r.outTok)
            }
        }
    }

    private sealed interface Got {
        data class Fine(val message: JSONObject, val inTok: Long, val outTok: Long) : Got
        data class Bad(val reply: AiReply) : Got
    }

    private fun call(body: JSONObject): Got {
        val (code, text) = try {
            Http.postJson(url, headers, body)
        } catch (e: IOException) {
            return Got.Bad(AiReply.Failed("인터넷 연결을 확인해 주세요"))
        }
        if (code !in 200..299) return Got.Bad(AiReply.Failed(Http.problem(code, text)))
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return Got.Bad(AiReply.Failed("답을 읽지 못했어요"))
        val choice = o.optJSONArray("choices")?.optJSONObject(0) ?: return Got.Bad(AiReply.Failed("답이 비어 있어요"))
        val message = choice.optJSONObject("message") ?: JSONObject()
        message.optString("refusal").takeIf { it.isNotBlank() && !message.isNull("refusal") }?.let { return Got.Bad(AiReply.Refused(it)) }
        if (choice.optString("finish_reason") == "content_filter") return Got.Bad(AiReply.Refused("안전 필터가 답을 막았어요"))
        val usage = o.optJSONObject("usage")
        return Got.Fine(message, usage?.optLong("prompt_tokens") ?: 0, usage?.optLong("completion_tokens") ?: 0)
    }
}
