import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.math.max

class SlidingWindowSpec : StringSpec({

    // ===== FIXED-SIZE WINDOW =====

    "should find max sum of k consecutive elements" {
        maxSumWindow(listOf(1, 3, 2, 6, 4), k = 3) shouldBe 12  // [2,6,4]
        maxSumWindow(listOf(1, 2, 3, 4, 5), k = 2) shouldBe 9   // [4,5]
        maxSumWindow(listOf(5), k = 1) shouldBe 5
    }

    "should find max element in each window" {
        // Harder variant: track max efficiently (can use deque for O(n) total)
        maxInEachWindow(listOf(1, 3, -1, -3, 5, 3, 6, 7), k = 3) shouldBe
                listOf(3, 3, 5, 5, 6, 7)
    }

    // ===== VARIABLE-SIZE WINDOW =====

    "should find longest substring without repeating characters" {
        longestUniqueSubstring("abcabcbb") shouldBe 3   // "abc"
        longestUniqueSubstring("bbbbb") shouldBe 1      // "b"
        longestUniqueSubstring("pwwkew") shouldBe 3     // "wke"
        longestUniqueSubstring("") shouldBe 0
        longestUniqueSubstring("abcdef") shouldBe 6     // whole string
        longestUniqueSubstring("dvdf") shouldBe 3       // "vdf" - can't just reset to single char
    }

    "should find smallest subarray with sum >= target" {
        minSubarrayLen(7, listOf(2, 3, 1, 2, 4, 3)) shouldBe 2   // [4,3]
        minSubarrayLen(4, listOf(1, 4, 4)) shouldBe 1            // [4]
        minSubarrayLen(11, listOf(1, 1, 1, 1, 1)) shouldBe 0     // not possible
    }

    "should find all anagrams in string" {
        findAnagrams("cbaebabacd", "abc") shouldBe listOf(0, 6)  // "cba", "bac"
        findAnagrams("abab", "ab") shouldBe listOf(0, 1, 2)
    }
})

// ===== FIXED-SIZE WINDOW =====

fun maxSumWindow(nums: List<Int>, k: Int): Int {
    if (nums.size < k) return 0

    // Initialize first window
    val initWindow = nums.take(k).sum()

    val (_, maxWindow) = (0 until nums.size - k).fold(initWindow to initWindow) { (currentWindowSum, currentMax), dropPosition ->
        val newWindowSum = currentWindowSum - nums[dropPosition] + nums[dropPosition + k]

        if (newWindowSum > currentMax) {
            newWindowSum to newWindowSum
        } else {
            newWindowSum to currentMax
        }
    }

    return maxWindow
    // For i in k until nums.size:
    //   windowSum += nums[i] - nums[i - k]  // add entering, remove leaving
    //   maxSum = maxOf(maxSum, windowSum)
}

fun maxInEachWindow(nums: List<Int>, k: Int): List<Int> {
    if (nums.size < k) return emptyList()


    // Simple approach: O(n*k) - recalculate max each window
    // Optimal approach: O(n) using Deque to track max candidates
    // For this kata, simple approach is fine

    // TODO: For each window position, find the max
    return nums.windowed(k).map { it.max() }
}

// ===== VARIABLE-SIZE WINDOW =====

fun longestUniqueSubstring(s: String): Int {
    // TODO: Use variable-size window with a Set to track chars in window
    //
    // var left = 0
    // var maxLen = 0
    // val seen = mutableSetOf<Char>()
    //
    // for (right in s.indices) {
    //     while (s[right] in seen) {
    //         seen.remove(s[left])
    //         left++
    //     }
    //     seen.add(s[right])
    //     maxLen = maxOf(maxLen, right - left + 1)
    // }

    val (_, max) = s.fold("" to 0) { (nonRepeatingSub, longestYet), nextChar ->
        if (nonRepeatingSub.contains(nextChar)) {
            val newSub = nonRepeatingSub.dropWhile { it != nextChar }.drop(1) + nextChar
            newSub to longestYet
        } else {
            nonRepeatingSub + nextChar to max(longestYet, nonRepeatingSub.length + 1)
        }
    }
    return max
}

fun minSubarrayLen(target: Int, nums: List<Int>): Int {
    // TODO: Variable window - expand until sum >= target, then shrink
    //
    // var left = 0
    // var sum = 0
    // var minLen = Int.MAX_VALUE
    //
    // for (right in nums.indices) {
    //     sum += nums[right]
    //     while (sum >= target) {
    //         minLen = minOf(minLen, right - left + 1)
    //         sum -= nums[left]
    //         left++
    //     }
    // }
    // return if (minLen == Int.MAX_VALUE) 0 else minLen
    TODO()
}

fun findAnagrams(s: String, p: String): List<Int> {
    if (s.length < p.length) return emptyList()

    // TODO: Fixed window of size p.length, but track character counts
    //
    // 1. Count chars in p
    // 2. Slide window over s, maintaining char counts
    // 3. When window counts match p counts, record index
    //
    // Hint: Use two maps (or arrays for lowercase only)
    // and a "matches" counter that tracks how many chars have correct count
    TODO()
}
