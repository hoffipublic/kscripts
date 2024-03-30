@file:EntryPoint("MainKt") // ending in Kt if main is global (not in class companion
@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
@file:DependsOn("com.squareup.okio:okio:3.6.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

// "faked" package structure via '_' as file:Import replaces the line with the content of that file (including any package declaration it might have)
@file:Import("ktscriptinternals/completions.kt")
@file:Import("helpers_ScriptHelpers.kt")
@file:Import("multiopt_Ctx.kt")
@file:Import("files_ReplaceInFiles.kt")
@file:Import("multiopt_UserOptGroup.kt")
@file:Import("tutorial_DoWithUsers.kt")
@file:Import("tutorial_PipedInput.kt")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import ktscriptinternals.completions
import okio.Path.Companion.toPath
import kotlin.system.exitProcess

/** see helper bash functions for calling/executing in `README.md` */
fun main(argv: Array<out String>) = MainApp(argv).subcommands(completions(), ReplaceInFiles(), Client(), Server(), UserOptGroup(), DoWithUsers(), PipedInput()).main(argv)

/**
 * MainApp is a convenience to have a single entry point to all of the kscripts in this project
 * As a) all kscripts should be .kt files and not .kts script files (for best development experience in e.g. Intellij)
 * and b) all the subcommands should be as standalone portable as possible (outside of this project with as few adjustments as possible)
 * any subcommand should store context in the top-level root context as a list of sealed classes defined in helpers_ScriptHelpers.kt
 * For calling MainApp (or its subcommands as standalone) with kscript see [github hoffipublic/kscripts](https://github.com/hoffipublic/kscripts)
 */
class MainApp(val argv: Array<out String> = emptyArray()) : CliktCommand(allowMultipleSubcommands = true) {

    override fun run() {
        // to be used in bash_kotlin for bash completions for kt|ktraw ktscript/ktscriptraww functions with: complete -W "${subcommands[*]}" <function>
        if ( argv.isNotEmpty() && argv[0] == "completions" ) {
            if ( argv.size == 1 ) {
                println(super.registeredSubcommandNames().joinToString(" "))
            } else {
                val allThings = FS.list((System.getenv("KTSCRIPTSBASE") ?: ".").toPath())
                println(allThings.filter { it.name.endsWith(".kt") }.joinToString(" ") { it.name })
            }
            exitProcess(0)
        }
    }
}

class Client : CliktCommand() {

    override fun run() {
        echo("cmd: client")
        echo()
    }

}

class Server : CliktCommand() {

    override fun run() {
        echo("cmd: server")
        echo()
    }
}
