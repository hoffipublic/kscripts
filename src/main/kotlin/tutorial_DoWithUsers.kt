// the following kscript annotation directives are only needed, if you want to call this via kscript without using Main.kt
//@file:EntryPoint("DoWithUsersEntryPointKt") // ending in <filename>Kt if main is global (not in class companion)
@file:EntryPoint("DoWithUsersEntryPoint") // ending in <classname> (without Kt) if main is static in class companion
@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
@file:DependsOn("com.squareup.okio:okio:3.6.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

@file:Import("helpers_ScriptHelpers.kt")
@file:Import("multiopt_Ctx.kt")
@file:Import("multiopt_UserOptGroup.kt")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique

/** companion main function in separate class as project might have multiple main classes and anything extending CliktCommand cannot have it */
class DoWithUsersEntryPoint {
    /** make sure the multiopt subcommands come first on cmdline and the action subcommand (here 'doWithUsers' after them)
     * cmdline e.g.: kscript <thisFilename.kt> user --login hoffi user --login admin --age 42 doWithUsers fileArg1 fileArg2
     */
    companion object { @JvmStatic fun main(args: Array<out String>) {
        printlnSTDERR(DoWithUsersEntryPoint::class.simpleName!!)
        val mainCliktCommand = DoWithUsersEntryPointApp().subcommands(DoWithUsers(), UserOptGroup())
        mainCliktCommand.main(args)
    }}
    class DoWithUsersEntryPointApp : CliktCommand("app", allowMultipleSubcommands = true) { override fun run() { } }
}

class DoWithUsers() : CliktCommand(name = "doWithUsers") {
    val userOptGroups: MutableMap<String, UserOptGroup> = mutableMapOf()
    val args: Set<String> by argument().multiple().unique().help("files to do replacement(s) in")

    override fun run() {
        userOptGroups.putAll(getOrSetInRootContextObj(AContextValue.Users()).userOptGroups)
        echo("DoWithUsers: ${userOptGroups.size} users: ${userOptGroups.entries.joinToStringSingleQuoted()}")
        echo("DoWithUsers: args: ${args.joinToStringSingleQuoted()}")
        if (userOptGroups.isEmpty()) {
            printlnSTDERR("no users given (make sure to have 'user --login' BEFORE 'doWithUsers ...' on the cmd line!")
            throw ProgramResult(1)
        }
    }
}
