import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger

class CircuitBreakerSpec : StringSpec({

    "should start in closed state" {
        val cb = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 1000)
        cb.state shouldBe State.CLOSED
    }

    "should allow successful calls in closed state" {
        val cb = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 1000)

        val result = cb.execute { "success" }

        result.getOrNull() shouldBe "success"
        cb.state shouldBe State.CLOSED
    }

    "should open after threshold consecutive failures" {
        val cb = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 1000)

        repeat(3) {
            cb.execute { throw RuntimeException("fail") }
        }

        cb.state shouldBe State.OPEN
    }

    "should reset failure count on success" {
        val cb = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 1000)

        cb.execute { throw RuntimeException("fail") }  // 1 failure
        cb.execute { throw RuntimeException("fail") }  // 2 failures
        cb.execute { "success" }                        // Reset!
        cb.execute { throw RuntimeException("fail") }  // 1 failure (not 3)

        cb.state shouldBe State.CLOSED  // Not open yet
    }

    "should fail fast when open" {
        val cb = CircuitBreaker(failureThreshold = 1, resetTimeoutMs = 1000)
        val callCount = AtomicInteger(0)

        cb.execute { throw RuntimeException("fail") }  // Opens circuit
        cb.state shouldBe State.OPEN

        val result = cb.execute {
            callCount.incrementAndGet()
            "should not reach"
        }

        callCount.get() shouldBe 0  // Action was never called
        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe CircuitOpenException
    }

    "should transition to half-open after timeout" {
        val clock = FakeClock2()
        val cb = CircuitBreaker(
            failureThreshold = 1, resetTimeoutMs = 1000, clock = clock::now
        )

        cb.execute { throw RuntimeException("fail") }
        cb.state shouldBe State.OPEN

        clock.advance(500)
        cb.state shouldBe State.OPEN  // Not yet

        clock.advance(600)  // Total 1100ms
        cb.state shouldBe State.HALF_OPEN
    }

    "should close on successful test in half-open" {
        val clock = FakeClock2()
        val cb = CircuitBreaker(
            failureThreshold = 1, resetTimeoutMs = 1000, clock = clock::now
        )

        cb.execute { throw RuntimeException("fail") }  // -> OPEN
        clock.advance(1500)  // -> HALF_OPEN

        cb.execute { "success" }  // Test request succeeds

        cb.state shouldBe State.CLOSED
    }

    "should reopen on failure in half-open" {
        val clock = FakeClock2()
        val cb = CircuitBreaker(
            failureThreshold = 1, resetTimeoutMs = 1000, clock = clock::now
        )

        cb.execute { throw RuntimeException("fail") }  // -> OPEN
        clock.advance(1500)  // -> HALF_OPEN

        cb.execute { throw RuntimeException("still failing") }  // Test fails

        cb.state shouldBe State.OPEN
    }

    "should limit concurrent requests in half-open" {
        val clock = FakeClock2()
        val cb = CircuitBreaker(
            failureThreshold = 1, resetTimeoutMs = 1000, halfOpenMaxCalls = 1, clock = clock::now
        )

        cb.execute { throw RuntimeException("fail") }  // -> OPEN
        clock.advance(1500)  // -> HALF_OPEN

        // First call in half-open is allowed
        var firstAllowed = false
        var secondAllowed = false

        // Simulate first call starting (but not completing)
        cb.tryAcquireHalfOpen().also { firstAllowed = it }

        // Second call should be rejected
        cb.tryAcquireHalfOpen().also { secondAllowed = it }

        firstAllowed shouldBe true
        secondAllowed shouldBe false
    }

    "should track metrics" {
        val cb = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 1000)

        cb.execute { "success" }
        cb.execute { "success" }
        cb.execute { throw RuntimeException("fail") }

        cb.metrics.successCount shouldBe 2
        cb.metrics.failureCount shouldBe 1
        cb.metrics.totalCount shouldBe 3
    }
})

enum class State { CLOSED, OPEN, HALF_OPEN }

object CircuitOpenException : RuntimeException("Circuit breaker is open")

class FakeClock2(private var time: Long = 0) {
    fun now(): Long = time
    fun advance(ms: Long) {
        time += ms
    }
}

data class Metrics(
    var successCount: Long = 0, var failureCount: Long = 0
) {
    val totalCount: Long get() = successCount + failureCount
}

class CircuitBreaker(
    private val failureThreshold: Int,
    private val resetTimeoutMs: Long,
    private val halfOpenMaxCalls: Int = 1,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var _state = State.CLOSED
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var halfOpenCallsInProgress = 0

    val metrics = Metrics()

    val state: State
        get() {
            // TODO: Check if OPEN -> HALF_OPEN transition should happen
            // If _state == OPEN and enough time has passed, transition to HALF_OPEN

            return when (_state) {
                State.CLOSED -> _state
                State.OPEN -> {
                    if (clock() - lastFailureTime > resetTimeoutMs) transitionTo(State.HALF_OPEN)
                    _state
                }

                State.HALF_OPEN -> _state
            }
        }

    fun <T> execute(action: () -> T): Result<T> {
        // TODO: Implement the full state machine
        //
        // CLOSED:
        //   - Execute action
        //   - On success: reset failures, record metric
        //   - On failure: increment failures, if >= threshold -> OPEN
        //
        // OPEN:
        //   - Return failure immediately (don't call action)
        //
        // HALF_OPEN:
        //   - Check if we can make a test call (limited concurrency)
        //   - If allowed: execute action
        //     - On success: transition to CLOSED
        //     - On failure: transition to OPEN
        //   - If not allowed: fail fast

        if (_state == State.OPEN) {
            if (clock() - lastFailureTime > resetTimeoutMs) transitionTo(State.HALF_OPEN)
        }

        return when (_state) {
            State.CLOSED -> {
                val resultOfAction = runCatching { action() }
                resultOfAction.onSuccess { recordSuccess() }
                resultOfAction.onFailure {
                    recordFailure()
                    if (failureCount >= failureThreshold) transitionTo(State.OPEN)
                }
                resultOfAction
            }

            State.OPEN -> Result.failure(CircuitOpenException)
            State.HALF_OPEN -> {
                if (tryAcquireHalfOpen()) {
                    halfOpenCallsInProgress += 1
                    val resultOfAction = runCatching { action() }
                    resultOfAction.onSuccess {
                        transitionTo(State.CLOSED)
                        recordSuccess()
                    }
                    resultOfAction.onFailure {
                        recordFailure()
                        transitionTo(State.OPEN)
                    }
                    releaseHalfOpen()
                    resultOfAction
                } else {
                    Result.failure(RuntimeException("Already half open in progress"))
                }
            }
        }
    }

    // For half-open concurrency control
    fun tryAcquireHalfOpen(): Boolean {
        // TODO: Return true if we can make a half-open test call
        // Only allow up to halfOpenMaxCalls concurrent test requests
        return halfOpenCallsInProgress <= halfOpenMaxCalls
    }

    private fun releaseHalfOpen() {
        halfOpenCallsInProgress--
    }

    private fun transitionTo(newState: State) {
        _state = newState
        if (newState == State.CLOSED) {
            failureCount = 0
        }
        if (newState == State.HALF_OPEN) {
            halfOpenCallsInProgress = 0
        }
    }

    private fun recordSuccess() {
        metrics.successCount++
        failureCount = 0
    }

    private fun recordFailure() {
        metrics.failureCount++
        failureCount++
        lastFailureTime = clock()
    }
}