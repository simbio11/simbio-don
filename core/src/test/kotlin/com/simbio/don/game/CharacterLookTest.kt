package com.simbio.don.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CharacterLookTest {
    @Test
    fun `저장한 모습을 그대로 읽는다`() {
        val look = CharacterLook.FEMALE.copy(hair = "bob", hairColor = "밝은 갈색", hat = "beanie", acc = "glasses", lashes = false)
        assertEquals(look, CharacterLook.decode(look.encode()))
        assertEquals(CharacterLook.MALE, CharacterLook.decode(CharacterLook.MALE.encode()))
    }

    @Test
    fun `비었거나 망가진 값은 기본 모습이다`() {
        assertEquals(CharacterLook.MALE, CharacterLook.decode(null))
        assertEquals(CharacterLook.MALE, CharacterLook.decode(""))
        assertEquals(CharacterLook.MALE, CharacterLook.decode("아무말;=;;x"))
    }

    @Test
    fun `모르는 칸은 그 성별 기본값으로 채운다`() {
        val look = CharacterLook.decode("gender=FEMALE;hair=perm;lashes=맞음")
        assertEquals(Gender.FEMALE, look.gender)
        assertEquals("perm", look.hair)
        assertEquals(CharacterLook.FEMALE.top, look.top)
        assertEquals(true, look.lashes)
        assertNull(look.hat)
    }

    @Test
    fun `구분 글자가 들어간 이름은 지워서 저장한다`() {
        val look = CharacterLook(top = "a;b=c")
        assertEquals("abc", CharacterLook.decode(look.encode()).top)
    }
}
