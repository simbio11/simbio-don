package com.simbio.don.game

/** 동물 머리 위에 잠깐 뜨는 말풍선 */
enum class PetBubble { HEART, NOTE, FOOD, BORED, SWEAT, ANGER, ZZZ }

/** 얼마나 움직이나: 잠 · 처짐(거의 앉아 있음) · 보통 · 신남 */
enum class PetEnergy { SLEEP, LOW, NORMAL, HIGH }

/**
 * 지금 동물의 마음: 어느 표정 그림인지, 머리 위 말풍선, 움직이는 정도, 한 줄 말.
 * [expr]는 그림 이름 (joy smile calm worry sad angry tease sleep).
 */
data class PetFeeling(val expr: String, val bubble: PetBubble?, val energy: PetEnergy, val line: String)

/**
 * 진짜 반려동물처럼: 밤늦게는 자고, 밥을 안 주면 배고파하고, 안 놀아 주면 심심해하고,
 * 내가 돈을 막 쓰면(가계부 기분) 같이 걱정하거나 화낸다. 다 괜찮으면 기분 좋게 돌아다닌다.
 *
 * 우선순위: 잠 > 많이 배고픔 > 지갑이 바닥(화남) > 배고픔 > 돈 걱정 > 심심함 > 지저분함 > 기분 좋음 > 평온.
 */
object PetFeelings {
    /** 23시~6시는 잔다 */
    fun asleep(hour: Int): Boolean = hour >= 23 || hour < 6

    /** 21~22시는 졸려서 덜 움직인다 */
    private fun drowsy(hour: Int): Boolean = hour in 21..22

    fun of(mood: Mood, pet: PetState?, hour: Int, name: String): PetFeeling {
        val ga = name + PetCare.josa(name, "이", "가")
        val s = pet ?: PetState()
        val sleepy = drowsy(hour)
        fun calmEnergy(e: PetEnergy) = if (sleepy && e != PetEnergy.SLEEP) PetEnergy.LOW else e
        return when {
            asleep(hour) -> PetFeeling("sleep", PetBubble.ZZZ, PetEnergy.SLEEP, "$ga 쿨쿨 자고 있어요")
            s.full < 15 -> PetFeeling("sad", PetBubble.FOOD, PetEnergy.LOW, "$ga 많이 배고파해요")
            mood == Mood.ANGRY -> PetFeeling("angry", PetBubble.ANGER, calmEnergy(PetEnergy.NORMAL), "$ga 텅 빈 지갑을 보고 화났어요")
            s.hungry -> PetFeeling("worry", PetBubble.FOOD, calmEnergy(PetEnergy.NORMAL), "$ga 밥그릇 앞에서 기다려요")
            mood == Mood.SAD -> PetFeeling("sad", PetBubble.SWEAT, PetEnergy.LOW, "$ga 줄어드는 지갑에 시무룩해요")
            mood == Mood.WORRIED -> PetFeeling("worry", PetBubble.SWEAT, calmEnergy(PetEnergy.NORMAL), "$ga 쓰는 속도가 빨라서 조마조마해요")
            s.bored -> PetFeeling("worry", PetBubble.BORED, PetEnergy.LOW, "$ga 심심해서 놀아 달래요")
            s.dirty -> PetFeeling("worry", PetBubble.SWEAT, calmEnergy(PetEnergy.NORMAL), "$ga 꼬질꼬질해서 씻고 싶대요")
            mood == Mood.CHEER -> PetFeeling("tease", PetBubble.NOTE, calmEnergy(PetEnergy.NORMAL), "$ga 다시 해 보재요")
            mood == Mood.JOYFUL -> PetFeeling("joy", PetBubble.HEART, calmEnergy(PetEnergy.HIGH), "$ga 신나서 꼬리를 흔들어요")
            mood == Mood.HAPPY || (s.full >= 70 && s.joy >= 70) ->
                PetFeeling("smile", PetBubble.NOTE, calmEnergy(PetEnergy.NORMAL), "$ga 기분이 좋아요")
            sleepy -> PetFeeling("calm", null, PetEnergy.LOW, "$ga 졸려 해요")
            else -> PetFeeling("calm", null, PetEnergy.NORMAL, name + PetCare.josa(name, "은", "는") + " 평온해요")
        }
    }

    /** 쓰다듬거나 돌봐 줬을 때 잠깐 짓는 얼굴 */
    val pleased = PetFeeling("joy", PetBubble.HEART, PetEnergy.HIGH, "")
}
