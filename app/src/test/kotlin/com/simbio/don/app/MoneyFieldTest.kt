package com.simbio.don.app

import androidx.compose.ui.text.AnnotatedString
import com.simbio.don.app.ui.ThousandsTransformation
import org.junit.Assert.assertEquals
import org.junit.Test

/** 금액 입력칸의 쉼표 표시와 커서 위치 */
class MoneyFieldTest {
    @Test
    fun 세_자리마다_쉼표를_넣고_커서는_숫자_기준으로_옮긴다() {
        val t = ThousandsTransformation.filter(AnnotatedString("1234567"))
        assertEquals("1,234,567", t.text.text)
        val m = t.offsetMapping
        assertEquals(0, m.originalToTransformed(0))
        assertEquals(2, m.originalToTransformed(1))
        assertEquals(3, m.originalToTransformed(2))
        assertEquals(9, m.originalToTransformed(7))
        assertEquals(1, m.transformedToOriginal(2))
        assertEquals(7, m.transformedToOriginal(9))

        assertEquals("999", ThousandsTransformation.filter(AnnotatedString("999")).text.text)
        assertEquals("", ThousandsTransformation.filter(AnnotatedString("")).text.text)
        assertEquals("1,000", ThousandsTransformation.filter(AnnotatedString("1000")).text.text)
    }
}
