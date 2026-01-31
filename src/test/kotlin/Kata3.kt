import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DataClassesSpec : StringSpec({
    "copy should create new instance with modified fields" {
        val original = User(id = 1, name = "Alice", email = "alice@example.com")
        val updated = original.copy(email = "newalice@example.com")

        updated.id shouldBe 1
        updated.name shouldBe "Alice"
        updated.email shouldBe "newalice@example.com"
        updated shouldNotBe original
    }

    "destructuring should work with component functions" {
        val user = User(id = 42, name = "Bob", email = "bob@example.com")
        val (id, name, email) = user

        id shouldBe 42
        name shouldBe "Bob"
        email shouldBe "bob@example.com"
    }

    "equals should compare by content not reference" {
        val user1 = User(1, "Alice", "alice@example.com")
        val user2 = User(1, "Alice", "alice@example.com")

        user1 shouldBe user2
        (user1 === user2) shouldBe false // Different instances
    }

    "should implement immutable update pattern" {
        val users = listOf(User(1, "Alice", "alice@old.com"), User(2, "Bob", "bob@old.com"))

        val updated = users.updateEmail(1, "alice@new.com")

        updated[0].email shouldBe "alice@new.com"
        updated[1].email shouldBe "bob@old.com"
        users[0].email shouldBe "alice@old.com" // Original unchanged
    }

    "should merge two users keeping non-null values" {
        val base = User(1, "Alice", "alice@example.com")
        val updates = PartialUser(name = null, email = "new@example.com")

        val merged = base.mergeWith(updates)

        merged.name shouldBe "Alice" // Kept from base
        merged.email shouldBe "new@example.com" // Updated
    }
})

// TODO: Define the data class
data class User(val id: Long, val name: String, val email: String)

// For partial updates
data class PartialUser(val name: String?, val email: String?)

fun List<User>.updateEmail(userId: Long, newEmail: String): List<User> = map {
    if (it.id == userId) {
        it.updateEmail(newEmail)
    } else {
        it
    }
}

fun User.updateEmail(newEmail: String): User = copy(email = newEmail)

fun User.mergeWith(partial: PartialUser): User =
    copy(name = partial.name ?: name, email = partial.email ?: email)
