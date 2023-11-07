
import kotlinx.datetime.*
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*


fun main(args: Array<String>) {
    main_FileTreeVisit(args)
}

@OptIn(ExperimentalPathApi::class)
fun main_FileTreeVisit(args: Array<String>) {
    val projectPath = object {}.javaClass.getResourceAsStream("ProjectPath.txt")?.bufferedReader()?.readLine()
    val path = Paths.get(projectPath!!, "src/main")

    println("traverse files in '$path'")
    visitFiles(path, skipHiddendFiles = true) { file, attributes ->
        println("file: $file")
        FileVisitResult.CONTINUE
    }

    println()
    println("walk '$path'")
    val allPaths: Sequence<Path> = path.walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.FOLLOW_LINKS).filter {
        val relPath = it.relativeTo(path)
        if ( ! (relPath.toString().startsWith(".") || relPath.name.startsWith('.') || relPath.toString() == "") ) {
            if (it.isDirectory()) {
                println("dir:  '$relPath' -> filtered")
                false
            } else {
                println("file: $relPath")
                true
            }
        } else {
            println("skipped: '$relPath'")
            false
        }
    }

    println(allPaths.joinToString(",\n- ", "\nwalk filter result:\n- "))
}

@OptIn(ExperimentalPathApi::class)
fun visitFiles(dir: Path, skipHiddenDirs: Boolean = true, skipHiddendFiles: Boolean = false, function: (file: Path, attributes: BasicFileAttributes) -> FileVisitResult) {
    dir.visitFileTree {
        onPreVisitDirectory { directory, attributes ->
            if (skipHiddenDirs && directory.name.startsWith('.')) { FileVisitResult.SKIP_SUBTREE} else { FileVisitResult.CONTINUE }
        }

        onVisitFile { file, attributes ->
            if (skipHiddendFiles && file.name.startsWith('.')) { FileVisitResult.CONTINUE }
            else { function(file, attributes)}
        }
    }
}
