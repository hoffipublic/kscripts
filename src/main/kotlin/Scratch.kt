import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import okio.FileSystem
import okio.Path.Companion.toPath

//fun main() {
//    traverseDir("./src/main")
//}

private fun traverseDir(dir: String) {
    val fs: FileSystem = FileSystem.SYSTEM
    val fileSeq = fs.listRecursively(dir.toPath(), false)
    for (fOrD in fileSeq) {
        if (fs.metadataOrNull(fOrD)?.isDirectory != false) {
            println("${fOrD}/")
        } else if (fs.metadataOrNull(fOrD)?.symlinkTarget != null) {
            println("${fOrD}*")
        } else {
            println(fOrD)
        }
    }
}

//open class ASuperClass {
//    //companion object { @JvmStatic fun main(args: Array<out String>) = ASuperClass().superFun(args) }
//    fun main(args: Array<out String>) = ASuperClass().superFun(args)
//    fun superFun(args: Array<out String>) {
//        println("ASuperClass: ${args.joinToString()}")
//    }
//}
//class SuperCmd : CliktCommand() {
//    companion object { @JvmStatic fun main(args: Array<out String>) = SuperCmd().subcommands(SubCmd()).main(args) }
//    override fun run() {
//        println("cmd: super-cmd")
//    }
//}
//class SubCmd : CliktCommand() {
//    override fun run() {
//        echo("cmd: sub-cmd")
//        echo()
//    }
//}
