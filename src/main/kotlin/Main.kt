@file:EntryPoint("MainKt") // ending in Kt if main is global (not in class companion
@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
@file:DependsOn("com.squareup.okio:okio:3.6.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

@file:Import("files/ReplaceInFiles.kt")
@file:Import("files/MultiOptGroups.kt")


import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import files.MultiOptGroups
import files.ReplaceInFiles
import okio.FileSystem

var FS: FileSystem = FileSystem.SYSTEM

/** see helper bash functions for calling/executing in `README.md` */
fun main(args: Array<String>) = MainApp().subcommands(ReplaceInFiles(FS), Client(), Server(), MultiOptGroups()).main(args)

class MainApp() : CliktCommand(allowMultipleSubcommands = true) {
    override fun run() {
        echo("Hello, from MainApp!")
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
