// the following kscript annotation directives are only needed, if you want to call this via kscript without using Main.kt
//@file:EntryPoint("ReplaceInFileKt")       // ending in <filename>Kt if main is global (not in class companion)
////@file:EntryPoint("files.ReplaceInFile") // ending in <classname> (without Kt) if main is static in class companion
//@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
//@file:DependsOn("com.squareup.okio:okio:3.6.0")
//@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

package files

//import DependsOn
//import EntryPoint
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique
import com.github.ajalt.clikt.parameters.options.*
import okio.*
import okio.Path.Companion.toPath
import kotlin.io.use
import kotlin.system.exitProcess

// this main() only needed, if you want to call this via kscript without using Main.kt
//fun main(args: Array<out String>) = ReplaceInFile().main(args) // Main.kt and clikt subcommands cannot both have a fun main()

class ReplaceInFile : CliktCommand(name = "replaceInFile") {
    //companion object { @JvmStatic fun main(args: Array<out String>) = ReplaceInFile().main(args) }
    val rplOpts: List<Pair<String, String>> by option("--replace", "-rpl").pair().multiple().help("pairs of regex and replacement strings.\u0085Only first regex that matches will be replaced (unless you explicitly specify --all)\u0085Replacement may have back references to regex groups via \$1, \$2, ...\u0085Mutual exclusive to either of --skip-replace and --skip-remove")
    val allReplacements: Boolean            by option("--all", "-a").flag().help("if multiple --replace pairs are give execute them all\u0085(otherwise only the first matching --replace will be executed)")
    val postfixOpt: String                  by option("--postfix", "-p").default("").help("postfix for output file(s)\u0085default: '.replaced'\u0085(mutual exclusive with --inline/--overwrite)")
    val inline: Boolean                     by option("--inline", "--overwrite").flag().help("replace file contents inline in given file(s)\u0085(mutual exclusive with --postfix)")
    val stdout: Boolean                     by option("--stdout").flag().help("do not write any files, but print resulting files to stdout (also ignores --backup)")
    val backup: Boolean                     by option("--backup").flag().help("keep a backup copy of the original file(s) with postfix '.backup'")
    val caseInsensitive: Boolean            by option("--case-insensitive", "-i").flag().help("case-insensitive regex matching")
    val ignoreNonexisting: Boolean          by option("--ignore--nonexisting").flag().help("ignore files that do not exist")
    val skipStartOpt: String                by option("--skip-start", "-ss").default("").help("leave all lines untouched from start of file (and after --skip-end)\u0085up to line matching this RE (unless --omit-skip-start)")
    val skipEndOpt: String                  by option("--skip-end", "-se").default("").help("leave all lines untouched from line matching this RE\u0085up to the end of the file (or next --skip-start) (unless --omit-skip-end)")
    val omitSkipStart: Boolean              by option("--omit-skip-start", "-oss").flag().help("do not write the lines from start of file up to the first '--skip-start' to the output file")
    val omitSkipEnd: Boolean                by option("--omit-skip-end", "-ose").flag().help("do not write the lines from after last match of '--skip-end' up to the end of file to the output file")
    val omitStartLine: Boolean              by option("--omit-skip-start-line", "-osl").flag().help("do not write the line(s) itself found by '--skip-start' to the output")
    val omitEndLine: Boolean                by option("--omit-skip-end-line", "-oel").flag().help("do not write the line(s) itself found by '--skip-end' to the output")
    val replaceBetweenSkipStartAndSkipEnd: String? by option("--skip-replace").help("replace anything between --skip-start and --skip-end with this\u0085(mutual exclusive with --replace and --skip-remove)")
    val removeBetweenSkipStartAndSkipEnd: Boolean  by option("--skip-remove").flag().help("just remove anything between --skip-start and --skip-end\u0085(mutual exclusive with --replace and --skip-replace)")
    val args: Set<String>                   by argument().multiple().unique().help("files to do replacement(s) in")
    var skipStartRE: Regex? = null
    var skipEndRE: Regex? = null
    var rplREs: MutableList<Pair<Regex, String>> = mutableListOf()
    val FS: FileSystem = FileSystem.SYSTEM
    var firstSkipStartFound = false
    var indexAfterLastSkipEnd = -1
    lateinit var postfix: String
    val warnings: MutableList<String> = mutableListOf()

