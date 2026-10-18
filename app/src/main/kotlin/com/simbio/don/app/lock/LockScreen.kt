package com.simbio.don.app.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.simbio.don.app.ui.DonColors
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.UiIcon
import kotlinx.coroutines.delay

/** 숫자 4자리 입력 (점 4개 + 숫자 판). [error]가 있으면 빨간 글로 */
@Composable
fun PinPad(title: String, message: String?, error: Boolean, enabled: Boolean = true, onPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(DonColors.Bg).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Icon(UiIcon.LOCK, 36.dp, DonColors.Real)
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(message ?: " ", fontSize = 14.sp, color = if (error) DonColors.Warn else DonColors.InkSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(AppLock.LENGTH) { i ->
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < pin.length) DonColors.Real else DonColors.Line))
            }
        }
        Spacer(Modifier.weight(1f))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(64.dp).clip(CircleShape)
                            .let { m ->
                                if (k.isEmpty() || !enabled) m else m.clickable(role = Role.Button) {
                                    if (k == "⌫") {
                                        pin = pin.dropLast(1)
                                    } else if (pin.length < AppLock.LENGTH) {
                                        pin += k
                                        if (pin.length == AppLock.LENGTH) {
                                            val done = pin
                                            pin = ""
                                            onPin(done)
                                        }
                                    }
                                }
                            }
                            .semantics { if (k == "⌫") contentDescription = "지우기" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = if (enabled) DonColors.Ink else DonColors.InkFaint)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 앱을 열 때 보이는 잠금 화면 */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var waitMs by remember { mutableLongStateOf(AppLock.waitLeft(context)) }
    LaunchedEffect(waitMs > 0) {
        while (waitMs > 0) {
            message = "${(waitMs + 999) / 1000}초 뒤에 다시 해 보세요"
            error = true
            delay(1_000)
            waitMs = AppLock.waitLeft(context)
        }
        if (error && message?.endsWith("다시 해 보세요") == true) message = null
    }
    PinPad("암호를 넣어 주세요", message ?: "돈't는 잠겨 있어요", error, enabled = waitMs == 0L) { pin ->
        when (val r = AppLock.check(context, pin)) {
            AppLock.Result.Ok -> onUnlock()
            is AppLock.Result.Wrong -> { message = "암호가 달라요 (${r.left}번 남음)"; error = true }
            is AppLock.Result.Wait -> waitMs = r.ms
        }
    }
}

/** 새 암호 정하기: 두 번 넣어 확인 */
@Composable
fun SetPinScreen(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            PinPad(
                if (first == null) "새 암호 4자리" else "한 번 더 넣어 주세요",
                message ?: "암호를 잊으면 앱 데이터를 지워야 해요. 백업 파일을 만들어 두세요.",
                message != null,
            ) { pin ->
                val f = first
                when {
                    f == null -> { first = pin; message = null }
                    f == pin -> onDone(pin)
                    else -> { first = null; message = "두 암호가 달라요. 처음부터 다시 넣어 주세요" }
                }
            }
        }
        Text(
            "취소",
            fontSize = 15.sp,
            color = DonColors.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(DonColors.Bg).clickable(role = Role.Button, onClick = onCancel).padding(16.dp),
        )
    }
}
