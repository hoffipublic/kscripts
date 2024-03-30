package ktscriptinternals

import com.github.ajalt.clikt.core.CliktCommand

/** dummy cliktCommand for writing all subcommands to STDOUT for bash completions in Main.kt */
class completions() : CliktCommand(name = "completions") {
    override fun run() {
        // noop
    }

}
