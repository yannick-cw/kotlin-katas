import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LruCacheSpec : StringSpec({

    "should return null for missing key" {
        val cache = LruCache<String, Int>(capacity = 2)
        cache.get("missing") shouldBe null
    }

    "should store and retrieve values" {
        val cache = LruCache<String, Int>(capacity = 2)
        cache.put("a", 1)
        cache.put("b", 2)

        cache.get("a") shouldBe 1
        cache.get("b") shouldBe 2
    }

    "should evict least recently used when over capacity" {
        val cache = LruCache<String, Int>(capacity = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)  // Should evict "a"

        cache.get("a") shouldBe null
        cache.get("b") shouldBe 2
        cache.get("c") shouldBe 3
    }

    "get should update recency" {
        val cache = LruCache<String, Int>(capacity = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a")     // Access "a" - now most recent
        cache.put("c", 3)  // Should evict "b" (least recent)

        cache.get("a") shouldBe 1
        cache.get("b") shouldBe null
        cache.get("c") shouldBe 3
    }

    "put should update existing value" {
        val cache = LruCache<String, Int>(capacity = 2)
        cache.put("a", 1)
        cache.put("a", 100)

        cache.get("a") shouldBe 100
    }
})

// TODO: Implement LRU Cache

class LruCache<K, V>(private val capacity: Int) {
    var items: java.util.LinkedList<Pair<K, V>> = java.util.LinkedList()
    val map = HashMap<K, V>(capacity)

    fun get(key: K): V? {
        // TODO how O(1) update last recently used one?
        return map.get(key)
    }

    fun put(key: K, value: V) {
        if (items.size == capacity) {
            val popped = items.pop()
            map.remove(popped.first)
        }
        items.addLast(key to value)
        map.put(key, value)
    }
}