package com.simbio.don.app.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * 도구 하나 (가계부 하네스가 AI에게 주는 것). [properties]는 JSON 스키마의 properties, [required]는 꼭 넣을 것.
 * 회사마다 모양이 달라 각 백엔드가 바꿔 보낸다.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val properties: Map<String, Map<String, Any>> = emptyMap(),
    val required: List<String> = emptyList(),
)

/** AI가 부른 도구 */
data class ToolCall(val id: String, val name: String, val args: JSONObject)

/** 도구를 돌린 결과 (AI에게 돌려줄 글) */
data class ToolOutcome(val callId: String, val name: String, val content: String, val isError: Boolean = false)

/** 대화 한 칸 */
sealed interface Turn {
    data class User(val text: String) : Turn

    /**
     * AI의 답. [native]는 그 회사가 보낸 원래 모양 (생각 블록·서명까지) — 다음 요청에 바꾸지 않고 그대로 돌려 보낸다.
     */
    data class Model(val text: String, val calls: List<ToolCall> = emptyList(), val native: Any? = null) : Turn

    data class Results(val results: List<ToolOutcome>) : Turn
}

data class AgentRequest(
    val system: String,
    val turns: List<Turn>,
    val tools: List<ToolSpec>,
    val maxTokens: Long = 8_000,
)

sealed interface AgentReply {
    data class Ok(val model: Turn.Model, val inputTokens: Long = 0, val outputTokens: Long = 0) : AgentReply
    data class Refused(val reason: String) : AgentReply
    data class Failed(val message: String) : AgentReply
}

/** 백엔드들이 같이 쓰는 것 */
internal object AiJson {
    /** Map·List → org.json */
    fun wrap(v: Any?): Any? = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply { v.forEach { (k, x) -> put(k.toString(), wrap(x)) } }
        is Iterable<*> -> JSONArray().apply { v.forEach { put(wrap(it)) } }
        else -> v
    }

    /** 도구 인자 스키마 (보통 JSON 스키마) */
    fun schema(t: ToolSpec): JSONObject = JSONObject()
        .put("type", "object")
        .put("properties", wrap(t.properties))
        .put("required", JSONArray(t.required))

    /** JSON 모양 답을 강제로 못 하는 곳: 안내문에 모양을 붙인다 */
    fun systemWithSchema(system: String, schema: Map<String, Any>?): String =
        if (schema == null) system else "$system\n\n반드시 아래 JSON 스키마에 맞는 JSON 하나만 답해 (설명·코드 블록 없이):\n${wrap(schema)}"

    /** ```json ... ``` 로 감싸 왔으면 벗긴다 */
    fun stripFence(text: String): String {
        val t = text.trim()
        if (!t.startsWith("```")) return t
        return t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
    }
}
