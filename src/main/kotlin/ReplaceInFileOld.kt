// the following kscript annotation directives are only needed, if you want to call this via kscript without using Main.kt
//@file:EntryPoint("ReplaceInFileOldKt")       // ending in <filename>Kt if main is global (not in class companion)
////@file:EntryPoint("ReplaceInFileOld") // ending in <classname> (without Kt) if main is static in class companion
//@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
//@file:DependsOn("com.squareup.okio:okio:3.6.0")
//@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

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
//fun main(args: Array<out String>) = ReplaceInFileOld().main(args) // Main.kt and clikt subcommands cannot both have a fun main()

class ReplaceInFileOld(val FS: FileSystem) : CliktCommand(name = "replaceInFile") {
    //companion object { @JvmStatic fun main(args: Array<out String>) = ReplaceInFileOld().main(args) }
    val rplOpts: List<Pair<String, String>> by option("--replace", "-rpl").pair().multiple().help("pairs of regex and replacement strings.\u0085Only first regex that matches will be replaced (unless you explicitly specify --all)\u0085Replacement may have back references to regex groups via \$1, \$2, ...\u0085Mutual exclusive to either of --replace-region and --remove-region")
    val allReplacements: Boolean            by option("--all", "-a").flag().help("if multiple --replace pairs are give execute them all\u0085(otherwise only the first matching --replace will be executed)")
    val postfixOpt: String                  by option("--postfix", "-p").default("").help("postfix for output file(s)\u0085default: '.replaced'\u0085(mutual exclusive with --inline/--overwrite)")
    val inline: Boolean                     by option("--inline", "--overwrite").flag().help("replace file contents inline in given file(s)\u0085(mutual exclusive with --postfix)")
    val stdout: Boolean                     by option("--stdout").flag().help("do not write any files, but print resulting files to stdout (also ignores --backup)")
    val backup: Boolean                     by option("--backup").flag().help("keep a backup copy of the original file(s) with postfix '.backup'")
    val caseInsensitive: Boolean            by option("--case-insensitive", "-i").flag().help("case-insensitive regex matching")
    val ignoreNonexisting: Boolean          by option("--ignore-nonexisting").flag().help("ignore files that do not exist")
    val regionStartOpt: String              by option("--region-start", "-s").default("").help("leave all lines untouched from start of file (and after --region-end)\u0085up to line matching this RE (unless --omit-before-first-region)")
    val regionEndOpt: String                by option("--region-end", "-e").default("").help("leave all lines untouched from line matching this RE\u0085up to the end of the file (or next --region-start) (unless --omit-after-last-region)")
    val omitBeforeFirstRegionStart: Boolean by option("--omit-before-first-region", "-os").flag().help("do not write the lines from start of file up to the first '--region-start' to the output file")
    val omitAfterLastRegionEnd: Boolean     by option("--omit-after-last-region", "-oe").flag().help("do not write the lines from after last match of '--region-end' up to the end of file to the output file")
    val omitRegionStartLines: Boolean       by option("--omit-region-start-lines", "-osl").flag().help("do not write the line(s) itself found by '--region-start' to the output")
    val omitRegionEndLines: Boolean         by option("--omit-region-end-lines", "-oel").flag().help("do not write the line(s) itself found by '--region-end' to the output")
    val replaceRegion: String?              by option("--replace-region").help("replace anything between --region-start and --region-end with this\u0085(mutual exclusive with --replace and --remove-region)")
    val removeRegion: Boolean               by option("--remove-region").flag().help("just remove anything between --region-start and --region-end\u0085(mutual exclusive with --replace and --replace-region)")
    val verbose: Boolean                    by option("--verbose").flag().help("more verbose output and statistics")
    val silent: Boolean                     by option("--silent").flag().help("as few output as makes sense")
    val args: Set<String>                   by argument().multiple().unique().help("files to do replacement(s) in")
    var regionStartRE: Regex? = null
    var regionEndRE: Regex? = null
    var rplREs: MutableList<Pair<Regex, String>> = mutableListOf()
    var firstRegionStartFound = false
    lateinit var postfix: String
    val warnings: MutableList<String> = mutableListOf()

