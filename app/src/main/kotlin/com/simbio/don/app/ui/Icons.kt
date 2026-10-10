package com.simbio.don.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 하단 탭용 막대그래프 아이콘 (icons-extended를 쓰지 않으려고 직접 그림). */
val ChartIcon: ImageVector by lazy {
    ImageVector.Builder("chart", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 20f); lineTo(4f, 12f); lineTo(8f, 12f); lineTo(8f, 20f); close()
            moveTo(10f, 20f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); close()
            moveTo(16f, 20f); lineTo(16f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); close()
        }
    }.build()
}

/** 내역 탭용 목록 아이콘. */
val ListIcon: ImageVector by lazy {
    ImageVector.Builder("list", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 6f); lineTo(6f, 6f); lineTo(6f, 8f); lineTo(4f, 8f); close()
            moveTo(8f, 6f); lineTo(20f, 6f); lineTo(20f, 8f); lineTo(8f, 8f); close()
            moveTo(4f, 11f); lineTo(6f, 11f); lineTo(6f, 13f); lineTo(4f, 13f); close()
            moveTo(8f, 11f); lineTo(20f, 11f); lineTo(20f, 13f); lineTo(8f, 13f); close()
            moveTo(4f, 16f); lineTo(6f, 16f); lineTo(6f, 18f); lineTo(4f, 18f); close()
            moveTo(8f, 16f); lineTo(20f, 16f); lineTo(20f, 18f); lineTo(8f, 18f); close()
        }
    }.build()
}
