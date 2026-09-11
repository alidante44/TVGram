package ir.tvgram.app.settings

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The lock that stands between someone picking up the remote and a signed-in
 * Telegram account.
 *
 * The passcode is never stored — only a salted PBKDF2 hash of it — so reading
 * the app's data does not reveal it. There is deliberately no recovery: a
 * forgotten passcode means clearing the app's data and signing in again, which
 * is the point of the lock.
 */
object Passcode {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    data class Hashed(val salt: String, val hash: String)

    fun hash(passcode: String): Hashed {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Hashed(salt = salt.encode(), hash = derive(passcode, salt).encode())
    }

    fun verify(passcode: String, stored: Hashed): Boolean {
        if (stored.salt.isEmpty() || stored.hash.isEmpty()) return false
        val salt = runCatching { stored.salt.decode() }.getOrNull() ?: return false
        val expected = runCatching { stored.hash.decode() }.getOrNull() ?: return false
        return derive(passcode, salt).constantTimeEquals(expected)
    }

    private fun derive(passcode: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance(ALGORITHM)
            .generateSecret(PBEKeySpec(passcode.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS))
            .encoded

    /**
     * Compares every byte regardless of where the first difference is, so the
     * time taken says nothing about how much of the code was right.
     */
    private fun ByteArray.constantTimeEquals(other: ByteArray): Boolean {
        if (size != other.size) return false
        var difference = 0
        for (index in indices) difference = difference or (this[index].toInt() xor other[index].toInt())
        return difference == 0
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}
