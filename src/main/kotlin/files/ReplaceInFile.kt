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

class ReplaceInFile(val FS: FileSystem) : CliktCommand(name = "replaceInFile") {
    //companion object { @JvmStatic fun main(args: Array<out String>) = ReplaceInFile().main(args) }
    val rplOpts: List<Pair<String, String>> by option("--replace", "-rpl").pair().multiple().help("pairs of regex and replacement strings.\u0085Only first regex that matches will be replaced (unless you explicitly specify --all)\u0085Replacement may have back references to regex groups via \$1, \$2, ...\u0085Mutual exclusive to either of --replace-region and --remove-region")
    val allReplacements: Boolean            by option("--all", "-a").flag().help("if multiple --replace pairs are give execute them all\u0085(otherwise only the first matching --replace will be executed)")
    val postfixOpt: String                  by option("--postfix", "-p").default("").help("postfix for output file(s)\u0085default: '.replaced'\u0085(mutual exclusive with --inline/--overwrite)")
    val inline: Boolean                     by option("--inline", "--overwrite").flag().help("replace file contents inline in given file(s)\u0085(mutual exclusive with --postfix)")
    val stdout: Boolean                     by option("--stdout").flag().help("do not write any files, but print resulting files to stdout (also ignores --backup)")
    val backup: Boolean                     by option("--backup").flag().help("keep a backup copy of the original file(s) with postfix '.backup'")
    val caseInsensitive: Boolean            by option("--case-insensitive", "-i").flag().help("case-insensitive regex matching")
    val ignoreNonexisting: Boolean          by option("--ignore--nonexisting").flag().help("ignore files that do not exist")
    val regionStartOpt: String              by option("--region-start", "-s").default("").help("leave all lines untouched from start of file (and after --region-end)\u0085up to line matching this RE (unless --omit-before-first-region)")
    val regionEndOpt: String                by option("--region-end", "-e").default("").help("leave all lines untouched from line matching this RE\u0085up to the end of the file (or next --region-start) (unless --omit-after-last-region)")
    val omitBeforeFirstRegionStart: Boolean by option("--omit-before-first-region", "-os").flag().help("do not write the lines from start of file up to the first '--region-start' to the output file")
    val omitAfterLastRegionEnd: Boolean     by option("--omit-after-last-region", "-oe").flag().help("do not write the lines from after last match of '--region-end' up to the end of file to the output file")
    val omitRegionStartLines: Boolean       by option("--omit-region-start-lines", "-osl").flag().help("do not write the line(s) itself found by '--region-start' to the output")
    val omitRegionEndLines: Boolean         by option("--omit-region-end-lines", "-oel").flag().help("do not write the line(s) itself found by '--region-end' to the output")
    val replaceRegion: String?              by option("--replace-region").help("replace anything between --region-start and --region-end with this\u0085(mutual exclusive with --replace and --remove-region)")
    val removeRegion: Boolean               by option("--remove-region").flag().help("just remove anything between --region-start and --region-end\u0085(mutual exclusive with --replace and --replace-region)")
    val args: Set<String>                   by argument().multiple().unique().help("files to do replacement(s) in")
    var regionStartRE: Regex? = null
    var regionEndRE: Regex? = null
    var rplREs: MutableList<Pair<Regex, String>> = mutableListOf()
    var firstRegionStartFound = false
    var indexAfterLastRegionEnd = -1
    lateinit var postfix: String
    val warnings: MutableList<String> = mutableListOf()

    //class BoolState(var isTrue: Boolean) {
    //    override fun equals(other: Any?): Boolean = if ((other is Boolean && isTrue != other) || (other !is BoolState) ) false else isTrue == other.isTrue
    //    override fun hashCode(): Int = isTrue.hashCode()
    //}
    data class ReplaceResult(
        val origFilePath: Path,
        val lines: MutableList<String> = mutableListOf(),
        //var foundARegionStartLine: Boolean = false,
        var currentLineNumber: Int = -42,
        val regions: MutableList<Pair<Int, Int>> = mutableListOf(),
        val countAlteredLines: Int = 0,
        val countRemovedLines: Int = 0,
        val countAddedLines: Int = 0,
    )

