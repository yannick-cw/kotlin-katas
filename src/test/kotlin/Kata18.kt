import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class QuorumSpec : StringSpec({

    "write should succeed when quorum reached" {
        val cluster = QuorumCluster(replicas = 3, writeQuorum = 2, readQuorum = 2)

        val success = cluster.write("user:1", "Alice")

        success shouldBe true
    }

    "write should fail when quorum not reached" {
        val cluster = QuorumCluster(replicas = 3, writeQuorum = 2, readQuorum = 2)
        cluster.failNode(0)
        cluster.failNode(1)  // Only 1 healthy node, need 2

        val success = cluster.write("user:1", "Alice")

        success shouldBe false
    }

    "read should return latest version when quorum reached" {
        val cluster = QuorumCluster(replicas = 3, writeQuorum = 2, readQuorum = 2)

        cluster.write("user:1", "Alice")
        cluster.write("user:1", "Alice Updated")  // Version 2

        val result = cluster.read("user:1")

        result shouldBe "Alice Updated"
    }

    "read should pick highest version from responses" {
        val cluster = QuorumCluster(replicas = 3, writeQuorum = 2, readQuorum = 2)

        // Simulate partial replication: node 0 has v2, nodes 1,2 have v1
        cluster.directWrite(0, "key", "v2-value", version = 2)
        cluster.directWrite(1, "key", "v1-value", version = 1)
        cluster.directWrite(2, "key", "v1-value", version = 1)

        // Read from quorum should find v2
        val result = cluster.read("key")

        result shouldBe "v2-value"
    }

    "read should return null for missing key" {
        val cluster = QuorumCluster(replicas = 3, writeQuorum = 2, readQuorum = 2)

        val result = cluster.read("nonexistent")

        result shouldBe null
    }

    "W+R>N should guarantee seeing latest write" {
        val cluster = QuorumCluster(replicas = 5, writeQuorum = 3, readQuorum = 3)
        // W + R = 6 > 5 = N, so guaranteed overlap

        cluster.write("key", "first")
        cluster.write("key", "second")

        // Even if we only reach 3 nodes, at least 1 has "second"
        val result = cluster.read("key")
        result shouldBe "second"
    }

    "read repair should update stale replicas" {
        val cluster = QuorumCluster(replicas = 3, writeQuorum = 2, readQuorum = 2)

        // Node 0 has v2, nodes 1,2 have v1 (stale)
        cluster.directWrite(0, "key", "latest", version = 2)
        cluster.directWrite(1, "key", "stale", version = 1)
        cluster.directWrite(2, "key", "stale", version = 1)

        // Read triggers read repair
        cluster.read("key")

        // After read repair, all nodes should have v2
        cluster.getNodeVersion(1, "key") shouldBe 2
        cluster.getNodeVersion(2, "key") shouldBe 2
    }
})

data class VersionedValue(val value: String, val version: Long)

class ReplicaNode {
    private val data = mutableMapOf<String, VersionedValue>()
    var healthy = true

    fun write(key: String, value: String, version: Long): Boolean {
        if (!healthy) return false
        val existing = data[key]
        // Only write if version is higher (or first write)
        if (existing == null || version > existing.version) {
            data[key] = VersionedValue(value, version)
        }
        return true
    }

    fun read(key: String): VersionedValue? {
        if (!healthy) return null
        return data[key]
    }

    fun getVersion(key: String): Long = data[key]?.version ?: 0
}

class QuorumCluster(
    private val replicas: Int, private val writeQuorum: Int, private val readQuorum: Int
) {
    private val nodes = List(replicas) { ReplicaNode() }
    private var globalVersion = 0L

    fun write(key: String, value: String): Boolean {
        val version = ++globalVersion
        val allWrites = nodes.map { it.write(key, value, version) }

        return allWrites.filter { it }.size >= writeQuorum
    }

    fun read(key: String): String? {
        // 1. Collect responses from healthy nodes
        // 2. Check if we have readQuorum responses
        // 3. Find the response with highest version
        // 4. (Bonus) Trigger read repair for stale nodes
        // 5. Return the value (or null if not found)
        val results = nodes.mapNotNull { it.read(key)?.let { v -> it to v } }
        return if (results.size >= readQuorum) {
            val mostUpToDateNodeRes =
                results.reduce { acc, value -> if (acc.second.version > value.second.version) acc else value }
            results.filter { it.second.version < mostUpToDateNodeRes.second.version }.forEach { (node, _) ->
                node.write(
                    key,
                    mostUpToDateNodeRes.second.value,
                    mostUpToDateNodeRes.second.version
                )
            }
            mostUpToDateNodeRes.second.value
        } else null
    }

    // Helper: perform read repair - update stale nodes
    private fun readRepair(key: String, latest: VersionedValue, staleNodes: List<Int>) {
        staleNodes.forEach { nodeIndex ->
            nodes[nodeIndex].write(key, latest.value, latest.version)
        }
    }

    // Test helpers
    fun failNode(index: Int) {
        nodes[index].healthy = false
    }

    fun recoverNode(index: Int) {
        nodes[index].healthy = true
    }

    fun directWrite(nodeIndex: Int, key: String, value: String, version: Long) {
        nodes[nodeIndex].write(key, value, version)
    }

    fun getNodeVersion(nodeIndex: Int, key: String): Long = nodes[nodeIndex].getVersion(key)
}
