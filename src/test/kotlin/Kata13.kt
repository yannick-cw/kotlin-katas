import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class LocksSpec : StringSpec({

    "read-write lock should allow concurrent reads" {
        val cache = ReadWriteCache<String, Int>()
        cache.put("a", 1)

        val results = mutableListOf<Int?>()
        val latch = CountDownLatch(3)

        // Multiple concurrent reads should work
        repeat(3) {
            Thread {
                results.add(cache.get("a"))
                latch.countDown()
            }.start()
        }

        latch.await(1, TimeUnit.SECONDS)
        results.filterNotNull().size shouldBe 3
    }

    "read-write lock should be thread-safe for writes" {
        val cache = ReadWriteCache<String, Int>()

        val latch = CountDownLatch(10)
        repeat(10) { i ->
            Thread {
                cache.put("key-$i", i)
                latch.countDown()
            }.start()
        }

        latch.await(1, TimeUnit.SECONDS)

        // All writes should have succeeded
        (0..9).all { cache.get("key-$it") == it } shouldBe true
    }
})

// Read-write lock cache
// ReadWriteLock allows multiple readers OR one writer
class ReadWriteCache<K, V> {
    private val map = mutableMapOf<K, V>()
    private val lock = ReentrantReadWriteLock()

    fun get(key: K): V? =
        lock.readLock().withLock { map[key] }

    fun put(key: K, value: V) = lock.writeLock().withLock {
        map[key] = value
    }
}
