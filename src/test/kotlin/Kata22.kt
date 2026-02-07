import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly

class IndexSpec : StringSpec({

    "should find exact match" {
        val index = BTreeIndex<Int, String>()
        index.insert(5, "row-5")
        index.insert(3, "row-3")
        index.insert(8, "row-8")
        index.insert(1, "row-1")

        index.get(5) shouldBe "row-5"
        index.get(3) shouldBe "row-3"
        index.get(99) shouldBe null
    }

    "should support range queries" {
        val index = BTreeIndex<Int, String>()
        listOf(10, 5, 15, 3, 7, 12, 20).forEachIndexed { i, key ->
            index.insert(key, "row-$key")
        }

        // Range: 5 <= key <= 12
        val range = index.range(5, 12)

        range.map { it.first } shouldContainExactly listOf(5, 7, 10, 12)
    }

    "should support greater-than queries" {
        val index = BTreeIndex<Int, String>()
        listOf(1, 5, 10, 15, 20).forEach { index.insert(it, "row-$it") }

        val result = index.greaterThan(10)

        result.map { it.first } shouldContainExactly listOf(15, 20)
    }

    "should support less-than queries" {
        val index = BTreeIndex<Int, String>()
        listOf(1, 5, 10, 15, 20).forEach { index.insert(it, "row-$it") }

        val result = index.lessThan(10)

        result.map { it.first } shouldContainExactly listOf(1, 5)
    }

    "should return sorted for ORDER BY" {
        val index = BTreeIndex<Int, String>()
        listOf(50, 10, 30, 20, 40).forEach { index.insert(it, "row-$it") }

        val sorted = index.scan()

        sorted.map { it.first } shouldContainExactly listOf(10, 20, 30, 40, 50)
    }

    "hash index should only support equality" {
        val hashIndex = HashIndex<String, Int>()
        hashIndex.insert("alice", 1)
        hashIndex.insert("bob", 2)
        hashIndex.insert("charlie", 3)

        hashIndex.get("bob") shouldBe 2
        hashIndex.get("dave") shouldBe null
    }

    "should count index accesses (for understanding cost)" {
        val index = BTreeIndex<Int, String>()
        // Insert 1000 items
        (1..1000).forEach { index.insert(it, "row-$it") }

        index.resetAccessCount()
        index.get(500)

        // Binary search: ~10 comparisons for 1000 items (log2(1000) ≈ 10)
        index.accessCount shouldBe 10
    }
})

// Simplified B-tree index (leaf-level only: sorted array + binary search)
class BTreeIndex<K : Comparable<K>, V> {
    private val entries = mutableListOf<Pair<K, V>>()
    var accessCount = 0
        private set

    fun insert(key: K, value: V) {
        // TODO: Insert maintaining sorted order
        // Find insertion point with binary search, insert there

        val pos = findIndex(key)

        entries.add(pos, key to value)
    }

    fun get(key: K): V? {
        // TODO: Binary search to find exact key
        // Increment accessCount for each comparison


        return entries.getOrNull(findIndex(key))?.second
    }

    // Range query: return all entries where from <= key <= to
    fun range(from: K, to: K): List<Pair<K, V>> {
        // TODO:
        // 1. Binary search to find first key >= from
        // 2. Scan forward until key > to

        val startPos = findIndex(from)
        return entries.drop(startPos).takeWhile { it.first <= to }.toList()
    }

    fun greaterThan(key: K): List<Pair<K, V>> {
        // TODO: Find first entry > key, return rest

        val startPos = findIndex(key)
        return entries.drop(startPos + 1)
    }

    fun lessThan(key: K): List<Pair<K, V>> {
        // TODO: Return all entries where key < target

        val startPos = findIndex(key)
        return entries.take(startPos)
    }

    // Full index scan (for ORDER BY without WHERE)
    fun scan(): List<Pair<K, V>> = entries.toList()

    fun resetAccessCount() {
        accessCount = 0
    }

    // Helper: binary search returning insertion point
    private fun findIndex(key: K): Int {
        var low = 0
        var high = entries.size - 1

        while (low <= high) {
            accessCount++
            val mid = (low + high) / 2
            val cmp = entries[mid].first.compareTo(key)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return low  // Insertion point
    }
}

// Hash index: O(1) equality, no range support
class HashIndex<K, V> {
    private val map = mutableMapOf<K, V>()

    fun insert(key: K, value: V) {
        map[key] = value
    }

    fun get(key: K): V? = map[key]

    // Hash indexes CAN'T do these efficiently:
    // fun range(...) - would need full scan
    // fun greaterThan(...) - would need full scan
}
