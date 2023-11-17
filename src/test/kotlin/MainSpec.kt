
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.CliktCommandTestResult
import io.kotest.core.spec.style.BehaviorSpec

class MainSpec : BehaviorSpec({
    fun printCmdConsoleOut(result: CliktCommandTestResult) {
        println("START console out===:\n${result.output}\n===END console out")
    }
    Given("running MainApp sub UserOptGroup") {
        When("running MainApp sub UserOptGroup") {
            val theCliktArgs = listOf(
                """multigroup""",

                """--region=1-start='^# START REPLACE Region 1'""",
                """--region=1-end='^# END REPLACE Region 1'""",
                """--region=1-replace='\d'__with__"X"""",   """--region=1-replace='ri'__with__'ir'""",

                """--region=2-start='^# START REPLACE Region 2'""",
                """--region=2-end='^# END REPLACE Region 2'""",
                """--region=2-replace='^(\w+) ([A-Z]+)(.*)$'__with__'CHANGED $2'""",   """--region=2-replace='regex'__with__'replaced compol'""",

                "~/tmp/original.txt", "~/tmp/nonex.txt"
            )
            MainApp().subcommands(UserOptGroup()).parse(theCliktArgs)

            Then("I can gather the clikt opt sugroups of options for each") {
                //printCmdConsoleOut(result)
                //result.statusCode shouldBe 0
            }
        }
    }
})
