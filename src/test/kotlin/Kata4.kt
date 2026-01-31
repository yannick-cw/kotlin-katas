import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.math.min

class ExtensionFunctionsSpec : StringSpec({

    "String.words should split into words" {
        "hello world kotlin".words() shouldBe listOf("hello", "world", "kotlin")
    }

    "String.words should handle multiple spaces" {
        "hello   world".words() shouldBe listOf("hello", "world")
    }

    "String.truncate should limit length with ellipsis" {
        "Hello World".truncate(5) shouldBe "He..."
        "Hi".truncate(5) shouldBe "Hi"
    }

    "Int.times should repeat action n times" {
        var counter = 0
        3.times { counter++ }
        counter shouldBe 3
    }

    "List<Int>.median should return middle value" {
        listOf(1, 3, 5).median() shouldBe 3.0
        listOf(1, 2, 3, 4).median() shouldBe 2.5
    }

    "nullable String.orEmpty should handle nulls" {
        val s1: String? = null
        val s2: String? = "hello"

        s1.orEmpty() shouldBe ""
        s2.orEmpty() shouldBe "hello"
    }

    "Map.getOrThrow should throw on missing key" {
        val map = mapOf("a" to 1, "b" to 2)

        map.getOrThrow("a") shouldBe 1

        val exception = runCatching { map.getOrThrow("c") }
        exception.isFailure shouldBe true
    }
})


fun String.words(): List<String> = split(regex = " +".toRegex())

fun String.truncate(maxLength: Int): String {
    val newContent = take(2)
    val dots = ".".repeat((min(maxLength, length)) - newContent.length)

    return newContent + dots
}

fun Int.times(action: () -> Unit) {
    (0 until this).forEach { action() }
}

fun List<Int>.median(): Double {
    return if (size % 2 == 0) {
        val left = this.elementAt((size / 2) - 1).toDouble()
        val right = this.elementAt(size / 2).toDouble()
        (left + right) / 2.0
    } else {
        this.elementAt((size / 2)).toDouble()
    }
}

fun String?.orEmpty(): String = this ?: ""

fun <K, V> Map<K, V>.getOrThrow(key: K): V {
    return this.get(key) ?: throw NullPointerException("Key $key not found")
}