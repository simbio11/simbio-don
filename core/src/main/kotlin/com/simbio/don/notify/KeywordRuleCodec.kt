package com.simbio.don.notify

/** 키워드 규칙 ↔ 저장용 문자열. 예: "IGNORE=광고,혜택;REFUND=취소,환불" */
object KeywordRuleCodec {
    fun encode(rules: List<KeywordRule>): String =
        rules.joinToString(";") { r -> "${r.action.name}=" + r.keywords.joinToString(",") { it.trim() } }

    fun decode(text: String): List<KeywordRule> = text.split(";").mapNotNull { part ->
        val (name, words) = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val action = RuleAction.entries.firstOrNull { it.name == name.trim() } ?: return@mapNotNull null
        KeywordRule(splitKeywords(words), action)
    }

    /** 쉼표로 나눈 키워드 입력을 정리한다. 빈 칸은 버린다. */
    fun splitKeywords(input: String): List<String> = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
