import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class VolatileSpec : StringSpec({

    "volatile flag should be visible across threads" {
        val stopper = ThreadStopper()
        val counter = AtomicInteger(0)

        val worker = Thread {
            while (stopper.isRunning()) {
                counter.incrementAndGet()
                Thread.yield()
            }
        }

        worker.start()
        Thread.sleep(50)
        stopper.stop()
        worker.join(1000)

        stopper.isRunning() shouldBe false
        (counter.get() > 0) shouldBe true
    }

    "spin wait should see value change" {
        val holder = ValueHolder()
        val latch = CountDownLatch(1)

        val reader = Thread {
            holder.spinUntilReady()
            latch.countDown()
        }

        reader.start()
        Thread.sleep(50)
        holder.setReady()

        val completed = latch.await(1, TimeUnit.SECONDS)
        completed shouldBe true
    }

    "publication should make object fully visible" {
        val publisher = SafePublisher()
        val results = mutableListOf<String?>()
        val latch = CountDownLatch(1)

        val reader = Thread {
            while (publisher.getData() == null) {
                Thread.yield()
            }
            results.add(publisher.getData()?.value)
            latch.countDown()
        }

        reader.start()
        Thread.sleep(50)
        publisher.publish(Data("hello"))

        latch.await(1, TimeUnit.SECONDS)
        results.first() shouldBe "hello"
    }
})

// TODO: Implement with proper volatile usage

class ThreadStopper {
    // Should be volatile!
    @Volatile
    private var running = true

    fun isRunning(): Boolean = running

    fun stop() {
        running = false
    }
}

class ValueHolder {
    // Should be volatile!
    @Volatile
    private var ready = false

    fun spinUntilReady() {
        while (!ready) {
            Thread.yield()
        }
    }

    fun setReady() {
        ready = true
    }
}

data class Data(val value: String)

class SafePublisher {
    // Should be volatile to ensure Data is fully published
    @Volatile
    private var data: Data? = null

    fun getData(): Data? = data

    fun publish(d: Data) {
        data = d
    }
}