    //class BoolState(var isTrue: Boolean) {
    //    override fun equals(other: Any?): Boolean = if ((other is Boolean && isTrue != other) || (other !is BoolState) ) false else isTrue == other.isTrue
    //    override fun hashCode(): Int = isTrue.hashCode()
    //}
    class ReplaceResult(
        val origFilePath: Path,
        val outLines: MutableList<String> = mutableListOf(),
        //var foundARegionStartLine: Boolean = false,
        var currentOutLineNumber: Int = -42,
        var currentOrigLineNumber: Int = -42,
        val regions: MutableList<Pair<Int, Int>> = mutableListOf(), // 0-indexed start, +1-index end (for usage with slice(x until y))
        val countAlteredLines: Int = 0,
        val countRemovedLines: Int = 0,
        val countAddedLines: Int = 0,
    ) {
        override fun toString() = origFilePath.toString()
        fun initLineNumbers() { currentOutLineNumber = 0 ; currentOrigLineNumber = 0 }
        fun incBothLineNumbers(inc: Int = 1) { currentOutLineNumber + inc ; currentOrigLineNumber + inc }
        fun incOrigLineNumber(inc: Int = 1) { currentOrigLineNumber + inc }
        fun incOutLineNmber(inc: Int = 1) { currentOutLineNumber + inc }
        //fun incOrigLineNumber() { currentOrigLineNumber++ }
        fun addOutLine(line: String) { outLines.add(line) }
    }

