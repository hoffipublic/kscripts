// the following kscript annotation directives are only needed, if you want to call this via kscript without using Main.kt
//@file:EntryPoint("UserOptGroupKt")       // ending in <filename>Kt if main is global (not in class companion)
@file:EntryPoint("UserOptGroupEntryPoint") // ending in <classname> (without Kt) if main is static in class companion
@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
@file:DependsOn("com.squareup.okio:okio:3.6.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

@file:Import("helpers_ScriptHelpers.kt")
@file:Import("multiopt_Ctx.kt")

//import DependsOn
//import EntryPoint
//import Import
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int

/** companion main function in separate class as project might have multiple main classes and anything extending CliktCommand cannot have it */
class UserOptGroupEntryPoint {
    companion object { @JvmStatic fun main(args: Array<out String>) {
        printlnSTDERR(UserOptGroupEntryPoint::class.simpleName!!)
        val mainCliktCommand = UserOptGroup()//.subcommands(UserOptGroup())
        mainCliktCommand.main(args)
    }}
}

/**
 * "fake" CliktCommand to set the given OptionGroup into parent CliktCommand's context
 * BEWARE this subcommand has to come FIRST on the terminal commandline BEFORE the "other" clikt subcommand that should operate on them!!!
 * WRONG!!: kscript xyz --xyzOpt   user --login hoffi user --login admin arg1 arg2
 * CORRECT: kscript user --login hoffi user --login admin   xyz --xyzOpt arg1 arg2
 */
class UserOptGroup() : CliktCommand(name = "user") {
    override fun toString(): String = "User('$login', ${name.singleQuote()}, $age, $opt)"
    val login by option().required()
    val name by option()
    val age by option().int()
    val opt by option().int()

    val unrelatedOption by option("--unrelatedOption").flag()

    override fun run() {
        getOrSetInRootContextObj(AContextValue.Users()).userOptGroups[login] = this
        // debug output
        echo("UserOptGroup run():")
        echo("  unrelatedOption: '${unrelatedOption}'")
        echo("  $this")
    }
}
