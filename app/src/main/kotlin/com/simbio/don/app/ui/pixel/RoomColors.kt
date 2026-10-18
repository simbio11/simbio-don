package com.simbio.don.app.ui.pixel

// 생성된 파일: tools/pixelart/make_art.py (palette.MARKERS·THEMES) — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 방 인테리어 색 바꾸기: 방 그림(px_room_*)에서 표식 색을 찾아 고른 색 묶음으로 바꾼다 (밝음 → 어둠). */
object RoomColors {
    val WALL = intArrayOf(0xFFF3E4C8.toInt(), 0xFFEFDCBD.toInt(), 0xFFE9D5B2.toInt(), 0xFFE4CFA8.toInt())
    val FLOOR = intArrayOf(0xFFDCAB76.toInt(), 0xFFCD9762.toInt(), 0xFFC18A58.toInt(), 0xFFA26D43.toInt())
    val RUG = intArrayOf(0xFFF2D68C.toInt(), 0xFF7FB5A4.toInt(), 0xFF5E9584.toInt(), 0xFF4A7A6C.toInt())
    val CURTAIN = intArrayOf(0xFFF4A08C.toInt(), 0xFFE7806C.toInt(), 0xFFC35E51.toInt(), 0xFFA24A43.toInt())
    val BEDDING = intArrayOf(0xFFA5BFF0.toInt(), 0xFF7FA0E0.toInt(), 0xFF5D7FC4.toInt(), 0xFF4A68A8.toInt())

