import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class CasSpec : StringSpec({

    "atomicCounter should handle concurrent increments" {
        val counter = AtomicCounter()
        val threads = 10
        val incrementsPerThread = 1000
        val latch = CountDownLatch(threads)

        repeat(threads) {
            Thread {
                repeat(incrementsPerThread) {
                    counter.increment()
                }
                latch.countDown()
            }.start()
        }

        latch.await()
        counter.get() shouldBe threads * incrementsPerThread
    }

    "cas should implement getAndUpdate pattern" {
        val value = AtomicInteger(10)

        val old = getAndUpdate(value) { it * 2 }

        old shouldBe 10
        value.get() shouldBe 20
    }
})

// Atomic counter using CAS
class AtomicCounter {
    private val value = AtomicInteger(0)

    fun increment() {
        // Pattern: read current, try to set current+1, retry if failed
        val current = value.get()
        if (!value.compareAndSet(current, current + 1)) {
            increment()
        }
    }

    fun get(): Int = value.get()
}

// TODO: Implement getAndUpdate using CAS loop
fun getAndUpdate(atomic: AtomicInteger, transform: (Int) -> Int): Int {
    val oldValue = atomic.get()
    val newValue = transform(oldValue)

    return if (!atomic.compareAndSet(oldValue, newValue)) {
        getAndUpdate(atomic, transform)
    } else {
        oldValue
    }
    // Return OLD value, but update to new
    // Pattern: do { old = get(); new = transform(old) } while (!cas(old, new))
}