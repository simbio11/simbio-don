package com.simbio.don.budget

/**
 * 자동 기록이 얼마나 알아서 되고 있는지. 알림으로 적힌 기록 [total]건 중 사용자가 확인해 줘야 하는 것([needs])이 몇 건인지.
 * 처음엔 많이 묻고, 알려 준 만큼 배워서 [percent]가 올라간다.
 */
data class AutoRate(val total: Int, val needs: Int) {
    /** 알아서 기록된 비율 (기록이 없으면 100) */
    val percent: Int get() = if (total <= 0) 100 else ((total - needs.coerceIn(0, total)) * 100 / total)

    /** 짧은 말 */
    val label: String
        get() = when {
            total <= 0 -> "알림 기록이 쌓이면 알아서 배워요"
            needs <= 0 -> "최근 ${total}건이 모두 알아서 기록됐어요"
            else -> "최근 ${total}건 중 ${total - needs}건은 알아서 · ${needs}건 확인 필요"
        }
}
