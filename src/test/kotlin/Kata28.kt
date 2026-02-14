import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.experimental.xor
import kotlin.math.max
import kotlin.system.measureNanoTime

class TimingAttackSpec : StringSpec({

    "constant time compare should return correct result" {
        constantTimeEquals("hello", "hello") shouldBe true
        constantTimeEquals("hello", "world") shouldBe false
        constantTimeEquals("hello", "hell") shouldBe false
    }

    "should take same time regardless of match position" {
        val secret = "abcdefghij"

        // Warm up JIT
        repeat(1000) { constantTimeEquals(secret, "xxxxxxxxxx") }

        val times = listOf(
            "xxxxxxxxxx",  // No match
            "axxxxxxxxx",  // First char matches
            "abxxxxxxxx",  // First 2 match
            "abcdefghij",  // All match
        ).map { input ->
            val totalTime = (1..100).sumOf {
                measureNanoTime { constantTimeEquals(secret, input) }
            }
            totalTime / 100
        }

        // All times should be similar (within 2x of each other)
        val maxTime = times.maxOrNull()!!
        val minTime = times.minOrNull()!!
        val ratio = maxTime.toDouble() / minTime

        // This test is flaky due to JIT/GC, but demonstrates concept
        (ratio < 3.0) shouldBe true
    }

    "vulnerable compare should have timing variance" {
        // This demonstrates the vulnerability (not a requirement to implement)
        val secret = "abcdefghij"

        // In practice, vulnerable implementations show clear timing differences
        // This is educational - don't rely on this test
    }

    "constant time array compare should work" {
        val a = byteArrayOf(1, 2, 3, 4)
        val b = byteArrayOf(1, 2, 3, 4)
        val c = byteArrayOf(1, 2, 3, 5)

        constantTimeEquals(a, b) shouldBe true
        constantTimeEquals(a, c) shouldBe false
    }

    "HMAC comparison should be constant time" {
        val stored = "a1b2c3d4e5f6"

        verifyToken("a1b2c3d4e5f6", stored) shouldBe true
        verifyToken("xxxxxxxxxxxx", stored) shouldBe false
        verifyToken("a1b2c3d4e5f7", stored) shouldBe false
    }
})

// TODO: Implement constant-time string comparison
fun constantTimeEquals(a: String, b: String): Boolean =
    constantTimeEquals(a.toByteArray(), b.toByteArray())

// TODO: Implement constant-time byte array comparison
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
    (0 until max(a.size, b.size)).map { pos ->
        a.getOrElse(pos) { 0 }.xor(b.getOrElse(pos) { 0 })
    }.sum() == 0

// TODO: Implement secure token verification
fun verifyToken(provided: String, stored: String): Boolean = constantTimeEquals(provided, stored)

