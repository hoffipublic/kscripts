import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DoWithUsersSpec : BehaviorSpec({
    fun printCmdConsoleOut(result: CliktCommandTestResult) {
        println("START console out===:\n${result.output}\n===END console out")
    }
    Given("multiple users to DoWithUsers") {
        When("correct multiple users to DoWithUsers") {
            val theCliktArgs = listOf(
                "user",
                "--login", "hoffi",
                "--name", "Dirk",
                "--age", "52",

                "user",
                "--login", "superuser",
                "--name", "jesus",

                "user",
                "--login", "unnamed",

                "doWithUsers",

                "~/tmp/original.txt", "~/tmp/nonex.txt"
            )
            val doWithUsersCliktCmd = DoWithUsers()
            val mainClicktCmd = MainApp().subcommands(UserOptGroup(), doWithUsersCliktCmd)
            val result: CliktCommandTestResult = mainClicktCmd.test(theCliktArgs) // execute

            Then("I can gather the clikt opt subgroups of options for each") {
                printCmdConsoleOut(result)
                result.statusCode shouldBe 0
            }
        }
        When("wrong multiple users to DoWithUsers") {
            val theCliktArgs = listOf(
                "doWithUsers",

                "user",
                "--login", "hoffi",
                "--name", "Dirk",
                "--age", "52",

                "user",
                "--login", "superuser",
                "--name", "jesus",

                "user",
                "--login", "unnamed",

                "~/tmp/original.txt", "~/tmp/nonex.txt"
            )
            val doWithUsersCliktCmd = DoWithUsers()
            val mainClicktCmd = MainApp().subcommands(UserOptGroup(), doWithUsersCliktCmd)
            val result: CliktCommandTestResult = mainClicktCmd.test(theCliktArgs) // execute

            Then("there are no subgroups of options for it") {
                printCmdConsoleOut(result)
                doWithUsersCliktCmd.userOptGroups.size shouldBe 0
                result.statusCode shouldBe 1
            }
        }
    }
})
