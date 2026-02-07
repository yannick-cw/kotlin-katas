import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class JoinsSpec : StringSpec({

    val users = listOf(
        User(1, "Alice"), User(2, "Bob"), User(3, "Charlie")
    )

    val orders = listOf(
        Order(101, 1, 100),   // Alice's order
        Order(102, 2, 200),   // Bob's order
        Order(103, 4, 50)     // Unknown user's order
    )

    "inner join should return only matching rows" {
        val result = innerJoin(users, orders) { u, o -> u.id == o.userId }

        result.size shouldBe 2
        result.map { it.first.name } shouldBe listOf("Alice", "Bob")
    }

    "left join should include all left rows" {
        val result = leftJoin(users, orders) { u, o -> u.id == o.userId }

        result.size shouldBe 3
        result.map { (u, o) -> u.name to o?.orderId } shouldBe listOf(
            "Alice" to 101, "Bob" to 102, "Charlie" to null
        )
    }

    "right join should include all right rows" {
        val result = rightJoin(users, orders) { u, o -> u.id == o.userId }

        result.size shouldBe 3
        result.map { (u, o) -> u?.name to o.orderId } shouldBe listOf(
            "Alice" to 101, "Bob" to 102, null to 103
        )
    }

    "full outer join should include all rows from both" {
        val result = fullOuterJoin(users, orders) { u, o -> u.id == o.userId }

        result.size shouldBe 4
        // Alice-101, Bob-102, Charlie-null, null-103
    }

    "cross join should return cartesian product" {
        val a = listOf(1, 2)
        val b = listOf("x", "y", "z")

        val result = crossJoin(a, b)

        result.size shouldBe 6
        result shouldBe listOf(
            1 to "x", 1 to "y", 1 to "z", 2 to "x", 2 to "y", 2 to "z"
        )
    }
})

data class User(val id: Int, val name: String)

// foreign key user id
data class Order(val orderId: Int, val userId: Int, val amount: Int)

// TODO: Implement joins

fun <A, B> innerJoin(left: List<A>, right: List<B>, predicate: (A, B) -> Boolean): List<Pair<A, B>> =
    left.flatMap { l -> right.flatMap { r -> if (predicate(l, r)) listOf(l to r) else listOf() } }


fun <A, B> leftJoin(left: List<A>, right: List<B>, predicate: (A, B) -> Boolean): List<Pair<A, B?>> =
    left.flatMap { l ->
        right.flatMap { r ->
            listOf(l to if (predicate(l, r)) r else null)
        }
    }.groupBy { it.first }.values.toList().mapNotNull { it ->
        it.firstOrNull { it.second != null } ?: it.firstOrNull()
    }


fun <A, B> rightJoin(left: List<A>, right: List<B>, predicate: (A, B) -> Boolean): List<Pair<A?, B>> =
    leftJoin(right, left) { r, l -> predicate(l, r) }.map { (r, l) -> l to r }


fun <A, B> fullOuterJoin(left: List<A>, right: List<B>, predicate: (A, B) -> Boolean): List<Pair<A?, B?>> {
    val leftSide = leftJoin(left, right, predicate)
    val rightSide = rightJoin(left, right, predicate)

    return (leftSide + rightSide).distinct()
}

fun <A, B> crossJoin(left: List<A>, right: List<B>): List<Pair<A, B>> = innerJoin(left, right) { _, _ -> true }

