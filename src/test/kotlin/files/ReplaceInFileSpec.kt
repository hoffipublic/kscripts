package files

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldNotBeTrue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem

class ReplaceInFileSpec : BehaviorSpec({
    var FFS = FakeFileSystem()
    val FFSHOME = "/Users/testuser".toPath()
    val tPath = FFSHOME / "testing"
    val originalBase = "original"
    val postfix = ".txt"
    val replacedPostfix = ".replaced"
    val original = "$originalBase$postfix".toPath()
    val bef = {
        FFS = FakeFileSystem()
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
    }
    beforeContainer {
        println("Spec: '${it.name.testName}'")
        bef()
    }
    afterContainer {
        FFS.checkNoOpenFiles()
    }

    Given("originalFile") {
        When("noninline replacing with regions and regexes incl sentinels") {
            // kt replaceInFile --ignore--nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFile(FFS)
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val result = cmd.test(listOf(
                """--ignore--nonexisting""",
                """--backup""",
                """--region-start""", """^# START REPLACE""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ))

            Then("noninline replacing with regions and regexes incl sentinels") {
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
        When("noninline replacing with regions and regexes omit sentinels") {
            // kt replaceInFile --ignore--nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFile(FFS)
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val result = cmd.test(listOf(
                """--ignore--nonexisting""",
                """--backup""",
                """--region-start""", """^# START REPLACE""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                """--omit-region-start-lines""",
                """--omit-region-end-lines""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ))

            Then("noninline replacing with regions and regexes omit sentinels") {
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
                    |CHANGED X
                    |CHANGED X
                    |CHANGED X
                    |one after
                    |orig 8
                    |orig 99 and 9
                    |three after with ir in it
                    |CHANGED X
                    |CHANGED X
                    |CHANGED X
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
        When("noninline omit-before-first-and-after-last-region replacing with regions and regexes incl sentinels") {
            // kt replaceInFile --ignore--nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFile(FFS)
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val result = cmd.test(listOf(
                """--ignore--nonexisting""",
                """--backup""",
                """--region-start""", """^# START REPLACE""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                """--omit-before-first-region""",
                """--omit-after-last-region""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ))

            Then("noninline replacing with regions and regexes incl sentinels") {
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
                    |
                """.trimMargin()

                val modified: String = outBuffer.readUtf8()
                //println("content===:\n$content\n===")
                //println("modifid===:\n$modified\n===")
                modified shouldBeEqual expected
            }
        }
        When("noninline omit-before-first-and-after-last-region replacing with regions and regexes omit sentinels") {
            // kt replaceInFile --ignore--nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFile(FFS)
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val result = cmd.test(listOf(
                """--ignore--nonexisting""",
                """--backup""",
                """--region-start""", """^# START REPLACE""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                """--omit-region-start-lines""",
                """--omit-region-end-lines""",
                """--omit-before-first-region""",
                """--omit-after-last-region""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ))

            Then("noninline replacing with regions and regexes omit sentinels") {
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
                    |CHANGED X
                    |CHANGED X
                    |CHANGED X
                    |one after
                    |orig 8
                    |orig 99 and 9
                    |three after with ir in it
                    |CHANGED X
                    |CHANGED X
                    |CHANGED X
                    |
                """.trimMargin()

                val modified: String = outBuffer.readUtf8()
                //println("content===:\n$content\n===")
                //println("modifid===:\n$modified\n===")
                modified shouldBeEqual  expected
            }
        }
        When("noninline replacing with regions and regexes only start sentinels") {
            // kt replaceInFile --ignore--nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFile(FFS)
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val result = cmd.test(listOf(
                """--ignore--nonexisting""",
                """--backup""",
                """--region-start""", """^# START REPLACE""",
                """--all""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ))

            Then("noninline replacing with regions and regexes only start sentinels") {
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
                    |# END REPLACE X
                    |one after
                    |CHANGED X
                    |CHANGED XX
                    |three after with xx in it
                    |# START REPLACE X
                    |CHANGED X
                    |CHANGED X
                    |CHANGED X
                    |# END REPLACE X
                    |second to last with xx in it
                    |CHANGED X
                    |last line
                    |
                """.trimMargin()

                val modified: String = outBuffer.readUtf8()
                //println("content===:\n$content\n===")
                //println("modifid===:\n$modified\n===")
                modified shouldBeEqual expected
            }
        }
        When("noninline replacing with regions and regexes only end sentinels") {
            // kt replaceInFile --ignore--nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFile(FFS)
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val result = cmd.test(listOf(
                """--ignore--nonexisting""",
                """--backup""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ))

            Then("noninline replacing with regions and regexes only end sentinels") {
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
                    |CHANGED X
                    |thxxd line
                    |# START REPLACE X
                    |CHANGED X
                    |CHANGED X
                    |CHANGED X
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

                val modified: String = outBuffer.readUtf8()
                //println("content===:\n$content\n===")
                //println("modifid===:\n$modified\n===")
                modified shouldBeEqual expected
            }
        }

    }
})
