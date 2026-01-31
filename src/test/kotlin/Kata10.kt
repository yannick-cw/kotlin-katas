import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RingBufferSpec : StringSpec({

    "should store and retrieve values" {
        val buffer = RingBuffer<Int>(capacity = 3)
        buffer.write(1)
        buffer.write(2)

        buffer.read() shouldBe 1
        buffer.read() shouldBe 2
    }

    "should return null when empty" {
        val buffer = RingBuffer<Int>(capacity = 3)
        buffer.read() shouldBe null
    }

    "should overwrite oldest when full" {
        val buffer = RingBuffer<Int>(capacity = 3)
        buffer.write(1)
        buffer.write(2)
        buffer.write(3)
        buffer.write(4)  // Overwrites 1

        buffer.read() shouldBe 2
        buffer.read() shouldBe 3
        buffer.read() shouldBe 4
        buffer.read() shouldBe null
    }

    "should report correct size" {
        val buffer = RingBuffer<Int>(capacity = 3)
        buffer.size() shouldBe 0

        buffer.write(1)
        buffer.size() shouldBe 1

        buffer.write(2)
        buffer.write(3)
        buffer.size() shouldBe 3

        buffer.write(4)  // Overwrite
        buffer.size() shouldBe 3
    }

    "should report isEmpty and isFull" {
        val buffer = RingBuffer<Int>(capacity = 2)

        buffer.isEmpty() shouldBe true
        buffer.isFull() shouldBe false

        buffer.write(1)
        buffer.write(2)

        buffer.isEmpty() shouldBe false
        buffer.isFull() shouldBe true
    }

    "toList should return elements in order" {
        val buffer = RingBuffer<Int>(capacity = 3)
        buffer.write(1)
        buffer.write(2)
        buffer.write(3)
        buffer.write(4)  // Overwrites 1

        buffer.toList() shouldBe listOf(2, 3, 4)
    }
})

// TODO: Implement Ring Buffer

class RingBuffer<T>(private val capacity: Int) {
    private val buffer = arrayOfNulls<Any?>(capacity)
    private var writePos = 0
    private var readPos = 0
    private var currentSize = 0


    fun write(element: T) {
        buffer[writePos] = element
        val nextPost = writePos + 1

        writePos = if (nextPost == capacity) {
            readPos = (readPos + 1) % capacity
            nextPost % capacity
        } else nextPost

        currentSize = Math.min(currentSize + 1, capacity)
        renderState("wrote $element")
    }

    @Suppress("UNCHECKED_CAST")
    fun read(): T? {
        val currentRead = buffer[(readPos % capacity)]
        readPos = (readPos + 1) % capacity
        renderState("read $currentRead")
        return if (currentRead != null && currentSize > 0) {
            currentSize -= 1
            currentRead as T
        } else null
    }

    fun size(): Int = currentSize


    fun isEmpty(): Boolean = currentSize == 0

    fun isFull(): Boolean = currentSize == capacity

    fun toList(): List<T> {
        val next = read()
        return if (next == null) {
            emptyList<T>()
        } else {
            listOf(next) + toList()
        }
    }

    private fun renderState(action: String) {
        println("┌─────────────────────────────────────────┐")
        println("│ $action")
        println("├─────────────────────────────────────────┤")

        // Top markers for read/write positions
        print("  ")
        for (i in buffer.indices) {
            when {
                i == readPos && i == writePos -> print("  R/W  ")
                i == readPos -> print("   R   ")
                i == writePos -> print("   W   ")
                else -> print("       ")
            }
        }
        println()

        // Top border
        print("  ")
        for (i in buffer.indices) {
            if (i == 0) print("┌─────") else print("┬─────")
        }
        println("┐")

        // Buffer contents
        print("  ")
        for (i in buffer.indices) {
            val value = buffer[i]
            val content = when {
                value == null -> "  -  "
                value.toString().length > 3 -> value.toString().take(3) + ".."
                else -> value.toString().padStart(3).padEnd(5)
            }
            print("│$content")
        }
        println("│")

        // Bottom border with indices
        print("  ")
        for (i in buffer.indices) {
            if (i == 0) print("└─────") else print("┴─────")
        }
        println("┘")

        // Index labels
        print("  ")
        for (i in buffer.indices) {
            print("  [$i]  ")
        }
        println()

        // Status info
        println("├─────────────────────────────────────────┤")
        println("│ Size: $currentSize/$capacity  │  Empty: ${isEmpty()}  │  Full: ${isFull()}")
        println("└─────────────────────────────────────────┘")
        println()
    }

//    var ring: java.util.LinkedList<T> = java.util.LinkedList()
//
//    fun write(element: T) {
//        if (ring.size == capacity && capacity > 0) {
//            ring.removeFirst()
//        }
//        ring.addLast(element)
//    }
//
//    fun read(): T? {
//        return if (ring.isEmpty()) null else ring.removeFirst()
//    }
//
//    fun size(): Int {
//        return ring.size
//    }
//
//    fun isEmpty(): Boolean {
//        return ring.isEmpty()
//    }
//
//    fun isFull(): Boolean {
//        return ring.size == capacity
//    }
//
//    fun toList(): List<T> {
//        return ring.toList()
//    }
}