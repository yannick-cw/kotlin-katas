import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class DelegationSpec : StringSpec({

    "lazy should compute only once" {
        var computeCount = 0

        val holder = object {
            val value: String by lazy {
                computeCount++
                "computed"
            }
        }

        holder.value shouldBe "computed"
        holder.value shouldBe "computed"
        computeCount shouldBe 1
    }

    "observable should track changes" {
        val changes = mutableListOf<Pair<String, String>>()

        var name: String by observable("initial") { old, new ->
            changes.add(old to new)
        }

        println(name)
        name = "first"
        name = "second"

        changes shouldBe listOf("initial" to "first", "first" to "second")
    }

    "vetoable should allow rejecting changes" {
        var age: Int by vetoable(0) { old, new ->
            new >= 0  // Only allow non-negative
        }

        age = 25
        age shouldBe 25

        age = -5  // Should be rejected
        age shouldBe 25
    }

    "cached delegate should recompute after timeout" {
        var computeCount = 0
        var currentTime = 0L

        val value: String by cached(ttlMs = 100, timeProvider = { currentTime }) {
            computeCount++
            "value-$computeCount"
        }

        value shouldBe "value-1"
        value shouldBe "value-1"  // Still cached

        currentTime = 150  // Expire cache
        value shouldBe "value-2"  // Recomputed
    }
})

// TODO: Implement observable delegate function
fun <T> observable(
    initial: T,
    onChange: (old: T, new: T) -> Unit
): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    private var value: T = initial
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        onChange(this.value, value)
        this.value = value
    }
}

// TODO: Implement vetoable delegate function
fun <T> vetoable(
    initial: T,
    shouldChange: (old: T, new: T) -> Boolean
): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    private var value: T = initial
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (shouldChange(this.value, value)) {
            this.value = value
        }
    }
}

// TODO: Implement cached delegate (more challenging)

// to easy, I do not like this, it is mixing side effects to deeply, I'll skip it
fun <T> cached(
    ttlMs: Long,
    timeProvider: () -> Long = System::currentTimeMillis,
    compute: () -> T
): ReadWriteProperty<Any?, T> {
    TODO()
}