    //class BoolState(var isTrue: Boolean) {
    //    override fun equals(other: Any?): Boolean = if ((other is Boolean && isTrue != other) || (other !is BoolState) ) false else isTrue == other.isTrue
    //    override fun hashCode(): Int = isTrue.hashCode()
    //}
    data class ReplaceResult(
        val origFilePath: Path,
        val lines: MutableList<String> = mutableListOf(),
        var didNotHadALineMatchingSkipStart: Boolean = false,
        val countAlteredLines: Int = 0,
        val countRemovedLines: Int = 0,
        val countAddedLines: Int = 0,
    )

    override fun run() {
        val fileReplaceResults: List<ReplaceResult> = validateArgs()

        for (rr in fileReplaceResults) {
            indexAfterLastSkipEnd = -1
            FS.source(rr.origFilePath).use { fileSource ->
                fileSource.buffer().use { bufferedFileSource ->
                    var currentLine: String? = bufferedFileSource.readUtf8Line()
                    if (currentLine == null) { warnings.add("${rr.origFilePath} did not have any content !!!") }
                    while (currentLine != null) {
                        if (skipStartRE != null) {
                            currentLine = readUpToStartMatch(currentLine, bufferedFileSource, rr)
                            // currentLine now is lineAfterSkipStartLine
                            if ( ! firstSkipStartFound && currentLine == null) {
                                rr.didNotHadALineMatchingSkipStart = true
                                warnings.add("${rr.origFilePath} did not had a line matching --skip-start '${skipStartRE!!.pattern}' or it was the last line in that file!!!")
                            }
                        }
                        if ( replaceBetweenSkipStartAndSkipEnd != null && ! rr.didNotHadALineMatchingSkipStart) {
                            rr.lines.add(replaceBetweenSkipStartAndSkipEnd!!)
                            currentLine = eatUpToSkipEndOrEndOfFile(currentLine, bufferedFileSource, rr)
                        } else if (removeBetweenSkipStartAndSkipEnd && ! rr.didNotHadALineMatchingSkipStart) {
                            currentLine = eatUpToSkipEndOrEndOfFile(currentLine, bufferedFileSource, rr)
                        } else if (rplREs.isNotEmpty()) {
                            //
                            // ==============================================================
                            currentLine = doReplacements(currentLine, bufferedFileSource, rr)
                            // ==============================================================
                            //
                        }
                    }
                }
            }
            if (omitSkipEnd && indexAfterLastSkipEnd >= 0) {
                for (i in 0 until (rr.lines.size - indexAfterLastSkipEnd)) {
                    rr.lines.removeLastOrNull()
                }
            }
        }

        echoWarnings()
        writeFiles(fileReplaceResults)
    } //  run()

    private fun doReplacements(line: String?, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var currentLine: String? = line
        while (currentLine != null) {
            if ( (skipEndRE != null) && (skipEndRE!!.containsMatchIn(currentLine)) ) {
                if ( ! omitEndLine) rr.lines.add(currentLine)
                indexAfterLastSkipEnd = rr.lines.size
                currentLine = bufferedFileSource.readUtf8Line() // lineAfterSkipEndLine
                break
            }

            // ==============================================================
            for ((index, pair) in rplREs.withIndex()) {
                val modifiedLine = currentLine!!.replace(pair.first, pair.second) // regex replace in THIS line !!!
                rr.lines.add(modifiedLine) // if regex did not match, line is unchanged
                if ( ! allReplacements && (modifiedLine != currentLine) ) {
                    break // at most ONE given regex should alter the line
                } else if (allReplacements && (index != (rplREs.size - 1)) ) {
                    // operate on the (maybe altered) modifiedLine in the next iteration, but keep the result of the last iteration
                    currentLine = rr.lines.removeLast()
                }
            }
            // ==============================================================

            currentLine = bufferedFileSource.readUtf8Line()
        }
        return currentLine
    }

    private fun readUpToStartMatch(line: String?, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var beforeSkipStartLine = true
        var currentLine: String? = line
        do {
            if (skipStartRE!!.containsMatchIn(currentLine!!)) {
                beforeSkipStartLine = false
                firstSkipStartFound = true
                if ( ! omitStartLine) rr.lines.add(currentLine)
            } else if ( ! omitSkipStart || firstSkipStartFound) {
                rr.lines.add(currentLine)
            }
            currentLine = bufferedFileSource.readUtf8Line()
        } while (beforeSkipStartLine && currentLine != null)
        return currentLine // lineAfterSkipStartLine
    }

