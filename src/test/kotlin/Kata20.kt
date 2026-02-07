import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MvccSpec : StringSpec({

    "transaction should see its own writes" {
        val store = MvccStore<String, String>()

        val tx = store.begin()
        tx.write("key", "value")
        tx.read("key") shouldBe "value"
        tx.commit()
    }

    "committed writes should be visible to new transactions" {
        val store = MvccStore<String, String>()

        val tx1 = store.begin()
        tx1.write("key", "first")
        tx1.commit()

        val tx2 = store.begin()
        tx2.read("key") shouldBe "first"
    }

    "uncommitted writes should not be visible to others" {
        val store = MvccStore<String, String>()

        val tx1 = store.begin()
        tx1.write("key", "uncommitted")
        // tx1 not committed yet

        val tx2 = store.begin()
        tx2.read("key") shouldBe null  // Can't see uncommitted

        tx1.commit()
    }

    "snapshot isolation: transaction sees consistent snapshot" {
        val store = MvccStore<String, String>()

        // Initial state
        val tx0 = store.begin()
        tx0.write("key", "original")
        tx0.commit()

        // tx1 starts, takes snapshot
        val tx1 = store.begin()

        // tx2 modifies and commits
        val tx2 = store.begin()
        tx2.write("key", "modified")
        tx2.commit()

        // tx1 should still see original (snapshot isolation)
        tx1.read("key") shouldBe "original"
    }

    "delete should hide row from new transactions" {
        val store = MvccStore<String, String>()

        val tx1 = store.begin()
        tx1.write("key", "value")
        tx1.commit()

        val tx2 = store.begin()
        tx2.delete("key")
        tx2.commit()

        val tx3 = store.begin()
        tx3.read("key") shouldBe null
    }
})

// TODO: Implement simplified MVCC store

data class Version<V>(
    val value: V?,
    val xmin: Long,      // Transaction that created this version
    val xmax: Long? = null,  // Transaction that deleted (null = alive)
    val deleted: Boolean = false
)

class MvccStore<K, V> {
    private var nextTxId = 1L
    private val committedTxIds = mutableSetOf<Long>()
    private val versions = mutableMapOf<K, MutableList<Version<V>>>()

    fun begin(): Transaction<K, V> {
        val txId = nextTxId++
        val snapshot = committedTxIds.toSet()
        return Transaction(txId, snapshot, this)
    }

    internal fun commit(txId: Long) {
        committedTxIds.add(txId)
    }

    internal fun addVersion(key: K, version: Version<V>) {
        versions.getOrPut(key) { mutableListOf() }.add(version)
    }

    internal fun getVersions(key: K): List<Version<V>> {
        return versions[key] ?: emptyList()
    }
}

class Transaction<K, V>(
    private val txId: Long,
    private val snapshot: Set<Long>,  // Committed txs at start
    private val store: MvccStore<K, V>
) {
    private val localWrites = mutableMapOf<K, V?>()

    fun read(key: K): V? {
        // 1. Check local writes first
        // 2. Find visible version: xmin committed in snapshot, xmax null or not in snapshot
        return localWrites[key] ?: store.getVersions(key).firstOrNull { version ->
            version.xmin in snapshot && (version.xmax == null || version.xmax !in snapshot)
        }?.value

    }

    fun write(key: K, value: V) {
        localWrites[key] = value
    }

    fun delete(key: K) {
        localWrites[key] = null
    }

    fun commit() {
        // Persist local writes as versions, mark self as committed
        localWrites.forEach { (k, v) ->
            if (v == null) {
                // Delete: create version with value=null and xmax set
                store.addVersion(k, Version(null, txId, txId))  // Created and deleted by same tx
            } else {
                // Write: create new version
                store.addVersion(k, Version(v, txId, null))
            }
        }
        store.commit(txId)
    }

    private fun isVisible(version: Version<V>): Boolean {
        // Visible if:
        // - Created by committed tx in our snapshot OR by us
        // - Not deleted, OR deleted by tx not in our snapshot and not us
        TODO()
    }
}