    /** 고를 수 있는 색 (첫째가 기본) */
    val walls: List<Pair<String, IntArray>> = listOf(
        "크림" to intArrayOf(0xFFF3E4C8.toInt(), 0xFFEFDCBD.toInt(), 0xFFE9D5B2.toInt(), 0xFFE4CFA8.toInt()),
        "민트" to intArrayOf(0xFFE2F1E4.toInt(), 0xFFD6EADB.toInt(), 0xFFCBE2D1.toInt(), 0xFFC0D9C6.toInt()),
        "라벤더" to intArrayOf(0xFFEBE4F3.toInt(), 0xFFE1D8EE.toInt(), 0xFFD7CDE6.toInt(), 0xFFCDC1DE.toInt()),
        "하늘" to intArrayOf(0xFFDEEDF7.toInt(), 0xFFD1E4F2.toInt(), 0xFFC5DBEC.toInt(), 0xFFB9D1E5.toInt()),
        "분홍" to intArrayOf(0xFFF9E2E2.toInt(), 0xFFF4D5D7.toInt(), 0xFFEDC9CC.toInt(), 0xFFE5BDC1.toInt()),
        "회색" to intArrayOf(0xFFE9E8E6.toInt(), 0xFFDFDEDB.toInt(), 0xFFD5D3D0.toInt(), 0xFFCBC8C4.toInt()),
    )
    val floors: List<Pair<String, IntArray>> = listOf(
        "원목" to intArrayOf(0xFFDCAB76.toInt(), 0xFFCD9762.toInt(), 0xFFC18A58.toInt(), 0xFFA26D43.toInt()),
        "진한 원목" to intArrayOf(0xFFA8754C.toInt(), 0xFF93623D.toInt(), 0xFF845735.toInt(), 0xFF5E3B23.toInt()),
        "화이트 오크" to intArrayOf(0xFFF0DFC2.toInt(), 0xFFE4CFAD.toInt(), 0xFFD9C29D.toInt(), 0xFFB59C78.toInt()),
        "회색 마루" to intArrayOf(0xFFCFC9C1.toInt(), 0xFFBDB6AD.toInt(), 0xFFADA59B.toInt(), 0xFF857D74.toInt()),
        "체리" to intArrayOf(0xFFD99470.toInt(), 0xFFC47D5A.toInt(), 0xFFB26E4C.toInt(), 0xFF874A32.toInt()),
        "대리석" to intArrayOf(0xFFF5F3F0.toInt(), 0xFFE8E5E1.toInt(), 0xFFDAD6D1.toInt(), 0xFFB4AEA7.toInt()),
    )
    val rugs: List<Pair<String, IntArray>> = listOf(
        "민트" to intArrayOf(0xFFF2D68C.toInt(), 0xFF7FB5A4.toInt(), 0xFF5E9584.toInt(), 0xFF4A7A6C.toInt()),
        "빨강" to intArrayOf(0xFFF3D39A.toInt(), 0xFFCF5F57.toInt(), 0xFFA8443F.toInt(), 0xFF84322F.toInt()),
        "남색" to intArrayOf(0xFFE8D7A8.toInt(), 0xFF5B6B9E.toInt(), 0xFF45537F.toInt(), 0xFF333F63.toInt()),
        "겨자" to intArrayOf(0xFFFFF0C4.toInt(), 0xFFDDB04F.toInt(), 0xFFB98C34.toInt(), 0xFF926B23.toInt()),
        "분홍" to intArrayOf(0xFFFFFFFF.toInt(), 0xFFF2A9BB.toInt(), 0xFFD9879C.toInt(), 0xFFB9687F.toInt()),
        "회색" to intArrayOf(0xFFF4F0E8.toInt(), 0xFFAAA6A1.toInt(), 0xFF8C8883.toInt(), 0xFF6F6B67.toInt()),
    )
    val curtains: List<Pair<String, IntArray>> = listOf(
        "코랄" to intArrayOf(0xFFF4A08C.toInt(), 0xFFE7806C.toInt(), 0xFFC35E51.toInt(), 0xFFA24A43.toInt()),
        "하늘" to intArrayOf(0xFFBFE0F7.toInt(), 0xFF92C4EC.toInt(), 0xFF6FA2D3.toInt(), 0xFF5584B7.toInt()),
        "노랑" to intArrayOf(0xFFFFE8A3.toInt(), 0xFFF6CD63.toInt(), 0xFFD8A840.toInt(), 0xFFB5862D.toInt()),
        "초록" to intArrayOf(0xFFCBE2B6.toInt(), 0xFFA9CB8F.toInt(), 0xFF87AD70.toInt(), 0xFF6A8F57.toInt()),
        "보라" to intArrayOf(0xFFDAC7F3.toInt(), 0xFFBB9EE7.toInt(), 0xFF987CCA.toInt(), 0xFF7C61AD.toInt()),
        "리넨" to intArrayOf(0xFFFBF8F2.toInt(), 0xFFECE4D6.toInt(), 0xFFD8CCB9.toInt(), 0xFFBDAE98.toInt()),
    )
    val beddings: List<Pair<String, IntArray>> = listOf(
        "파랑" to intArrayOf(0xFFA5BFF0.toInt(), 0xFF7FA0E0.toInt(), 0xFF5D7FC4.toInt(), 0xFF4A68A8.toInt()),
        "분홍" to intArrayOf(0xFFFFD1DC.toInt(), 0xFFF4A6BA.toInt(), 0xFFDA859C.toInt(), 0xFFBD6A83.toInt()),
        "노랑" to intArrayOf(0xFFFFF0B8.toInt(), 0xFFF5D679.toInt(), 0xFFDAB456.toInt(), 0xFFB8923F.toInt()),
        "초록" to intArrayOf(0xFFCFE9BD.toInt(), 0xFFA8D190.toInt(), 0xFF85B270.toInt(), 0xFF69955A.toInt()),
        "회색" to intArrayOf(0xFFE6E4E1.toInt(), 0xFFC9C6C2.toInt(), 0xFFAAA6A1.toInt(), 0xFF8B8782.toInt()),
        "보라" to intArrayOf(0xFFDDCCF6.toInt(), 0xFFBFA6EA.toInt(), 0xFF9F83D2.toInt(), 0xFF8268B6.toInt()),
    )
}
