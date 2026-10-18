package com.simbio.don.app.lock

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 앱 잠금 (숫자 4자리 암호). 암호는 그대로 저장하지 않고 소금을 친 해시로만 이 폰에 둔다.
 * 백업 파일에는 넣지 않는다 (복원한 폰에서 잠겨 못 여는 일이 없게).
 *
 * 다섯 번 틀리면 30초 기다린다. 암호를 잊으면 앱 데이터를 지워야 한다 (백업 파일로 되살릴 수 있다).
 */
object AppLock {
    private const val PREFS = "app_lock"
    private const val KEY_HASH = "hash"
    private const val KEY_SALT = "salt"
    private const val KEY_FAILS = "fails"
    private const val KEY_WAIT_UNTIL = "wait_until"

    const val LENGTH = 4
    const val MAX_FAILS = 5
    const val WAIT_MS = 30_000L

    /** 앱을 떠났다 이만큼 지나 돌아오면 다시 잠근다 */
    const val RELOCK_AFTER_MS = 30_000L

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(c: Context): Boolean = prefs(c).contains(KEY_HASH)

    fun isValidPin(pin: String): Boolean = pin.length == LENGTH && pin.all(Char::isDigit)

    fun setPin(c: Context, pin: String) {
        require(isValidPin(pin)) { "암호는 숫자 ${LENGTH}자리" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit()
            .putString(KEY_SALT, salt.hex())
            .putString(KEY_HASH, hash(salt, pin))
            .remove(KEY_FAILS)
            .remove(KEY_WAIT_UNTIL)
            .apply()
    }

    fun turnOff(c: Context) = prefs(c).edit().clear().apply()

    /** 남은 기다림 (밀리초, 0이면 바로 입력 가능) */
    fun waitLeft(c: Context, now: Long = System.currentTimeMillis()): Long =
        (prefs(c).getLong(KEY_WAIT_UNTIL, 0) - now).coerceAtLeast(0)

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val left: Int) : Result
        data class Wait(val ms: Long) : Result
    }

    fun check(c: Context, pin: String, now: Long = System.currentTimeMillis()): Result {
        val p = prefs(c)
        waitLeft(c, now).takeIf { it > 0 }?.let { return Result.Wait(it) }
        val salt = p.getString(KEY_SALT, null)?.unhex() ?: return Result.Ok
        if (hash(salt, pin) == p.getString(KEY_HASH, null)) {
            p.edit().remove(KEY_FAILS).remove(KEY_WAIT_UNTIL).apply()
            return Result.Ok
        }
        val fails = p.getInt(KEY_FAILS, 0) + 1
        return if (fails >= MAX_FAILS) {
            p.edit().putInt(KEY_FAILS, 0).putLong(KEY_WAIT_UNTIL, now + WAIT_MS).apply()
            Result.Wait(WAIT_MS)
        } else {
            p.edit().putInt(KEY_FAILS, fails).apply()
            Result.Wrong(MAX_FAILS - fails)
        }
    }

    private fun hash(salt: ByteArray, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var h = salt + pin.toByteArray()
        // 4자리라 쉽게 다 넣어 볼 수 있으니 여러 번 돌려 조금이라도 느리게
        repeat(10_000) { h = md.digest(h) }
        return h.hex()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    private fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
