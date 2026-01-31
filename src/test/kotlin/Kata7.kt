import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class InlineReifiedSpec : StringSpec({

    "filterIsInstance should filter by type" {
        val mixed: List<Any> = listOf(1, "hello", 2, "world", 3.14)

        val strings = mixed.filterByType<String>()
        val ints = mixed.filterByType<Int>()

        strings shouldBe listOf("hello", "world")
        ints shouldBe listOf(1, 2)
    }

    // skip, I do not care for this type casting magic
    // stupid shit, fails at runtime if not constructor
    "create should instantiate class with no-arg constructor" {
        val instance = create<SimpleClass>()
        instance.shouldBeInstanceOf<SimpleClass>()
    }

    "safecast should return null for wrong type" {
        val any: Any = "hello"

        any.safeCast<String>() shouldBe "hello"
        any.safeCast<Int>() shouldBe null
    }

    "typeName should return class name" {
        typeName<String>() shouldBe "String"
        typeName<List<Int>>() shouldBe "List"
    }

    "measureTime inline should return result and duration" {
        val (result, duration) = measureTimeWithResult {
            Thread.sleep(50)
            "computed"
        }

        result shouldBe "computed"
        (duration >= 50) shouldBe true
    }
})

class SimpleClass {
    val name = "simple"
}

// TODO: Implement these inline/reified functions

// TODO any vs *?
inline fun <reified T> List<*>.filterByType(): List<T> = mapNotNull {
    it?.safeCast<T>()
}

inline fun <reified T : Any> create(): T {
    return T::class.java.getDeclaredConstructor().newInstance()
}

inline fun <reified T> Any.safeCast(): T? =
    // or just the build in as?
    if (this is T) {
        this
    } else {
        null
    }


inline fun <reified T> typeName(): String? {
    return T::class.simpleName
}

// Non-reified inline - just for performance (no lambda allocation)
inline fun <T> measureTimeWithResult(block: () -> T): Pair<T, Long> {
    val startTime = System.currentTimeMillis()
    val result = block()
    val elapsedTime = System.currentTimeMillis() - startTime
    return result to elapsedTime
}