    override fun run() {
        val fileReplaceResults: List<ReplaceResult> = validateArgs()

        for (rr in fileReplaceResults) {
            FS.source(rr.origFilePath).use { fileSource ->
                fileSource.buffer().use { bufferedFileSource ->
                    var currentLine: String? = bufferedFileSource.readUtf8Line()
                    rr.initLineNumbers()
                    if (currentLine == null) { warnings.add("${rr.origFilePath} did not have any content !!!") }
                    while (currentLine != null) {
                        if (regionStartRE != null) {
                            currentLine = readUpToRegionStartMatch(currentLine, bufferedFileSource, rr)
                            // currentLine now is lineAfterRegionStartLine
                            if ( ! firstRegionStartFound && currentLine == null) {
                                warnings.add("${rr.origFilePath} did not had a line matching --region-start '${regionStartRE!!.pattern}' or it was the last line in that file!!!")
                            }
                        }
                        if ( replaceRegion != null && rr.regions.isNotEmpty()) {
                            rr.outLines.add(replaceRegion!!)
                            rr.incOutLineNmber(replaceRegion!!.split("\n").size)
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
                        if (currentLine == null) {
                            finalizeRegions(rr)
                        }
                    }
                }
            }
            if (omitAfterLastRegionEnd && rr.regions.isNotEmpty() && rr.regions.last().second >= 0) {
                for (i in 0 until (rr.outLines.size - rr.regions.last().second)) { // TODO CHECK
                    rr.outLines.removeLastOrNull()
                }
            }
        }

        if (verbose) echoInfos(fileReplaceResults)
        echoWarnings()
        writeFiles(fileReplaceResults)
    } //  run()

    private fun addLineAndReadNextLineNormal(currentMaybeModifiedLine: String, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? =
        bufferedFileSource.readUtf8Line().also {
            rr.addOutLine(currentMaybeModifiedLine)
            rr.incBothLineNumbers()
        }
    private fun addLineAndReadNextLineAfterRegionStartMatch(currentMaybeModifiedLine: String, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        val nextLine = bufferedFileSource.readUtf8Line()
        if (nextLine == null) { warnings.add("${rr.origFilePath} --region-start match on last line of file") }
        rr.regions.add(Pair(rr.outLines.size, -1))
        if (omitRegionStartLines) {
            rr.incOrigLineNumber()
        } else {
            rr.addOutLine(currentMaybeModifiedLine)
            rr.incBothLineNumbers()
        }
        return nextLine
    }
    private fun addLineAndReadNextLineAfterRegionEndMatch(currentMaybeModifiedLine: String, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        if (omitRegionEndLines) {
            rr.incOrigLineNumber()
        } else {
            rr.addOutLine(currentMaybeModifiedLine)
            rr.incBothLineNumbers()
        }
        if (rr.regions.isEmpty() || rr.regions.last().second != -1) {
            if (regionStartRE == null) {
                rr.regions.clear()
                rr.regions.add(Pair(0, rr.outLines.size))
                eatUpToEndOfFile(bufferedFileSource, rr)
            } else {
                warnings.add("${rr.origFilePath} did not had a matching --region-start at line ${rr.currentOutLineNumber}")
            }
        } else {
            val region = rr.regions.removeLast()
            rr.regions.add(region.copy(second = rr.outLines.size)) // index+1 for later usage of list.slice(start until end)
        }
        val nextLine = bufferedFileSource.readUtf8Line()
        return nextLine
    }
    private fun omitLineAndReadNextLine(bufferedFileSource: BufferedSource, rr: ReplaceResult): String? =
        bufferedFileSource.readUtf8Line().also {
            rr.incOrigLineNumber()
        }

    private fun doReplacements(line: String?, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var currentLine: String? = line
        while (currentLine != null) {
            if ( (regionEndRE != null) && (regionEndRE!!.containsMatchIn(currentLine)) ) {
                currentLine = addLineAndReadNextLineAfterRegionEndMatch(currentLine, bufferedFileSource, rr)
                break
            }

            // ==============================================================
            var modifiedLine: String = currentLine
            for (pair in rplREs) {
                modifiedLine = modifiedLine.replace(pair.first, pair.second) // regex replace in THIS line !!!
                if ( ! allReplacements && (modifiedLine != currentLine) ) {
                    break // at most ONE given regex should alter the line
                }
            }
            // ==============================================================

            currentLine = addLineAndReadNextLineNormal(modifiedLine, bufferedFileSource, rr)
        }
        return currentLine
    }

    private fun readUpToRegionStartMatch(line: String?, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var currentLine: String? = line
        do {
            if (regionStartRE!!.containsMatchIn(currentLine!!)) {
                firstRegionStartFound = true
                currentLine = addLineAndReadNextLineAfterRegionStartMatch(currentLine, bufferedFileSource, rr)
                // current line now is lineAfterRegionStartLine
                break
            } else if ( omitBeforeFirstRegionStart && ! firstRegionStartFound) {
                currentLine = omitLineAndReadNextLine(bufferedFileSource, rr)
            } else {
                // after the complete first Region, but outside the other regions
                currentLine = addLineAndReadNextLineNormal(currentLine, bufferedFileSource, rr)
            }
        } while (currentLine != null)
        return currentLine // lineAfterRegionStartLine
    }

    private fun eatUpToRegionEndOrEndOfFile(line: String?, bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var currentLine: String? = line
        do {
            if (regionEndRE != null && regionEndRE!!.containsMatchIn(currentLine!!)) {
                currentLine = addLineAndReadNextLineAfterRegionEndMatch(currentLine, bufferedFileSource, rr)
                break
            } else {
                currentLine = omitLineAndReadNextLine(bufferedFileSource, rr)
            }
        } while (currentLine != null)
        return currentLine // lineAfterRegionEndLine
    }
    private fun eatUpToEndOfFile(bufferedFileSource: BufferedSource, rr: ReplaceResult): String? {
        var nextLine = bufferedFileSource.readUtf8Line()
        while ( nextLine != null) {
            rr.outLines.add(nextLine)
            rr.incBothLineNumbers()
            nextLine = bufferedFileSource.readUtf8Line()
        }
        finalizeRegions(rr)
        return null
    }

    private fun finalizeRegions(rr: ReplaceResult) {
        if (rr.regions.isNotEmpty() && rr.regions.last().second == -1) {
            val pair = rr.regions.removeLast()
            rr.regions.add(pair.copy(second = rr.outLines.size))
        }
    }

    private fun writeFiles(fileReplaceResults: List<ReplaceResult>) {
        for (rr in fileReplaceResults) {
            if (stdout) {
                echo(rr.origFilePath.toString())
                echo(rr.origFilePath.toString().replace(".".toRegex(), "=")) // underline
                for (l in rr.outLines) {
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
                    for (line in rr.outLines) {
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

    private fun echoInfos(fileReplaceResults: List<ReplaceResult>) {
        for (rr in fileReplaceResults) {
            echo("${rr.origFilePath}:")
            echo("${rr.origFilePath}:".replace(".".toRegex(), "=")) // underline
            val infolines: MutableList<String> = mutableListOf()
            if (rr.regions.isEmpty()) infolines.add("  no regions") else { infolines.add("  regions:") ; infolines.add("==========") }
            for (region in rr.regions) {
                infolines.add("    ${region.first}..${region.second}")
                infolines.add(rr.outLines.slice(region.first until region.second).joinToString("\n      |", "      |"))
            }
            println(infolines.joinToString("\n"))
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
