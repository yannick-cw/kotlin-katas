import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SequencesSpec : StringSpec({

    "sequence should process lazily" {
        val log = mutableListOf<String>()

        val result = sequenceOf(1, 2, 3, 4, 5)
            .map {
                log.add("map $it")
                it * 2
            }
            .filter {
                log.add("filter $it")
                it > 4
            }
            .first()

        result shouldBe 6
        // Only processes until first match: 1->2 (filtered), 2->4 (filtered), 3->6 (kept)
        log shouldBe listOf("map 1", "filter 2", "map 2", "filter 4", "map 3", "filter 6")
    }

    "generateSequence should create infinite sequence" {
        val powers = generatePowersOfTwo()
            .take(5)
            .toList()

        powers shouldBe listOf(1, 2, 4, 8, 16)
    }

    "sequence builder should yield values" {
        val fib = fibonacciSequence()
            .take(8)
            .toList()

        fib shouldBe listOf(0, 1, 1, 2, 3, 5, 8, 13)
    }

    "chunked should group elements" {
        val result = (1..10).asSequence()
            .chunked(3)
            .toList()

        result shouldBe listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(10))
    }

    "windowed should create sliding windows" {
        val result = (1..5).asSequence()
            .windowed(3)
            .toList()

        result shouldBe listOf(listOf(1, 2, 3), listOf(2, 3, 4), listOf(3, 4, 5))
    }
})

// TODO: Implement these functions

fun generatePowersOfTwo(): Sequence<Int> = generateSequence(1) { it * 2 }


fun fibonacciSequence(): Sequence<Int> = generateSequence(0 to 1) { (a, b) -> b to a + b }.map { it.first }

