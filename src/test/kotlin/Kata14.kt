import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class ThreadPoolsSpec : StringSpec({

    "fixed pool should limit concurrent execution" {
        val maxConcurrent = AtomicInteger(0)
        val current = AtomicInteger(0)

        val executor = createFixedPool(poolSize = 2)

        val futures = (1..10).map {
            executor.submit {
                val c = current.incrementAndGet()
                maxConcurrent.updateAndGet { max -> maxOf(max, c) }
                Thread.sleep(50)
                current.decrementAndGet()
            }
        }

        futures.forEach { it.get() }
        executor.shutdown()

        maxConcurrent.get() shouldBe 2
    }

    "future should return computed value" {
        val executor = Executors.newSingleThreadExecutor()

        val future = computeAsync(executor) {
            Thread.sleep(50)
            42
        }

        future.get(1, TimeUnit.SECONDS) shouldBe 42
        executor.shutdown()
    }
})

// TODO: Create a fixed thread pool
fun createFixedPool(poolSize: Int): ExecutorService {
    return Executors.newFixedThreadPool(poolSize)
}

// TODO: Submit a callable that returns a result
fun <T> computeAsync(executor: ExecutorService, computation: () -> T): Future<T> {
    // Use executor.submit with a Callable

    return executor.submit(Callable { computation() })
}
