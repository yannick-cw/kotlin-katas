import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class IdempotencySpec : StringSpec({

    "first request should execute operation" {
        val service = IdempotentService<String, Int>()
        var executionCount = 0

        val result = service.execute("key-1") {
            executionCount++
            42
        }

        result.getOrThrow() shouldBe 42
        executionCount shouldBe 1
    }

    "duplicate request should return cached result" {
        val service = IdempotentService<String, Int>()
        var executionCount = 0

        service.execute("key-1") { executionCount++; 42 }
        val result = service.execute("key-1") { executionCount++; 99 }

        result.getOrThrow() shouldBe 42  // Original result
        executionCount shouldBe 1  // Only executed once
    }

    "different keys should execute independently" {
        val service = IdempotentService<String, Int>()

        val result1 = service.execute("key-1") { 42 }
        val result2 = service.execute("key-2") { 99 }

        result1.getOrThrow() shouldBe 42
        result2.getOrThrow() shouldBe 99
    }

    "failed operations should not be cached (allow retry)" {
        val service = IdempotentService<String, Int>()
        val attempts = AtomicInteger(0)

        // First attempt fails
        val result1 = service.execute("key-1") {
            if (attempts.incrementAndGet() == 1) {
                throw RuntimeException("Network error")
            }
            42
        }

        result1.isFailure shouldBe true

        // Retry should execute again
        val result2 = service.execute("key-1") {
            attempts.incrementAndGet()
            42
        }

        result2.getOrThrow() shouldBe 42
        attempts.get() shouldBe 2
    }

    "expired keys should allow re-execution" {
        val clock = FakeClock()
        val service = IdempotentService<String, Int>(ttlMs = 100, clock = clock::now)
        var executionCount = 0

        service.execute("key-1") { executionCount++; 42 }
        clock.advance(150)  // Expire the entry
        service.execute("key-1") { executionCount++; 99 }

        executionCount shouldBe 2
    }

    "concurrent requests with same key should execute only once" {
        val service = IdempotentService<String, Int>()
        val executionCount = AtomicInteger(0)
        val startLatch = CountDownLatch(1)
        val results = mutableListOf<Result<Int>>()

        // Launch 10 concurrent requests with same key
        val threads = (1..10).map {
            Thread {
                startLatch.await()  // All start together
                val result = runCatching {
                    service.execute("key-1") {
                        executionCount.incrementAndGet()
                        Thread.sleep(50)  // Simulate work
                        42
                    }
                }
                result.onSuccess {
                    synchronized(results) { results.add(it) }
                }
            }.also { it.start() }
        }

        startLatch.countDown()  // Release all threads
        threads.forEach { it.join() }

        // Only ONE execution, all get same result
        executionCount.get() shouldBe 1
    }

    "concurrent requests should not both execute (race condition)" {
        val service = IdempotentService<String, Int>()
        val executionCount = AtomicInteger(0)

        // Simulate race: two threads check cache simultaneously
        val latch = CountDownLatch(2)
        val results = mutableListOf<Result<Int>>()

        repeat(2) { i ->
            Thread {
                latch.countDown()
                latch.await()  // Sync start

                val result = runCatching {
                    service.execute("race-key") {
                        executionCount.incrementAndGet()
                        Thread.sleep(10)
                        100 + i
                    }
                }
                result.onSuccess {
                    synchronized(results) { results.add(it) }
                }
            }.start()
        }

        Thread.sleep(200)  // Wait for completion

        // Must execute exactly once
        executionCount.get() shouldBe 1
        // Both should get same result
    }
})

class FakeClock(private var time: Long = 0) {
    fun now(): Long = time
    fun advance(ms: Long) {
        time += ms
    }
}

// State of an idempotency key
sealed class KeyState<V> {
    class InProgress<V> : KeyState<V>()  // Operation running
    data class Completed<V>(val value: V, val timestamp: Long) : KeyState<V>()
}

class IdempotentService<K, V>(
    private val ttlMs: Long = Long.MAX_VALUE,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val cache = mutableMapOf<K, KeyState<V>>()

    fun execute(idempotencyKey: K, operation: () -> V): Result<V> {
        // TODO: Implement with proper locking for concurrent requests
        // 
        // 1. synchronized(cache) {
        //      - Check if key exists and is Completed and not expired -> return cached
        //      - Check if key is InProgress -> wait for it (or return error)
        //      - Otherwise, mark as InProgress
        //    }
        // 
        // 2. Execute operation (outside lock!)
        // 
        // 3. synchronized(cache) {
        //      - On success: store Completed result
        //      - On failure: remove InProgress marker (allow retry)
        //    }
        //
        // Hint: You can use wait()/notifyAll() or a simple spin loop for waiting

        // simple impl

        synchronized(cache) {
            if (cache.containsKey(idempotencyKey)) {
                when (val value = cache[idempotencyKey]!!) {
                    is KeyState.Completed if !isExpired(value) -> return Result.success(value.value)
                    is KeyState.InProgress -> return Result.failure(IllegalAccessError("in progress"))
                    else -> cache[idempotencyKey] = KeyState.InProgress()
                }
            } else cache[idempotencyKey] = KeyState.InProgress()
        }

        val runCatching = runCatching { operation() }
        return synchronized(cache) {
            runCatching.onSuccess {
                cache[idempotencyKey] = KeyState.Completed(it, clock())
            }.onFailure { _ -> cache.remove(idempotencyKey) }
        }
    }

    private fun isExpired(state: KeyState.Completed<V>): Boolean {
        return clock() - state.timestamp > ttlMs
    }
}