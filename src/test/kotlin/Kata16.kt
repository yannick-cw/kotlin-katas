import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.math.max

class VectorClockSpec : StringSpec({

    "local event should increment own counter" {
        val clock = VectorClock("A")
        clock.tick()
        clock.tick()

        clock.get("A") shouldBe 2
        clock.get("B") shouldBe 0  // Unknown nodes default to 0
    }

    "send should return clock snapshot for message" {
        val clock = VectorClock("A")
        clock.tick()
        clock.tick()

        val snapshot = clock.send()

        snapshot["A"] shouldBe 3  // tick increments on send too
    }

    "receive should merge and increment" {
        val clockA = VectorClock("A")
        val clockB = VectorClock("B")

        clockA.tick()  // A: {A:1}
        clockA.tick()  // A: {A:2}
        val msg = clockA.send()  // A: {A:3}

        clockB.tick()  // B: {B:1}
        clockB.receive(msg)  // B merges: max({B:1}, {A:3}) + tick

        clockB.get("A") shouldBe 3  // From A's message
        clockB.get("B") shouldBe 2  // Own tick + receive tick
    }

    "compare should detect happens-before" {
        val a = mapOf("A" to 1, "B" to 0)
        val b = mapOf("A" to 2, "B" to 0)

        VectorClock.compare(a, b) shouldBe Ordering.BEFORE
        VectorClock.compare(b, a) shouldBe Ordering.AFTER
    }

    "compare should detect equality" {
        val a = mapOf("A" to 2, "B" to 3)
        val b = mapOf("A" to 2, "B" to 3)

        VectorClock.compare(a, b) shouldBe Ordering.EQUAL
    }

    "compare should detect concurrency" {
        // A did 2 events, B did 1 event, no communication
        val a = mapOf("A" to 2, "B" to 0)
        val b = mapOf("A" to 0, "B" to 1)

        VectorClock.compare(a, b) shouldBe Ordering.CONCURRENT
    }

    "concurrent writes should be detected as conflict" {
        val alice = VectorClock("alice")
        val bob = VectorClock("bob")

        // Both start from same state, then diverge
        alice.tick()  // Alice writes
        bob.tick()    // Bob writes (concurrently)

        val aliceVersion = alice.snapshot()
        val bobVersion = bob.snapshot()

        // Neither happened-before the other = CONFLICT
        VectorClock.compare(aliceVersion, bobVersion) shouldBe Ordering.CONCURRENT
    }

    "causal chain should be tracked across nodes" {
        val a = VectorClock("A")
        val b = VectorClock("B")
        val c = VectorClock("C")

        // A sends to B
        a.tick()
        val msg1 = a.send()
        b.receive(msg1)

        // B sends to C
        b.tick()
        val msg2 = b.send()
        c.receive(msg2)

        // C's clock should show: A's event happened-before C's current state
        val aSnapshot = mapOf("A" to 2)  // A after send
        val cSnapshot = c.snapshot()

        VectorClock.compare(aSnapshot, cSnapshot) shouldBe Ordering.BEFORE
    }
})

enum class Ordering { BEFORE, AFTER, CONCURRENT, EQUAL }

class VectorClock(private val nodeId: String) {
    private val clock = mutableMapOf<String, Int>()

    init {
        clock[nodeId] = 0
    }

    fun tick() =
        clock.computeIfPresent(nodeId) { _, time -> time + 1 }


    fun send(): Map<String, Int> {
        tick()
        return clock
    }

    fun receive(other: Map<String, Int>) {
        // TODO: Question if other has a higher counter for myself, do I take that, why?
        // Answer: Yes, because I might not know my own latest state
        other.forEach { (key, otherTime) ->
            clock.compute(key) { _, localTime ->
                max(otherTime, localTime ?: 0)
            }
        }
        tick()
    }

    fun get(nodeId: String): Int = clock[nodeId] ?: 0

    fun snapshot(): Map<String, Int> = clock.toMap()

    companion object {
        // Compare two vector clock snapshots
        // BEFORE: all of a <= all of b, AND at least one a[i] < b[i]
        // AFTER: all of a >= all of b, AND at least one a[i] > b[i]
        // EQUAL: all equal
        // CONCURRENT: some a[i] < b[i] AND some a[j] > b[j]
        fun compare(a: Map<String, Int>, b: Map<String, Int>): Ordering {
            fun isMap1AfterMap2(
                map1: Map<String, Int>, map2: Map<String, Int>
            ): Boolean = map1.keys.containsAll(map2.keys) && map1.all { map ->
                map.value >= (map2[map.key] ?: 0)
            } && map1.any { map -> map.value > (map2[map.key] ?: 0) }

            return if (isMap1AfterMap2(a, b)) {
                Ordering.AFTER
            } else if (isMap1AfterMap2(b, a)) {
                Ordering.BEFORE
            } else if (a == b) Ordering.EQUAL
            else Ordering.CONCURRENT
        }
    }
}
