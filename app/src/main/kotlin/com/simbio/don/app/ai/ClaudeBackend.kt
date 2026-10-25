package com.simbio.don.app.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * Anthropic 공식 SDK로 Claude에 보낸다. 사용자 키로, 요청 하나에 한 번.
 * 안전 판단으로 거절되면 서버가 권하는 다른 모델로 다시 해 보게(fallbacks) 한다 — 그래도 거절이면 그대로 알린다.
 */
class ClaudeBackend(
    private val apiKey: String,
    /** 테스트에서만 바꾼다 (기본은 api.anthropic.com) */
    private val baseUrl: String? = null,
) : AiBackend {
    override suspend fun send(model: AiModel, req: AiRequest): AiReply = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofSeconds(90)).maxRetries(2)
            .apply { baseUrl?.let { baseUrl(it) } }
            .build()
        try {
            val b = MessageCreateParams.builder()
                .model(model.id)
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
            if (model.fallback) {
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
        } catch (e: UnauthorizedException) {
            AiReply.Failed("API 키가 맞지 않아요. 설정에서 다시 넣어 주세요")
        } catch (e: PermissionDeniedException) {
            AiReply.Failed("이 키로는 쓸 수 없는 모델이에요. 다른 모델을 골라 보세요")
        } catch (e: RateLimitException) {
            AiReply.Failed("요청이 많아요. 잠시 뒤 다시 해 주세요")
        } catch (e: AnthropicServiceException) {
            AiReply.Failed("AI 서버가 답하지 못했어요 (${e.statusCode()}). 잠시 뒤 다시 해 주세요")
        } catch (e: AnthropicIoException) {
            AiReply.Failed("인터넷 연결을 확인해 주세요")
        } catch (e: Exception) {
            AiReply.Failed("AI 요청이 안 됐어요: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            client.close()
        }
    }

    private companion object {
        /** 서버 쪽 다른 모델로 다시 하기 ("default": 거절 종류에 맞는 모델을 서버가 고른다) */
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}
