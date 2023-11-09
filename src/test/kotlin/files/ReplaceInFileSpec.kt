package files

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldNotBeTrue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.mpp.start
import okio.Buffer
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem

class ReplaceInFileSpec : BehaviorSpec({

    Given("originalFile") {
        val FFS = FakeFileSystem()
        val FFSHOME = "/Users/testuser".toPath()
        val tPath = FFSHOME / "testing"
        val originalBase = "original"
        val postfix = ".txt"
        val replacedPostfix = ".replaced"
        val original = "$originalBase$postfix".toPath()
        FFS.createDirectories(tPath)
        val content = """
            |
            |second line
            |orig 0
            |third line
            |# START REPLACE 1
            |orig 1
            |orig 2
            |orig 3
            |# END REPLACE 1
            |one after
            |orig 8
            |orig 99 and 9
            |three after with ir in it
            |# START REPLACE 2
            |orig 9
            |orig 8
            |orig 7
            |# END REPLACE 2
            |second to last with ir in it
            |orig 8
            |last line
            |
        """.trimMargin()
        FFS.write(tPath / original) { writeUtf8(content) }
        When("replacing without regions") {
            // kt replaceInFile --ignore--nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFile(FFS)
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val result = cmd.test(listOf(
                """--ignore--nonexisting""",
                //"""--inline""",
                """--backup""",
                """--region-start""", """^# START REPLACE""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                //"""--omit-region-start-lines""",
                //"""--omit-region-end-lines""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ))

            Then("replacements in any line") {
                FFS.checkNoOpenFiles()
                FFS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FFS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FFS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
                val outBuffer = Buffer()
                FFS.source(modifiedFileToCheck).use { fileSource ->
                    fileSource.buffer().use { bufferedFileSource ->
                        bufferedFileSource.readAll(outBuffer)
                    }
                }
                println("console out===:\n${result.output}\n===")
                result.statusCode shouldBe 0
                val expected = """
                    |
                    |second line
                    |orig 0
                    |third line
                    |# START REPLACE 1
                    |CHANGED X
                    |CHANGED X
                    |CHANGED X
                    |# END REPLACE 1
                    |one after
                    |orig 8
                    |orig 99 and 9
                    |three after with ir in it
                    |# START REPLACE 2
                    |CHANGED X
                    |CHANGED X
                    |CHANGED X
                    |# END REPLACE 2
                    |second to last with ir in it
                    |orig 8
                    |last line
                    |
                """.trimMargin()

                val modified: String = outBuffer.readUtf8()
                //println("content===:\n$content\n===")
                //println("modifid===:\n$modified\n===")
                modified shouldBeEqual  expected
            }
        }

    }
})
