package com.simbio.don.notify

import kotlin.test.Test
import kotlin.test.assertEquals

class KeywordRuleCodecTest {
    @Test
    fun `저장했다 불러오면 그대로다`() {
        val rules = AppRule.DEFAULT_KEYWORD_RULES
        assertEquals(rules, KeywordRuleCodec.decode(KeywordRuleCodec.encode(rules)))
    }

    @Test
    fun `빈 키워드와 모르는 동작은 버린다`() {
        assertEquals(
            listOf(KeywordRule(listOf("이체", "송금"), RuleAction.SPEND)),
            KeywordRuleCodec.decode("SPEND= 이체, ,송금 ;UNKNOWN=x;broken"),
        )
    }
}
