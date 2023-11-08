@file:EntryPoint("MainKt") // ending in Kt if main is global (not in class companion
@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
@file:DependsOn("com.squareup.okio:okio:3.6.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

@file:Import("files/ReplaceInFile.kt")


import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import files.ReplaceInFile

/**
 * kt () { kscript "$HOME/gitRepos/kscripts/src/main/kotlin/Main.kt" "$@" 2> >(grep -v '^\[kscript\]') ; }
 * ktraw () { kscript "$HOME/gitRepos/kscripts/src/main/kotlin/Main.kt" "$@" ; }
 */
fun main(args: Array<String>) = App().subcommands(ReplaceInFile(), Client(), Server()).main(args)

class App() : CliktCommand(allowMultipleSubcommands = true) {
    override fun run() {
        echo("Hello, from App!")
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
