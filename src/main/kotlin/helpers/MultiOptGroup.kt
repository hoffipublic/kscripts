//// the following kscript annotation directives are only needed, if you want to call this via kscript without using Main.kt
//@file:EntryPoint("MultiOptGroupKt")       // ending in <filename>Kt if main is global (not in class companion)
////@file:EntryPoint("files.MultiOptGroup") // ending in <classname> (without Kt) if main is static in class companion
//@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
//@file:DependsOn("com.squareup.okio:okio:3.6.0")
//@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

package files

//import DependsOn
//import EntryPoint
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int

//// this main() only needed, if you want to call this via kscript without using Main.kt
//fun main(args: Array<String>) = MultiOptGroup(args).main(args) // Main.kt and clikt subcommands cannot both have a fun main()

class MultiOptGroup() : CliktCommand(name = "multigroup") {
    class UserOptions : OptionGroup() {
        val name by option().required()
        val age by option().int()
        val opt by option().int()
    }
    val userOptions by UserOptions().cooccurring()
    val unrelatedOption by option("--unrelatedOption").flag()
    val regions: Map<String, String> by option("--region").associate()
    val args: Set<String> by argument().multiple().unique().help("files to do replacement(s) in")

    override fun run() {
        //UserCmd().subcommands(UserCmd()).main(args)
        echo("MultiOptGroup run:")
        echo("  unrelatedOption: '${unrelatedOption}'")
        if (regions.isEmpty()) echo("no regions!")
        regions.forEach { echo("${it.key}->${it.value}") }
        echo("  files: ${args.joinToString("', '", "'", "'")}")
    }
}
