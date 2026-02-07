import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.doubles.shouldBeLessThan
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class ConsistentHashingSpec : StringSpec({

    "should distribute keys to nodes" {
        val ring = ConsistentHashRing<String>(virtualNodes = 1)
        ring.addNode("NodeA")
        ring.addNode("NodeB")
        ring.addNode("NodeC")

        val node = ring.getNode("user-123")
        node shouldNotBe null
    }

    "same key should always map to same node" {
        val ring = ConsistentHashRing<String>(virtualNodes = 1)
        ring.addNode("NodeA")
        ring.addNode("NodeB")

        val node1 = ring.getNode("my-key")
        val node2 = ring.getNode("my-key")

        node1 shouldBe node2
    }

    "removing node should only affect nearby keys" {
        val ring = ConsistentHashRing<String>(virtualNodes = 10)
        ring.addNode("NodeA")
        ring.addNode("NodeB")
        ring.addNode("NodeC")

        val keysBefore = (0..999).associate { "key-$it" to ring.getNode("key-$it") }
        ring.removeNode("NodeB")
        val keysAfter = (0..999).associate { "key-$it" to ring.getNode("key-$it") }

        // With 3 nodes, removing 1 should move ~1/3 of keys
        val moved = keysBefore.count { (k, v) -> keysAfter[k] != v }
        val movedPercent = moved.toDouble() / 1000

        // Should move roughly 33% (allow 20-50% range)
        (movedPercent > 0.20) shouldBe true
        (movedPercent < 0.50) shouldBe true
    }

    "virtual nodes should improve distribution balance" {
        val ringNoVirtual = ConsistentHashRing<String>(virtualNodes = 1)
        val ringWithVirtual = ConsistentHashRing<String>(virtualNodes = 100)

        listOf("A", "B", "C").forEach {
            ringNoVirtual.addNode(it)
            ringWithVirtual.addNode(it)
        }

        // Measure distribution of 10000 keys
        val distNoVirtual = measureDistribution(ringNoVirtual, 10000)
        val distWithVirtual = measureDistribution(ringWithVirtual, 10000)

        // Standard deviation should be lower with virtual nodes
        val stdDevNoVirtual = standardDeviation(distNoVirtual.values)
        val stdDevWithVirtual = standardDeviation(distWithVirtual.values)

        stdDevWithVirtual shouldBeLessThan stdDevNoVirtual
    }

    "should handle ring wrap-around" {
        val ring = ConsistentHashRing<String>(virtualNodes = 1)
        ring.addNode("OnlyNode")

        // All keys should go to OnlyNode regardless of hash position
        (0..100).forEach { i ->
            ring.getNode("key-$i") shouldBe "OnlyNode"
        }
    }

    "getNodes should return N replicas for replication" {
        val ring = ConsistentHashRing<String>(virtualNodes = 10)
        ring.addNode("A")
        ring.addNode("B")
        ring.addNode("C")
        ring.addNode("D")

        val replicas = ring.getNodes("my-key", count = 3)

        replicas.size shouldBe 3
        replicas.distinct().size shouldBe 3  // All different nodes
    }
})

// Helper functions for tests
fun measureDistribution(ring: ConsistentHashRing<String>, keyCount: Int): Map<String, Int> {
    return (0 until keyCount)
        .mapNotNull { ring.getNode("key-$it") }
        .groupingBy { it }
        .eachCount()
}

fun standardDeviation(values: Collection<Int>): Double {
    val mean = values.average()
    val variance = values.map { (it - mean) * (it - mean) }.average()
    return sqrt(variance)
}

class ConsistentHashRing<N>(private val virtualNodes: Int = 100) {
    private val ring: TreeMap<Int, N> = TreeMap()
    private val nodeToPositions = mutableMapOf<N, MutableList<Int>>()

    private fun hash(key: String): Int {
        // Better hash function using multiple mixing rounds
        // (MurmurHash-inspired for better distribution)
        var h = key.hashCode().toLong()
        h = h xor (h ushr 16)
        h = h * 0x85ebca6b  // Prime multiplier for mixing
        h = h xor (h ushr 13)
        h = h * 0xc2b2ae35  // Another prime multiplier
        h = h xor (h ushr 16)
        return h.toInt() and Int.MAX_VALUE
    }

    fun addNode(node: N) {
        // TODO: Add node at multiple positions (virtual nodes)
        // For i in 0 until virtualNodes:
        //   position = hash("$node-$i")
        //   ring[position] = node
        //   track position in nodeToPositions

        (0 until virtualNodes).forEach { index ->
            val position = hash("$node-$index")
            ring[position] = node
            nodeToPositions.compute(node) { _, listAtKey ->
                listAtKey?.also { it.addFirst(position) } ?: mutableListOf(position)
            }
        }
    }

    fun removeNode(node: N) {
        // TODO: Remove all virtual node positions for this node
        // Use nodeToPositions to find all positions, remove from ring
        nodeToPositions[node]?.forEach { position -> ring.remove(position) }
        nodeToPositions.remove(node)
    }

    // TODO: Find first node at or after key's position (clockwise)
    // Use ring.ceilingEntry(hash) or wrap to first if null
    fun getNode(key: String): N? {
        if (ring.isEmpty()) return null
        else {
            val position = hash(key)
            return ring.ceilingEntry(position)?.value ?: ring.firstEntry()?.value
        }
    }

    // For replication: get N distinct physical nodes clockwise from key
    fun getNodes(key: String, count: Int): List<N> {
        if (ring.isEmpty() || count <= 0) return emptyList()
        val position = hash(key)
        val result = mutableListOf<N>()
        val seen = mutableSetOf<N>()

        var entries = ring.tailMap(position, true).values + ring.headMap(position, false).values
        for (node in entries) {
            if (seen.add(node)) {
                result.add(node)
                if (result.size == count) break
            }
        }
        return result


        // TODO:
        // 1. Start at key's position
        // 2. Walk clockwise collecting distinct physical nodes
        // 3. Stop when we have 'count' nodes or wrapped around
    }
}
