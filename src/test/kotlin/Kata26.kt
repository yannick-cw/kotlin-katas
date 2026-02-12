import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

class TrieSpec : StringSpec({

    "should insert and search words" {
        val trie = Trie()
        trie.insert("hello")
        trie.insert("help")

        trie.search("hello") shouldBe true
        trie.search("help") shouldBe true
        trie.search("hell") shouldBe false  // Prefix, not word
        trie.search("helper") shouldBe false
    }

    "should check if prefix exists" {
        val trie = Trie()
        trie.insert("hello")

        trie.startsWith("hel") shouldBe true
        trie.startsWith("hello") shouldBe true
        trie.startsWith("hellox") shouldBe false
        trie.startsWith("abc") shouldBe false
    }

    "should find all words with prefix" {
        val trie = Trie()
        listOf("car", "card", "care", "careful", "cat").forEach { trie.insert(it) }

        trie.wordsWithPrefix("car") shouldContainExactlyInAnyOrder listOf("car", "card", "care", "careful")
        trie.wordsWithPrefix("cat") shouldContainExactlyInAnyOrder listOf("cat")
        trie.wordsWithPrefix("dog") shouldBe emptyList()
    }

    "should delete words" {
        val trie = Trie()
        trie.insert("hello")
        trie.insert("help")

        trie.delete("hello")

        trie.search("hello") shouldBe false
        trie.search("help") shouldBe true  // Unaffected
        trie.startsWith("hel") shouldBe true  // Prefix still valid
    }

    "should count words with prefix" {
        val trie = Trie()
        listOf("app", "apple", "application", "apply", "apt").forEach { trie.insert(it) }

        trie.countWordsWithPrefix("app") shouldBe 4  // app, apple, application, apply
        trie.countWordsWithPrefix("apt") shouldBe 1
        trie.countWordsWithPrefix("xyz") shouldBe 0
    }

    "autocomplete should return top suggestions" {
        val trie = Trie()
        // Insert words with frequency/score
        trie.insertWithScore("apple", 100)
        trie.insertWithScore("application", 50)
        trie.insertWithScore("apply", 75)
        trie.insertWithScore("apt", 25)

        // Should return top 2 by score
        trie.autocomplete("app", limit = 2) shouldBe listOf("apple", "apply")
    }
})

class Trie {
    private class Node {
        val children = mutableMapOf<Char, Node>()
        var isEndOfWord = false
        var score = 0  // For autocomplete ranking
    }

    private val root = Node()

    fun insert(word: String) = insertWithScore(word, 0)

    fun insertWithScore(word: String, score: Int) {
        // TODO: Same as insert, but also set score at end node
        val endNode = word.fold(root) { node, c ->
            node.children.getOrPut(c) { Node() }
        }
        endNode.isEndOfWord = true
        endNode.score = score
    }

    fun search(word: String): Boolean = walkToEnd(word)?.isEndOfWord == true


    fun startsWith(prefix: String): Boolean = walkToEnd(prefix) != null


    private fun walkToEnd(word: String): Node? = word.fold(root as Node?) { currNode, char ->
        currNode?.children[char]
    }

    fun delete(word: String): Boolean =
    // TODO: Find the word, unmark isEndOfWord
        // (Simple version - doesn't clean up unused nodes)
        walkToEnd(word)?.let { it.isEndOfWord = false } != null

    fun wordsWithPrefix(prefix: String): List<String> = wordsWithPrefixAndNode(prefix).map { it.second }


    private fun wordsWithPrefixAndNode(prefix: String): List<Pair<Node, String>> {
        fun recCollectPrefixNodes(node: Node, path: String): List<Pair<Node, String>> =
            (if (node.isEndOfWord) listOf(node to path) else listOf()) + node.children.flatMap { (nextChar, nextNode) ->
                recCollectPrefixNodes(nextNode, path + nextChar)
            }
        return walkToEnd(prefix)?.let { recCollectPrefixNodes(it, prefix) } ?: listOf()
    }

    fun countWordsWithPrefix(prefix: String): Int = wordsWithPrefix(prefix).size


    fun autocomplete(prefix: String, limit: Int): List<String> =
    // TODO:
    // 1. Find all words with prefix
    // 2. Sort by score descending
        // 3. Return top 'limit'
        wordsWithPrefixAndNode(prefix).sortedByDescending { it.first.score }.take(limit).map { it.second }


    // Helper: navigate to node at end of path (or null if not exists)
    private fun findNode(path: String): Node? {
        var current = root
        for (c in path) {
            current = current.children[c] ?: return null
        }
        return current
    }

    // Helper: DFS to collect words from a node
    private fun collectWords(node: Node, prefix: String, result: MutableList<Pair<String, Int>>) {
        if (node.isEndOfWord) {
            result.add(prefix to node.score)
        }
        for ((char, child) in node.children) {
            collectWords(child, prefix + char, result)
        }
    }
}
