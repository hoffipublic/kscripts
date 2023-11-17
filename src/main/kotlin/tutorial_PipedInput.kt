@file:EntryPoint("PipedInputEntryPoint") // ending in <classname> (without Kt) if main is static in class companion
@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
@file:DependsOn("com.squareup.okio:okio:3.6.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

@file:Import("helpers_ScriptHelpers.kt")

import com.github.ajalt.clikt.core.*

/** companion main function in separate class as project might have multiple main classes and anything extending CliktCommand cannot have it */
class PipedInputEntryPoint {
    /** cmdline e.g.: cat README.md | kscript <thisFilename.kt> -- */
    companion object { @JvmStatic fun main(args: Array<out String>) {
        val mainCliktCommand = PipedInput()
        mainCliktCommand.main(args)
    }}
}

class PipedInput : CliktCommand() {
    override fun run() {
        var pipedInput = readlnOrNull()
        if (pipedInput == null) {
            echo("no piped input")
        } else {
            while (pipedInput != null) {
                echo(pipedInput)
                pipedInput = readlnOrNull()
            }
        }
    }
}