    private fun eatUpToSkipEndOrEndOfFile(line: String?, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var beforeSkipEndLine = true
        var currentLine: String? = line
        do {
            if (skipEndRE != null && skipEndRE!!.containsMatchIn(currentLine!!)) {
                beforeSkipEndLine = false
                if ( ! omitEndLine) rr.lines.add(currentLine)
                indexAfterLastSkipEnd = rr.lines.size
            }
            currentLine = bufferedFileSource.readUtf8Line()
        } while (beforeSkipEndLine && currentLine != null)
        return currentLine // lineAfterSkipEndLine
    }

    private fun writeFiles(fileReplaceResults: List<ReplaceResult>) {
        for (rr in fileReplaceResults) {
            if (stdout) {
                echo(rr.origFilePath.toString())
                echo(rr.origFilePath.toString().replace(".".toRegex(), "="))
                for (l in rr.lines) {
                    echo(l)
                }
            } else {
                if (backup) {
                    FS.copy(rr.origFilePath, (rr.origFilePath.toString() + ".backup").toPath())
                }
                if (rr.didNotHadALineMatchingSkipStart) continue
                val sinkPath = if (inline) {
                    rr.origFilePath
                } else {
                    (rr.origFilePath.toString() + postfix).toPath()
                }
                FileSystem.SYSTEM.write(sinkPath) {
                    for (line in rr.lines) {
                        writeUtf8(line).writeUtf8("\n")
                    }
                }
            }
        }
    }

    private fun echoWarnings() {
        if (warnings.isNotEmpty()) {
            echo(); echo("warnings:")
        }
        for (warning in warnings) {
            echo("  $warning")
        }
    }

    private fun validateArgs(): MutableList<ReplaceResult> {
        val errors: MutableList<String> = mutableListOf()
        if (args.isEmpty()) {
            echo("no files given!"); exitProcess(1)
        }
        skipStartRE = if (skipStartOpt.isNotEmpty()) if (caseInsensitive) skipStartOpt.toRegex(RegexOption.IGNORE_CASE) else skipStartOpt.toRegex() else null
        skipEndRE = if (skipEndOpt.isNotEmpty()) if (caseInsensitive) skipEndOpt.toRegex(RegexOption.IGNORE_CASE) else skipEndOpt.toRegex() else null
        val fileReplaceResults: MutableList<ReplaceResult> = mutableListOf()
        for (fileString in args) {
            val origFilePath = fileString.toPath()
            val fileMeta = FS.metadataOrNull(origFilePath)
            if ( ! FS.exists(origFilePath)) {
                if ( ! ignoreNonexisting) errors.add(fileString) else warnings.add("$fileString does not exist!")
            } else if (fileMeta?.isDirectory != false) {
                errors.add(if (fileString.endsWith("/") || fileString.endsWith("\\")) fileString else "$fileString/")
            } else {
                fileReplaceResults.add(ReplaceResult(origFilePath))
            }
        }
        if (errors.isNotEmpty()) {
            echo("given file does not exist or is a folder/dir: ${errors.joinToString("', '", "'", "'")}"); exitProcess(2)
        }
        if (rplOpts.isEmpty() && ! (removeBetweenSkipStartAndSkipEnd || replaceBetweenSkipStartAndSkipEnd != null) ) { errors.add("nothing to replace given") }
        for (reString in rplOpts) {
            val re = if (caseInsensitive) reString.first.toRegex(RegexOption.IGNORE_CASE) else reString.first.toRegex()
            rplREs.add(Pair(re, reString.second))
        }
        if (postfixOpt.isNotBlank() && inline) { errors.add("--postfix and --inline/--overwrite are mutual exclusive!") }
        if ( ! inline && postfixOpt.isBlank()) postfix = ".replaced" else if ( ! inline ) postfix = postfixOpt
        if (removeBetweenSkipStartAndSkipEnd && replaceBetweenSkipStartAndSkipEnd != null) { errors.add("--skip-remove and --skip-replace opts are mutual exclusive!") }
        if ( rplREs.isNotEmpty() && (removeBetweenSkipStartAndSkipEnd || replaceBetweenSkipStartAndSkipEnd != null) ) {
            errors.add("--replace opt is mutual exclusive to either of --skip-remove and --skip-replace")
        }
        if (errors.isNotEmpty()) {
            echo("problems with given --replace things: ${errors.joinToString("', '", "'", "'")}"); exitProcess(3)
        }
        return fileReplaceResults
    }
}
