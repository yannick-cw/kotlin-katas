import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.uuid.Uuid

class SecureRandomSpec : StringSpec({

    "should generate unique tokens" {
        val tokens = (1..100).map { generateToken(32) }.toSet()

        tokens.size shouldBe 100  // All unique
        tokens.toSet().size shouldBe tokens.size
    }

    "should generate token of correct length" {
        val token = generateToken(16)

        // 16 bytes = 128 bits = 22 base64 chars (without padding)
        (token.length >= 21) shouldBe true
    }

    "should generate URL-safe tokens" {
        val token = generateUrlSafeToken(32)

        // Should only contain URL-safe characters
        token.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '=' } shouldBe true
    }

    "should generate API key with prefix" {
        val key = generateApiKey(prefix = "sk_live_")

        key.startsWith("sk_live_") shouldBe true
        key.length shouldBe "sk_live_".length + 32  // prefix + 32 chars
    }

    "should generate numeric OTP" {
        val otp = generateOtp(length = 6)

        otp.length shouldBe 6
        otp.all { it.isDigit() } shouldBe true
    }

    "random bytes should have good distribution" {
        val bytes = generateSecureBytes(1000)

        // Simple distribution check: each byte value should appear
        val uniqueValues = bytes.toSet().size
        (uniqueValues > 200) shouldBe true  // Should see most of 256 values
    }

    "UUID should be v4 format" {
        val uuid = generateSecureUuid()

        // v4 UUID format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
        // where y is 8, 9, a, or b
        uuid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) shouldBe true
    }
})

// TODO: Implement secure random generation

fun generateSecureBytes(length: Int): ByteArray =
    getSecBytes(length)

fun generateToken(byteLength: Int): String {
    val byteArray = getSecBytes(byteLength)
    // Generate random bytes, encode as base64
    return Base64.getEncoder().encodeToString(byteArray)
}

private fun getSecBytes(byteLength: Int): ByteArray {
    val sec = SecureRandom()
    val byteArray = ByteArray(byteLength)
    sec.nextBytes(byteArray)
    return byteArray
}

fun generateUrlSafeToken(byteLength: Int): String {
    // Base64 URL-safe encoding (no +, /, =)
    val byteArray = getSecBytes(byteLength)
    return Base64.getUrlEncoder().encodeToString(byteArray)
}

fun generateApiKey(prefix: String, byteLength: Int = 24): String {
    // Prefix + URL-safe random
    val generateUrlSafeToken = generateUrlSafeToken(byteLength)
    return prefix + generateUrlSafeToken
}

fun generateOtp(length: Int): String {
    // Numeric only, uniform distribution
    // Be careful: random.nextInt(10) * length isn't uniform!
    return getSecBytes(length).map { it.toInt().absoluteValue % 10 }.joinToString("")
}

fun generateSecureUuid(): String {
    // UUID v4: random with version/variant bits set
    return UUID.randomUUID().toString()
}
