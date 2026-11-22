package com.simbio.don.app.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaTool
import com.anthropic.models.beta.messages.BetaToolResultBlockParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration

/**
 * Anthropic 공식 SDK로 Claude에 보낸다. 사용자 키로, 요청 하나에 한 번 (대화는 [turn]으로 한 걸음씩).
 * 안전 판단으로 거절되면 서버가 권하는 다른 모델로 다시 해 보게(fallbacks) 한다 — 그래도 거절이면 그대로 알린다.
 */
class ClaudeBackend(
    private val apiKey: String,
    /** 테스트에서만 바꾼다 (기본은 api.anthropic.com) */
    private val baseUrl: String? = null,
) : AiBackend {
    private fun client() = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(90)).maxRetries(2)
        .apply { baseUrl?.let { baseUrl(it) } }
        .build()

    /** 안전 판단으로 거절되면 서버가 다른 모델로 다시 해 보게 하는 모델 (Opus·Sonnet) */
    private fun fallback(model: String) = AiModel.entries.firstOrNull { it.id == model }?.fallback ?: false

    override suspend fun send(model: String, req: AiRequest): AiReply = withContext(Dispatchers.IO) {
        val client = client()
        try {
            val b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(req.maxTokens)
                .system(req.system)
                .addUserMessage(req.user)
            if (req.quick || req.schema != null) {
                val oc = BetaOutputConfig.builder()
                if (req.quick) oc.effort(BetaOutputConfig.Effort.LOW)
                req.schema?.let { s ->
                    val schema = BetaJsonOutputFormat.Schema.builder().putAllAdditionalProperties(s.mapValues { (_, v) -> JsonValue.from(v) }).build()
                    oc.format(BetaJsonOutputFormat.builder().schema(schema).build())
                }
                b.outputConfig(oc.build())
            }
            if (fallback(model)) {
                b.addBeta(FALLBACK_BETA)
                b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            val msg = client.beta().messages().create(b.build())
            val stop = msg.stopReason().orElse(null)
            // 거절은 내용을 읽기 전에 먼저 본다
            if (stop == BetaStopReason.REFUSAL) {
                return@withContext AiReply.Refused(msg.stopDetails().flatMap { it.explanation() }.orElse("이 요청은 답하지 않기로 했어요"))
            }
            val text = msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            if (stop == BetaStopReason.MAX_TOKENS && req.schema != null) {
                return@withContext AiReply.Failed("답이 너무 길어 잘렸어요. 조금 짧게 다시 해 주세요")
            }
            AiReply.Ok(text, msg.usage().inputTokens(), msg.usage().outputTokens())
        } catch (e: Exception) {
            AiReply.Failed(problem(e))
        } finally {
            client.close()
        }
    }

    /**
     * 도구를 쓰는 대화 한 걸음. AI의 답은 받은 그대로([BetaMessage]) 다음 요청에 돌려 보낸다 — 생각 블록을 바꾸지 않게.
     */
    override suspend fun turn(model: String, req: AgentRequest): AgentReply = withContext(Dispatchers.IO) {
        val client = client()
        try {
            val b = MessageCreateParams.builder().model(model).maxTokens(req.maxTokens).system(req.system)
            req.tools.forEach { t ->
                val props = BetaTool.InputSchema.Properties.builder()
                t.properties.forEach { (k, v) -> props.putAdditionalProperty(k, JsonValue.from(v)) }
                b.addTool(
                    BetaTool.builder().name(t.name).description(t.description)
                        .inputSchema(BetaTool.InputSchema.builder().properties(props.build()).required(t.required).build())
                        .build(),
                )
            }
            req.turns.forEach { turn ->
                when (turn) {
                    is Turn.User -> b.addUserMessage(turn.text)
                    is Turn.Model -> (turn.native as? BetaMessage)?.let { b.addMessage(it) } ?: b.addAssistantMessage(turn.text.ifBlank { "…" })
                    is Turn.Results -> b.addUserMessageOfBetaContentBlockParams(
                        turn.results.map { r ->
                            BetaContentBlockParam.ofToolResult(
                                BetaToolResultBlockParam.builder().toolUseId(r.callId).content(r.content).isError(r.isError).build(),
                            )
                        },
                    )
                }
            }
            if (fallback(model)) {
                b.addBeta(FALLBACK_BETA)
                b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            val msg = client.beta().messages().create(b.build())
            if (msg.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
                return@withContext AgentReply.Refused(msg.stopDetails().flatMap { it.explanation() }.orElse("이 요청은 답하지 않기로 했어요"))
            }
            val text = msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            val calls = msg.content().mapNotNull { it.toolUse().orElse(null) }.map { u ->
                val args = runCatching { AiJson.wrap(u._input().convert(Map::class.java)) as JSONObject }.getOrElse { JSONObject() }
                ToolCall(u.id(), u.name(), args)
            }
            AgentReply.Ok(Turn.Model(text, calls, msg), msg.usage().inputTokens(), msg.usage().outputTokens())
        } catch (e: Exception) {
            AgentReply.Failed(problem(e))
        } finally {
            client.close()
        }
    }

    private fun problem(e: Exception): String = when (e) {
        is UnauthorizedException -> "API 키가 맞지 않아요. 설정에서 다시 넣어 주세요"
        is PermissionDeniedException -> "이 키로는 쓸 수 없는 모델이에요. 다른 모델을 골라 보세요"
        is RateLimitException -> "요청이 많아요. 잠시 뒤 다시 해 주세요"
        is AnthropicServiceException -> "AI 서버가 답하지 못했어요 (${e.statusCode()}). 잠시 뒤 다시 해 주세요"
        is AnthropicIoException -> "인터넷 연결을 확인해 주세요"
        else -> "AI 요청이 안 됐어요: ${e.message ?: e.javaClass.simpleName}"
    }

    private companion object {
        /** 서버 쪽 다른 모델로 다시 하기 ("default": 거절 종류에 맞는 모델을 서버가 고른다) */
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}