    override fun run() {
        val fileReplaceResults: List<ReplaceResult> = validateArgs()

        for (rr in fileReplaceResults) {
            indexAfterLastRegionEnd = -1
            FS.source(rr.origFilePath).use { fileSource ->
                fileSource.buffer().use { bufferedFileSource ->
                    var currentLine: String? = bufferedFileSource.readUtf8Line()
                    rr.currentLineNumber = 0
                    if (currentLine == null) { warnings.add("${rr.origFilePath} did not have any content !!!") }
                    while (currentLine != null) {
                        if (regionStartRE != null) {
                            currentLine = readUpToStartMatch(currentLine, bufferedFileSource, rr)
                            // currentLine now is lineAfterRegionStartLine
                            if ( ! firstRegionStartFound && currentLine == null) {
                                warnings.add("${rr.origFilePath} did not had a line matching --region-start '${regionStartRE!!.pattern}' or it was the last line in that file!!!")
                            }
                        }
                        if ( replaceRegion != null && rr.regions.isNotEmpty()) {
                            rr.lines.add(replaceRegion!!)
                            currentLine = eatUpToRegionEndOrEndOfFile(currentLine, bufferedFileSource, rr)
                        } else if (removeRegion && rr.regions.isNotEmpty()) {
                            currentLine = eatUpToRegionEndOrEndOfFile(currentLine, bufferedFileSource, rr)
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
            if (omitAfterLastRegionEnd && indexAfterLastRegionEnd >= 0) {
                for (i in 0 until (rr.lines.size - indexAfterLastRegionEnd)) {
                    rr.lines.removeLastOrNull()
                }
            }
        }

        echoWarnings()
        writeFiles(fileReplaceResults)
    } //  run()

    private fun readUtf8Line(bufferedFileSource: BufferedSource, rr: ReplaceInFile.ReplaceResult): String? = bufferedFileSource.readUtf8Line().also { rr.currentLineNumber++ }

    private fun doReplacements(line: String?, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var currentLine: String? = line
        while (currentLine != null) {
            if ( (regionEndRE != null) && (regionEndRE!!.containsMatchIn(currentLine)) ) {
                if ( ! omitRegionEndLines) rr.lines.add(currentLine)
                indexAfterLastRegionEnd = rr.lines.size
                currentLine = readUtf8Line(bufferedFileSource, rr)
                regionEndWithCurrentLineAfterIt(rr)
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

            currentLine = readUtf8Line(bufferedFileSource, rr)
        }
        return currentLine
    }

    private fun readUpToStartMatch(line: String?, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var beforeRegionStartLine = true
        var currentLine: String? = line
        do {
            if (regionStartRE!!.containsMatchIn(currentLine!!)) {
                beforeRegionStartLine = false
                firstRegionStartFound = true
                if ( ! omitRegionStartLines) rr.lines.add(currentLine)
                currentLine = readUtf8Line(bufferedFileSource, rr)
                // current line now is lineAfterRegionStartLine
                rr.regions.add(Pair(rr.currentLineNumber, -1))
            } else if ( ! omitBeforeFirstRegionStart || firstRegionStartFound) {
                rr.lines.add(currentLine)
                currentLine = readUtf8Line(bufferedFileSource, rr)
            }
        } while (beforeRegionStartLine && currentLine != null)
        return currentLine // lineAfterRegionStartLine
    }

    private fun eatUpToRegionEndOrEndOfFile(line: String?, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var beforeRegionEndLine = true
        var currentLine: String? = line
        do {
            if (regionEndRE != null && regionEndRE!!.containsMatchIn(currentLine!!)) {
                beforeRegionEndLine = false
                if ( ! omitRegionEndLines) rr.lines.add(currentLine)
                indexAfterLastRegionEnd = rr.lines.size
                currentLine = readUtf8Line(bufferedFileSource, rr)
                regionEndWithCurrentLineAfterIt(rr)
            } else {
                currentLine = readUtf8Line(bufferedFileSource, rr)
            }
        } while (beforeRegionEndLine && currentLine != null)
        return currentLine // lineAfterRegionEndLine
    }

    private fun regionEndWithCurrentLineAfterIt(rr: ReplaceResult) {
        if (rr.regions.isEmpty() || rr.regions.last().second != -1) {
            warnings.add("${rr.origFilePath} did not had a matching --region-start at line ${rr.currentLineNumber - 1}")
        } else {
            val region = rr.regions.removeLast()
            rr.regions.add(region.copy(second = rr.currentLineNumber))
        }
    }

    private fun writeFiles(fileReplaceResults: List<ReplaceResult>) {
        for (rr in fileReplaceResults) {
            if (stdout) {
                echo(rr.origFilePath.toString())
                echo(rr.origFilePath.toString().replace(".".toRegex(), "=")) // underline
                for (l in rr.lines) {
                    echo(l)
                }
            } else {
                if (backup) {
                    FS.copy(rr.origFilePath, (rr.origFilePath.toString() + ".backup").toPath())
                }
                if (regionStartRE != null &&  rr.regions.isEmpty()) continue
                val sinkPath = if (inline) {
                    rr.origFilePath
                } else {
                    (rr.origFilePath.toString() + postfix).toPath()
                }
                FS.write(sinkPath) {
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
        regionStartRE = if (regionStartOpt.isNotEmpty()) if (caseInsensitive) regionStartOpt.toRegex(RegexOption.IGNORE_CASE) else regionStartOpt.toRegex() else null
        regionEndRE = if (regionEndOpt.isNotEmpty()) if (caseInsensitive) regionEndOpt.toRegex(RegexOption.IGNORE_CASE) else regionEndOpt.toRegex() else null
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
        if (rplOpts.isEmpty() && ! (removeRegion || replaceRegion != null) ) { errors.add("nothing to replace given") }
        for (reString in rplOpts) {
            val re = if (caseInsensitive) reString.first.toRegex(RegexOption.IGNORE_CASE) else reString.first.toRegex()
            rplREs.add(Pair(re, reString.second))
        }
        if (postfixOpt.isNotBlank() && inline) { errors.add("--postfix and --inline/--overwrite are mutual exclusive!") }
        if ( ! inline && postfixOpt.isBlank()) postfix = ".replaced" else if ( ! inline ) postfix = postfixOpt
        if (removeRegion && replaceRegion != null) { errors.add("--remove-region and --replace-region opts are mutual exclusive!") }
        if ( rplREs.isNotEmpty() && (removeRegion || replaceRegion != null) ) {
            errors.add("--replace opt is mutual exclusive to either of --remove-region and --replace-region")
        }
        if (errors.isNotEmpty()) {
            echo("problems with given --replace things: ${errors.joinToString("', '", "'", "'")}"); exitProcess(3)
        }
        return fileReplaceResults
    }
}
