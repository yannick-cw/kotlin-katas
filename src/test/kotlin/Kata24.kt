import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class IsolationSpec : StringSpec({

    "dirty read: reading uncommitted changes" {
        val db = IsolationDB(IsolationLevel.READ_UNCOMMITTED)
        db.set("x", 10)

        val tx1 = db.begin()
        tx1.set("x", 20)  // Not committed!

        val tx2 = db.begin()
        val dirtyValue = tx2.get("x")

        // READ UNCOMMITTED allows dirty reads
        val allowsDirtyRead = (dirtyValue == 20)
        allowsDirtyRead shouldBe true
    }

    "read committed prevents dirty reads" {
        val db = IsolationDB(IsolationLevel.READ_COMMITTED)
        db.set("x", 10)

        val tx1 = db.begin()
        tx1.set("x", 20)  // Not committed!

        val tx2 = db.begin()

        // READ COMMITTED only sees committed values
        tx2.get("x") shouldBe 10
    }

    "non-repeatable read: value changes between reads" {
        val db = IsolationDB(IsolationLevel.READ_COMMITTED)
        db.set("x", 10)

        val tx1 = db.begin()
        tx1.get("x") shouldBe 10

        // Another transaction commits
        val tx2 = db.begin()
        tx2.set("x", 20)
        tx2.commit()

        // Same read, different result (non-repeatable)
        val allowsNonRepeatable = db.isolationLevel == IsolationLevel.READ_COMMITTED
        allowsNonRepeatable shouldBe true
    }

    "repeatable read prevents non-repeatable reads" {
        val db = IsolationDB(IsolationLevel.REPEATABLE_READ)
        db.set("x", 10)

        val tx1 = db.begin()
        tx1.get("x") shouldBe 10

        // Another transaction commits
        val tx2 = db.begin()
        tx2.set("x", 20)
        tx2.commit()

        // tx1 still sees original value (snapshot)
        tx1.get("x") shouldBe 10
    }

    "phantom read: new rows appear" {
        val db = IsolationDB(IsolationLevel.READ_COMMITTED)
        db.insert(Row(1, "active"))
        db.insert(Row(2, "active"))

        val tx1 = db.begin()
        tx1.count { it.status == "active" } shouldBe 2

        // Another transaction inserts
        val tx2 = db.begin()
        tx2.insert(Row(3, "active"))
        tx2.commit()

        // Phantom: new row appears in same query
        // READ COMMITTED allows phantoms
    }

    "serializable prevents all anomalies" {
        val level = IsolationLevel.SERIALIZABLE

        level.preventsDirtyRead() shouldBe true
        level.preventsNonRepeatableRead() shouldBe true
        level.preventsPhantomRead() shouldBe true
    }
})

enum class IsolationLevel {
    READ_UNCOMMITTED,
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE;

    fun preventsDirtyRead(): Boolean {
        TODO()
    }

    fun preventsNonRepeatableRead(): Boolean {
        TODO()
    }

    fun preventsPhantomRead(): Boolean {
        TODO()
    }
}

data class Row(val id: Int, val status: String)

// TODO: Implement isolation-aware DB simulation
class IsolationDB(val isolationLevel: IsolationLevel) {
    private val data = mutableMapOf<String, Any?>()
    private val rows = mutableListOf<Row>()
    private val uncommitted = mutableMapOf<Long, MutableMap<String, Any?>>()
    private var nextTxId = 1L

    fun set(key: String, value: Any?) { data[key] = value }
    fun insert(row: Row) { rows.add(row) }

    fun begin(): Transaction {
        val txId = nextTxId++
        val snapshot = when (isolationLevel) {
            IsolationLevel.REPEATABLE_READ, IsolationLevel.SERIALIZABLE -> data.toMap()
            else -> emptyMap()
        }
        return Transaction(txId, snapshot)
    }

    inner class Transaction(
        private val txId: Long,
        private val snapshot: Map<String, Any?>
    ) {
        private val localWrites = mutableMapOf<String, Any?>()

        init {
            uncommitted[txId] = localWrites
        }

        fun get(key: String): Any? {
            // Implement based on isolation level
            TODO()
        }

        fun set(key: String, value: Any?) {
            localWrites[key] = value
        }

        fun insert(row: Row) {
            rows.add(row)  // Simplified
        }

        fun count(predicate: (Row) -> Boolean): Int = rows.count(predicate)

        fun commit() {
            data.putAll(localWrites)
            uncommitted.remove(txId)
        }
    }
}
