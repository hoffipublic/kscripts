import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class ReplaceInFilesSpec : BehaviorSpec({

    fun printCmdConsoleOut(result: CliktCommandTestResult) { /*println("START console out===:\n${result.output}\n===END console out")*/ }
    fun MutableList<String>.alterArgs(): MutableList<String> = this //{ return  this.apply { add(0, "--silent") } }

    val FFSHOME = "/Users/testuser".toPath()
    val tPath = FFSHOME / "testing"
    val originalBase = "original"
    val postfix = ".txt"
    val replacedPostfix = ".replaced"
    val original = "$originalBase$postfix".toPath()
    var content = ""
    val bef = {
        newFakeFileSystem()
        FS.createDirectories(tPath)
        val befFunContent = """
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
        content = befFunContent
        FS.write(tPath / original) { writeUtf8(befFunContent) }
    }
    beforeContainer {
        println("Spec: '${it.name.testName}'")
        bef()
    }
    afterContainer {
        (FS as FakeFileSystem).checkNoOpenFiles()
    }

    Given("originalFile") {
        When("noninline --replace with regions and regexes incl sentinels") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
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
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace with regions and regexes incl sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
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
                val modified = readFullyToString(modifiedFileToCheck)
                //println("content===:'\n$content'\n===")
                //println("modifid===:'\n$modified'\n===")
                modified shouldBe expected
            }
        }
        When("noninline --replace with regions and regexes omit sentinels") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
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
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace with regions and regexes omit sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
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
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBeEqual  expected
            }
        }
        When("noninline --replace omit-before-first-and-after-last-region with regions and regexes incl sentinels") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
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
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace omit-before-first-and-after-last-region with regions and regexes incl sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
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
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }
        When("noninline --replace omit-before-first-and-after-last-region with regions and regexes omit sentinels") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
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
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace omit-before-first-and-after-last-region with regions and regexes omit sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
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
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBeEqual  expected
            }
        }
        When("noninline --replace with regions and regexes only start sentinels") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
                """--backup""",
                """--lenient-regions""",
                """--region-start""", """^# START REPLACE""",
                """--all""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace with regions and regexes only start sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
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
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }
        When("noninline --replace with regions and regexes only end sentinels") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
                """--backup""",
                """--lenient-regions""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace with regions and regexes only end sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
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
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }

        // --replace-region

        When("noninline --replace-region with both sentinels") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
                """--backup""",
                """--region-start""", """^# START REPLACE""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace-region""","something\nsecond line\nthird line",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace-region with both sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
                result.statusCode shouldBe 0
                val expected = """
                    |
                    |second line
                    |orig 0
                    |third line
                    |# START REPLACE 1
                    |something
                    |second line
                    |third line
                    |# END REPLACE 1
                    |one after
                    |orig 8
                    |orig 99 and 9
                    |three after with ir in it
                    |# START REPLACE 2
                    |something
                    |second line
                    |third line
                    |# END REPLACE 2
                    |second to last with ir in it
                    |orig 8
                    |last line
                    |
                """.trimMargin()
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }
        When("noninline --replace-region with only region-start sentinel") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
                """--backup""",
                """--lenient-regions""",
                """--region-start""", """^# START REPLACE""",
                """--all""",
                """--replace-region""","something\nsecond line\nthird line",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace-region with only region-start sentinel") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
                result.statusCode shouldBe 0
                val expected = """
                    |
                    |second line
                    |orig 0
                    |third line
                    |# START REPLACE 1
                    |something
                    |second line
                    |third line
                    |
                """.trimMargin()
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }
        When("noninline --replace-region with only region-end sentinel") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
                """--backup""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace-region""","something\nsecond line\nthird line",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace-region with regions-end sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
                result.statusCode shouldBe 0
                val expected = """
                    |something
                    |second line
                    |third line
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
                val modified = readFullyToString(modifiedFileToCheck)
                ////println("content===:\n$content\n===")
                ////println("modifid===:\n$modified\n===")
                modified shouldBe expected
            }
        }
        When("noninline --replace-region omit-before-first-and-after-last-region with both sentinels") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
                """--backup""",
                """--region-start""", """^# START REPLACE""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace-region""","something\nsecond line\nthird line",
                """--omit-region-start-lines""",
                """--omit-region-end-lines""",
                """--omit-before-first-region""",
                """--omit-after-last-region""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace-region omit-before-first-and-after-last-region with both sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
                result.statusCode shouldBe 0
                val expected = """
                    |something
                    |second line
                    |third line
                    |one after
                    |orig 8
                    |orig 99 and 9
                    |three after with ir in it
                    |something
                    |second line
                    |third line
                    |
                """.trimMargin()
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }
        When("noninline --replace-region omit-before-first-and-after-last-region with only region-start sentinel") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
                """--backup""",
                """--lenient-regions""",
                """--region-start""", """^# START REPLACE""",
                """--all""",
                """--replace-region""","something\nsecond line\nthird line",
                """--omit-region-start-lines""",
                """--omit-region-end-lines""",
                """--omit-before-first-region""",
                """--omit-after-last-region""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace-region omit-before-first-and-after-last-region with only region-start sentinel") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
                result.statusCode shouldBe 0
                val expected = """
                    |something
                    |second line
                    |third line
                    |
                """.trimMargin()
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }
        When("noninline --replace-region omit-before-first-and-after-last-region with only region-end sentinel") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
                """--backup""",
                """--region-end""", """^# END REPLACE""",
                """--all""",
                """--replace-region""","something\nsecond line\nthird line",
                """--omit-region-start-lines""",
                """--omit-region-end-lines""",
                """--omit-before-first-region""",
                """--omit-after-last-region""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace-region omit-before-first-and-after-last-region with regions-end sentinels") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
                result.statusCode shouldBe 0
                val expected = """
                    |something
                    |second line
                    |third line
                    |
                """.trimMargin()
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }

        When("noninline --replace without regions") {
            // kt replaceInFiles --ignore-nonexisting --backup --region-start '^# START REPLACE' --region-end '^# END REPLACE' --all --replace '\d' "X" --replace 'ri' "ir" --replace 'ir' 'xx' --replace '^(\w+) ([A-Z]+)(.*)$' 'CHANGED $2' ~/tmp/original.txt ~/tmp/nonex.txt
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                """--ignore-nonexisting""",
                """--backup""",
                """--replace""","""\d""","""X""",
                """--replace""","""ri""","""ir""",
                """--replace""","""ir""","""xx""",
                """--replace""","""^(\w+) ([A-Z]+)(.*)$""","""CHANGED $2""",
                """--verbose""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline --replace without regions") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
                result.statusCode shouldBe 0
                val expected = """
                    |
                    |second line
                    |orig X
                    |thxxd line
                    |# START REPLACE X
                    |orig X
                    |orig X
                    |orig X
                    |# END REPLACE X
                    |one after
                    |orig X
                    |orig XX and X
                    |three after with xx in it
                    |# START REPLACE X
                    |orig X
                    |orig X
                    |orig X
                    |# END REPLACE X
                    |second to last with xx in it
                    |orig X
                    |last line
                    |
                """.trimMargin()
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }
        When("noninline custom") {
            val cmd = ReplaceInFiles()
            val modifiedFileToCheck = ((tPath / original).toString() + replacedPostfix).toPath()
            val args = mutableListOf(
                "--verbose",
                "--ignore-nonexisting",
                "--backup",
                "--region-start", """^# START REPLACE""",
                "--replace", """\d""", "X",
                "--replace", "ri", "ir",
                "--replace", "ir", "xx",
                "--replace", """^(\w+) ([A-Z]+)(.*)$""", """CHANGED $2""",
                (tPath / original).toString(),
                """~/tmp/nonex.txt""",
            ).alterArgs()
            println(args.joinToString("' '", "'", "'"))
            val result: CliktCommandTestResult = cmd.test(args)
            printCmdConsoleOut(result)

            Then("noninline custom") {
                (FS as FakeFileSystem).checkNoOpenFiles()
                FS.exists(tPath / "$originalBase$postfix.replaced").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix.backup").shouldBeTrue()
                FS.exists(tPath / "$originalBase$postfix").shouldBeTrue()
                result.statusCode shouldBe 0
                val expected = """
                    |
                    |second line
                    |orig 0
                    |third line
                    |# START REPLACE 1
                    |orig X
                    |orig X
                    |orig X
                    |# END REPLACE X
                    |one after
                    |orig X
                    |orig XX and X
                    |three after with xx in it
                    |# START REPLACE X
                    |orig X
                    |orig X
                    |orig X
                    |# END REPLACE X
                    |second to last with xx in it
                    |orig X
                    |last line
                    |
                """.trimMargin()
                val modified = readFullyToString(modifiedFileToCheck)
                modified shouldBe expected
            }
        }
    }
})
