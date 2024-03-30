
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class MainSpec : BehaviorSpec({
    fun printCmdConsoleOut(result: CliktCommandTestResult) {
        println("START console out===:\n${result.output}\n===END console out")
    }
    Given("running MainApp sub UserOptGroup") {
        When("running MainApp sub UserOptGroup") {
            val theCliktArgs = listOf(
                "user",
                "--login", "hoffi",
                "--name", "Dirk",
                "--age", "52",

                "user",
                "--login", "superuser",
                "--name", "jesus",

                "user",
                "--login", "unnamed"
            )
            val mainClicktCmd = MainApp().subcommands(UserOptGroup())
            val result: CliktCommandTestResult = mainClicktCmd.test(theCliktArgs) // execute

            Then("I can gather the clikt opt sugroups of options for each") {
                printCmdConsoleOut(result)
                val users: AContextValue.Users = mainClicktCmd.getOrSetInRootContextObj(AContextValue.Users())
                val userOptGroups: MutableMap<String, UserOptGroup> = users.userOptGroups
                userOptGroups.size shouldBe 3
                result.statusCode shouldBe 0
            }
        }
    }
})
