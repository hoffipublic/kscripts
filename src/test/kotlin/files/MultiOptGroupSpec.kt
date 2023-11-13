package files

import io.kotest.core.spec.style.BehaviorSpec
import com.github.ajalt.clikt.testing.test
import helpers.CodeLink
import io.kotest.matchers.shouldBe

@CodeLink("class under test", MultiOptGroup::class)
class MultiOptGroupsSpec : BehaviorSpec({
    Given("multiple subcommands to clikt") {
        When("multiple subcommands to clikt") {
            val theCliktArgs = listOf(
                """--region=1-start='^# START REPLACE Region 1'""",
                """--region=1-end='^# END REPLACE Region 1'""",
                """--region=1-replace='\d'__with__"X"""",   """--region=1-replace='ri'__with__'ir'""",

                """--region=2-start='^# START REPLACE Region 2'""",
                """--region=2-end='^# END REPLACE Region 2'""",
                """--region=2-replace='^(\w+) ([A-Z]+)(.*)$'__with__'CHANGED $2'""",   """--region=2-replace='regex'__with__'replaced compol'""",

                "~/tmp/original.txt", "~/tmp/nonex.txt"
            )
            val cmd = MultiOptGroup()
            val result = cmd.test(theCliktArgs)
            Then("I can gather the clikt opt sugroups of options for each") {
                println("console out===:\n${result.output}\n===")
                result.statusCode shouldBe 0
            }
        }
    }
})
