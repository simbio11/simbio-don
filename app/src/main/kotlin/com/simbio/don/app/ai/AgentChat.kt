package com.simbio.don.app.ai

import com.simbio.don.app.data.AgentAction
import com.simbio.don.app.data.LedgerHarness

/** 대화 화면에 보이는 한 줄 */
sealed interface ChatLine {
    data class You(val text: String) : ChatLine
    data class Bot(val text: String, val usage: String = "") : ChatLine

    /** AI가 가계부를 찾아본 것 — [sent]는 AI에게 보낸 결과 그대로 (눌러서 보기) */
    data class Tool(val name: String, val title: String, val sent: String, val failed: Boolean = false) : ChatLine

    /** 고치기 제안 (적용·취소). [state]: 0 기다림, 1 적용함, 2 취소함 */
    data class Proposal(val action: AgentAction, val state: Int = 0) : ChatLine
    data class Problem(val text: String) : ChatLine
}

/**
 * AI 대화 한 판: 사용자 말 → AI → (도구 부름 → 하네스가 폰에서 돌림 → 결과 돌려줌)을 끝날 때까지 되풀이.
 * 도구는 [LedgerHarness]만 — 가계부를 바꾸는 도구는 없고 제안만 한다.
 */
class AgentChat(
    private val harness: LedgerHarness,
    private val send: suspend (AgentRequest) -> AgentReply,
    private val today: () -> java.time.LocalDate = java.time.LocalDate::now,
) {
    private val turns = mutableListOf<Turn>()

    /** [text]를 보내고 새로 생긴 줄들을 [emit]으로 하나씩 내보낸다 */
    suspend fun ask(text: String, emit: (ChatLine) -> Unit) {
        turns += Turn.User(text.trim())
        repeat(MAX_STEPS) {
            val before = harness.proposals.size
            when (val r = send(AgentRequest(harness.system(today()), turns.toList(), harness.tools))) {
                is AgentReply.Failed -> {
                    emit(ChatLine.Problem(r.message))
                    rollback()
                    return
                }
                is AgentReply.Refused -> {
                    emit(ChatLine.Problem(r.reason))
                    rollback()
                    return
                }
                is AgentReply.Ok -> {
                    turns += r.model
                    val usage = if (r.inputTokens + r.outputTokens == 0L) "" else "보낸 ${"%,d".format(r.inputTokens)} · 받은 ${"%,d".format(r.outputTokens)} 토큰"
                    if (r.model.calls.isEmpty()) {
                        emit(ChatLine.Bot(r.model.text.trim().ifBlank { "(답이 비어 있어요)" }, usage))
                        return
                    }
                    if (r.model.text.isNotBlank()) emit(ChatLine.Bot(r.model.text.trim()))
                    val results = r.model.calls.map { c ->
                        val out = harness.run(c)
                        emit(ChatLine.Tool(c.name, title(c.name, out.content, out.isError), out.content, out.isError))
                        out
                    }
                    harness.proposals.drop(before).forEach { emit(ChatLine.Proposal(it)) }
                    turns += Turn.Results(results)
                }
            }
        }
        emit(ChatLine.Problem("도구를 너무 많이 불러서 멈췄어요. 질문을 조금 좁혀 주세요"))
    }

    /** 실패한 질문은 대화에서 뺀다 (다음에 다시 물을 수 있게) */
    private fun rollback() {
        val i = turns.indexOfLast { it is Turn.User }
        if (i >= 0) while (turns.size > i) turns.removeAt(turns.lastIndex)
    }

    /** 사용자가 제안에 답했다 — 다음 질문 때 AI가 알게 */
    fun note(text: String) {
        turns += Turn.User("(알림) $text")
        turns += Turn.Model("알겠어요.")
    }

    fun clear() {
        turns.clear()
        harness.proposals.clear()
    }

    companion object {
        const val MAX_STEPS = 8

        fun title(name: String, out: String, failed: Boolean): String {
            if (failed) return "도구 오류: ${out.take(60)}"
            val count = Regex("\"count\":(\\d+)").find(out)?.groupValues?.get(1)
            return when (name) {
                "overview" -> "이번 달 살펴봄"
                "search_entries" -> "기록 찾음 · ${count ?: "?"}건"
                "totals" -> "합계 냄"
                "accounts" -> "자산 잔액 봄"
                "categories" -> "분류 목록 봄"
                "checkup" -> "비서 점검 봄"
                "propose_add", "propose_edit", "propose_delete" -> "고칠 것 제안"
                else -> name
            }
        }
    }
}
