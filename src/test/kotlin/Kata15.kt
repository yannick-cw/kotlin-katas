import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.comparables.shouldBeGreaterThan
import kotlin.math.max

class HappensBeforeSpec : StringSpec({

    "local events should be ordered" {
        val clock = LamportClock()

        val t1 = clock.tick()  // First event
        val t2 = clock.tick()  // Second event
        val t3 = clock.tick()  // Third event

        t2 shouldBeGreaterThan t1
        t3 shouldBeGreaterThan t2
    }

    "send should return timestamp for message" {
        val sender = LamportClock()
        sender.tick()  // Local event

        val sendTime = sender.send()

        sendTime shouldBeGreaterThan 0
    }

    "receive should sync clocks and advance" {
        val sender = LamportClock()
        val receiver = LamportClock()

        // Sender does 5 local events then sends
        repeat(5) { sender.tick() }
        val messageTime = sender.send()

        // Receiver receives (was behind)
        val receiveTime = receiver.receive(messageTime)

        // Receiver's clock should now be > sender's message time
        receiveTime shouldBeGreaterThan messageTime
    }

    "receive establishes happens-before" {
        val a = LamportClock()
        val b = LamportClock()

        // A: tick, tick, send
        a.tick()
        a.tick()
        val msgTime = a.send()

        // B: tick (concurrent with A's events)
        b.tick()

        // B receives A's message
        val afterReceive = b.receive(msgTime)

        // Now B's clock > A's send time (happens-before established)
        afterReceive shouldBeGreaterThan msgTime

        // Any event after receive happens-after the send
        val laterEvent = b.tick()
        laterEvent shouldBeGreaterThan msgTime
    }

    "concurrent events can have same timestamp" {
        val a = LamportClock()
        val b = LamportClock()

        // No communication = concurrent
        val aTime = a.tick()
        val bTime = b.tick()

        // Both start at 1, no ordering between them
        aTime shouldBe 1
        bTime shouldBe 1
    }
    "MY Addition => what about clashes?" {
        val a = LamportClock()
        val b = LamportClock()

        // No communication = concurrent
        val aTime = a.tick()
        val bTime = b.tick()

        val aSend = a.send()
        val breceive = b.receive(aSend)

        // Happened at same time but now b is ahead - explain
        // ah does this show - **But NOT the converse: C(A) < C(B) doesn't mean A → B** !V
        breceive shouldBe 2
    }

    "causal ordering should be respected in complex scenario" {
        val alice = LamportClock()
        val bob = LamportClock()
        val charlie = LamportClock()

        // Alice sends to Bob
        val msg1 = alice.send()
        bob.receive(msg1)

        // Bob sends to Charlie
        val msg2 = bob.send()
        charlie.receive(msg2)

        // Charlie's receive happens-after Alice's send
        charlie.current() shouldBeGreaterThan msg1
    }
})

// Lamport Clock: simplest logical clock
// Rule 1: On local event, increment counter
// Rule 2: On send, increment counter, attach timestamp
// Rule 3: On receive, set counter = max(local, received) + 1
class LamportClock {
    private var time = 0

    // Local event: increment and return new time
    fun tick(): Int {
        time += 1
        return time
    }

    // Send: increment and return timestamp to attach to message
    fun send(): Int {
        return time
    }

    // Receive: sync with sender's time, then increment
    fun receive(messageTime: Int): Int {
        time = max(messageTime, time) + 1
        return time
    }

    fun current(): Int = time
}
