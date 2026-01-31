import org.junit.jupiter.api.Assertions.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ScopeFunctionsSpec : StringSpec({

    "myLet should pass object as 'it' and return lambda result" {
        val result = "hello".myLet { it.length }
        result shouldBe 5
    }

    "myApply should pass object as 'this' and return the object" {
        val list = mutableListOf<String>().myApply {
            add("one")
            add("two")
        }
        list shouldBe listOf("one", "two")
    }

    "myAlso should pass object as 'it' and return the object" {
        var sideEffect = ""
        val result = "hello".myAlso { sideEffect = it.uppercase() }
        result shouldBe "hello"
        sideEffect shouldBe "HELLO"
    }

    "myRun should pass object as 'this' and return lambda result" {
        val result = StringBuilder().myRun {
            append("Hello")
            append(" World")
            toString()
        }
        result shouldBe "Hello World"
    }

    "myWith should take object, use 'this', return lambda result" {
        val result = myWith(StringBuilder()) {
            append("Kotlin")
            length
        }
        result shouldBe 6
    }
})

// TODO: Implement these functions

inline fun <T, R> T.myLet(block: (T) -> R): R {
    return block(this)
}

inline fun <T> T.myApply(block: T.() -> Unit): T {
    block(this)
    return this
}

inline fun <T> T.myAlso(block: (T) -> Unit): T {
    block(this)
    return this
}

inline fun <T, R> T.myRun(block: T.() -> R): R {
    return block(this)
}

inline fun <T, R> myWith(receiver: T, block: T.() -> R): R {
    return block(receiver)
}
