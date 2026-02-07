import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TransactionSpec : StringSpec({

    "atomicity: successful transaction commits all changes" {
        val db = SimpleDB()
        db.set("a", 1)
        db.set("b", 2)

        db.transaction {
            set("a", 10)
            set("b", 20)
        }

        db.get("a") shouldBe 10
        db.get("b") shouldBe 20
    }

    "atomicity: failed transaction should rollback all changes" {
        val db = SimpleDB()
        db.set("balance", 100)

        runCatching {
            db.transaction {
                set("balance", 50)     // Would commit this
                set("audit", "debit")  // And this
                throw RuntimeException("Network error!")  // But this fails
            }
        }

        db.get("balance") shouldBe 100  // Rolled back
        db.get("audit") shouldBe null   // Never committed
    }

    "consistency: constraint violation should rollback" {
        val bank = BankDB()
        bank.createAccount("alice", 100)
        bank.createAccount("bob", 50)

        val result = runCatching {
            bank.transfer("alice", "bob", 200)  // Violates: balance >= 0
        }

        result.isFailure shouldBe true
        bank.getBalance("alice") shouldBe 100  // Unchanged
        bank.getBalance("bob") shouldBe 50     // Unchanged
    }

    "isolation: concurrent reads see consistent state" {
        val db = SimpleDB()
        db.set("counter", 0)

        val snapshot = db.snapshot()

        db.set("counter", 100)  // Change after snapshot

        snapshot.get("counter") shouldBe 0  // Snapshot isolation
        db.get("counter") shouldBe 100      // Current value
    }
})

// TODO: Implement SimpleDB with transaction support
class SimpleDB {
    private val data = mutableMapOf<String, Any?>()

    fun set(key: String, value: Any?) {
        data[key] = value
    }

    fun get(key: String): Any? = data[key]

    fun transaction(block: TransactionContext.() -> Unit) {
        // 1. Create transaction context with copy of data
        // 2. Execute block
        // 3. On success: merge changes
        // 4. On failure: discard changes
        val newTransaction = TransactionContext()

        // do not even need to catch here, as failure prevents commit anyway
        block(newTransaction)
        newTransaction.commit()
    }

    fun snapshot(): Snapshot {
        return Snapshot()
    }

    inner class TransactionContext {
        private val localChanges = mutableMapOf<String, Any?>()

        fun set(key: String, value: Any?) {
            localChanges[key] = value
        }

        fun get(key: String): Any? = localChanges[key] ?: data[key]

        internal fun commit() {
            data.putAll(localChanges)
        }
    }

    inner class Snapshot {
        private val frozenData = data.toMap()

        fun get(key: String): Any? = frozenData[key]
    }
}

// TODO: Implement BankDB with constraints
class BankDB {
    private val accounts = SimpleDB()

    fun createAccount(name: String, balance: Int) {
        require(balance >= 0) { "Balance must be non-negative" }
        accounts.set(name, balance)
    }

    fun getBalance(name: String): Int = accounts.get(name)?.safeCast() ?: 0

    fun transfer(from: String, to: String, amount: Int) {
        // Atomically transfer with constraint: balance >= 0
        // Should rollback if constraint violated

        accounts.transaction {
            fun getBalance(name: String): Int? = get(name)?.safeCast()

            val balanceFrom = getBalance(from) ?: throw IllegalStateException("acc does not exist")
            val balanceTo = getBalance(to) ?: throw IllegalStateException("acc does not exist")
            set(from, balanceFrom - amount)
            set(to, balanceTo + amount)

            val newFromBalance = getBalance(from)!!

            if (newFromBalance < 0) {
                throw IllegalStateException("Out of funds")
            }
        }
    }
}
