import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

class BloomFilterSpec : StringSpec({

    "should return true for added elements" {
        val bloom = BloomFilter<String>(expectedElements = 100, falsePositiveRate = 0.01)
        bloom.add("hello")
        bloom.add("world")

        bloom.mightContain("hello") shouldBe true
        bloom.mightContain("world") shouldBe true
    }

    "should return false for definitely absent elements" {
        val bloom = BloomFilter<String>(expectedElements = 100, falsePositiveRate = 0.01)
        bloom.add("hello")

        bloom.mightContain("goodbye") shouldBe false
        bloom.mightContain("test") shouldBe false
    }

    "should never have false negatives" {
        val bloom = BloomFilter<Int>(expectedElements = 1000, falsePositiveRate = 0.01)
        val added = (1..500).toList()

        added.forEach { bloom.add(it) }

        // ALL added elements must return true (no false negatives)
        added.all { bloom.mightContain(it) } shouldBe true
    }

    "false positive rate should be approximately as configured" {
        val bloom = BloomFilter<Int>(expectedElements = 1000, falsePositiveRate = 0.05)

        // Add 1000 elements
        (0 until 1000).forEach { bloom.add(it) }

        // Test 10000 elements that were NOT added
        val falsePositives = (10000 until 20000).count { bloom.mightContain(it) }
        val actualRate = falsePositives.toDouble() / 10000

        // Should be approximately 5% (allow 2-10% range)
        actualRate shouldBeGreaterThan 0.02
        actualRate shouldBeLessThan 0.10
    }

    "should calculate optimal parameters" {
        // For 1000 elements with 1% FP rate
        val params = BloomFilter.optimalParameters(elements = 1000, falsePositiveRate = 0.01)

        // Optimal bits ≈ -n*ln(p) / (ln(2)^2) ≈ 9585
        (params.bits > 9000) shouldBe true
        (params.bits < 10000) shouldBe true

        // Optimal k = (m/n) * ln(2) ≈ 7
        (params.hashFunctions >= 6) shouldBe true
        (params.hashFunctions <= 8) shouldBe true
    }

    "should report approximate element count" {
        val bloom = BloomFilter<Int>(expectedElements = 1000, falsePositiveRate = 0.01)

        repeat(500) { bloom.add(it) }

        val estimated = bloom.approximateElementCount()

        // Should be roughly 500 (allow 400-600 range)
        (estimated > 400) shouldBe true
        (estimated < 600) shouldBe true
    }

    "should merge two bloom filters" {
        val bloom1 = BloomFilter<String>(expectedElements = 100, falsePositiveRate = 0.01)
        val bloom2 = BloomFilter<String>(expectedElements = 100, falsePositiveRate = 0.01)

        bloom1.add("hello")
        bloom2.add("world")

        val merged = bloom1.merge(bloom2)

        merged.mightContain("hello") shouldBe true
        merged.mightContain("world") shouldBe true
    }
})

data class BloomParameters(val bits: Int, val hashFunctions: Int)

class BloomFilter<T>(
    expectedElements: Int,
    falsePositiveRate: Double
) {
    private val params = optimalParameters(expectedElements, falsePositiveRate)
    private val bits = BooleanArray(params.bits)
    private val hashCount = params.hashFunctions

    fun add(element: T) {
        // TODO: Set bit at each hash position to true
        TODO()
    }

    fun mightContain(element: T): Boolean {
        // TODO: Return true only if ALL hash positions are set
        TODO()
    }

    // Estimate how many elements have been added
    // Formula: n* = -(m/k) * ln(1 - X/m)
    // where X = number of bits set
    fun approximateElementCount(): Int {
        // TODO: Count set bits, apply formula
        TODO()
    }

    // Merge two bloom filters (OR their bit arrays)
    fun merge(other: BloomFilter<T>): BloomFilter<T> {
        require(this.params == other.params) { "Filters must have same parameters" }

        // TODO: Create new filter, OR the bit arrays
        TODO()
    }

    // Double hashing: h(i) = (h1 + i*h2) mod m
    private fun getHashPositions(element: T): List<Int> {
        val h1 = element.hashCode()
        val h2 = element.toString().reversed().hashCode()

        return (0 until hashCount).map { i ->
            abs((h1 + i * h2) % bits.size)
        }
    }

    companion object {
        // Calculate optimal m (bits) and k (hash functions)
        // m = -n*ln(p) / (ln(2)^2)
        // k = (m/n) * ln(2)
        fun optimalParameters(elements: Int, falsePositiveRate: Double): BloomParameters {
            val bits =
                val  hashFunctions =
            // TODO: Calculate optimal bit array size and hash count
                    
                    return return BloomParameters(bits, hashFunctions = )
        }
    }
}
