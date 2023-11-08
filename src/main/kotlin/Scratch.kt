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
