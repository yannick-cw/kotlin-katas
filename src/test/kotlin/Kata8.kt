import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DslBasicsSpec : StringSpec({

    "should build simple HTML" {
        val html = html {
            head {
                title("My Page")
            }
            body {
                h1("Welcome")
                p("This is a paragraph")
            }
        }

        html shouldBe """
            <html>
            <head>
            <title>My Page</title>
            </head>
            <body>
            <h1>Welcome</h1>
            <p>This is a paragraph</p>
            </body>
            </html>
        """.trimIndent()
    }

    "should support nested divs" {
        val html = html {
            body {
                div {
                    p("Inside div")
                }
            }
        }

        html shouldBe """
            <html>
            <body>
            <div>
            <p>Inside div</p>
            </div>
            </body>
            </html>
        """.trimIndent()
    }
})

// TODO: Implement the HTML DSL

class HtmlBuilder {
    private val content = StringBuilder()

    fun head(block: HeadBuilder.() -> Unit) {
        val b = HeadBuilder()
        b.block()
        content.append(b.build())
    }

    fun body(block: BodyBuilder.() -> Unit) {
        val b = BodyBuilder()
        b.block()
        content.append(b.build())
    }

    fun build(): String = "<html>\n$content\n</html>"
}

class HeadBuilder {
    private val content = StringBuilder()

    fun title(text: String) {
        content.append("<title>$text</title>")
    }

    fun build(): String = "<head>\n$content\n</head>\n"
}

class BodyBuilder {
    private val content = StringBuilder()

    fun h1(text: String) {
        content.append("<h1>$text</h1>\n")
    }

    fun p(text: String) {
        content.append("<p>$text</p>\n")
    }

    fun div(block: BodyBuilder.() -> Unit) {
        content.append("<div>\n")
        this.block()
        content.append("</div>\n")
    }

    fun build(): String = "<body>\n$content</body>"
}

fun html(block: HtmlBuilder.() -> Unit): String {
    val b = HtmlBuilder()
    b.block()
    return b.build()
}
