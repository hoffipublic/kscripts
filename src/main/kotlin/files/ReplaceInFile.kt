@file:EntryPoint("ReplaceInFileKt")       // ending in <filename>Kt if main is global (not in class companion)
//@file:EntryPoint("files.ReplaceInFile") // ending in <classname> (without Kt) if main is static in class companion
@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
@file:DependsOn("com.squareup.okio:okio:3.6.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

package files

import DependsOn
import EntryPoint
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

//fun main(args: Array<out String>) = ReplaceInFile().main(args) // Main.kt and clikt subcommands cannot both have a fun main()

class ReplaceInFile : CliktCommand(name = "replaceInFile") {
    //companion object { @JvmStatic fun main(args: Array<out String>) = ReplaceInFile().main(args) }
    val count: Int by option().int().default(1).help("Number of greetings")
    val name: String by option().prompt("Your name").help("The person to greet")
    val args: List<Path> by argument().path(mustExist = true).multiple()
    //val dest: Path by argument().path(canBeFile = false)
    override fun run() {
        repeat(count) {
            echo("Hello $name!")
        }
        if (args.isEmpty())
            println("ReplaceInFile was called")
        else
            println("ReplaceInFile was called with ${args.joinToString("', '", "'", "'")}")
    }
}
