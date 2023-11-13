//// the following kscript annotation directives are only needed, if you want to call this via kscript without using Main.kt
//@file:EntryPoint("ReplaceInFilesKt")       // ending in <filename>Kt if main is global (not in class companion)
////@file:EntryPoint("files.ReplaceInFiles") // ending in <classname> (without Kt) if main is static in class companion
//@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
//@file:DependsOn("com.squareup.okio:okio:3.6.0")
//@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

package files

//import DependsOn
//import EntryPoint
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique
import com.github.ajalt.clikt.parameters.options.*
import okio.*
import okio.Path.Companion.toPath
import kotlin.io.use

//// this main() only needed, if you want to call this via kscript without using Main.kt
//fun main(args: Array<out String>) = ReplaceInFiles().main(args) // Main.kt and clikt subcommands cannot both have a fun main()

class ReplaceInFiles(val FS: FileSystem) : CliktCommand(name = "replaceInFile") {
    //companion object { @JvmStatic fun main(args: Array<out String>) = ReplaceInFiles().main(args) }
    val rplOpts: List<Pair<String, String>> by option("--replace", "-rpl").pair().multiple().help("pairs of regex and replacement strings.\u0085Only first regex that matches will be replaced (unless you explicitly specify --all)\u0085Replacement may have back references to regex groups via \$1, \$2, ...\u0085Mutual exclusive to either of --replace-region and --remove-region")
    val allReplacements: Boolean by option("--all", "-a").flag().help("if multiple --replace pairs are give execute them all\u0085(otherwise only the first matching --replace will be executed)")
    val postfixOpt: String by option("--postfix", "-p").default("").help("postfix for output file(s)\u0085default: '.replaced'\u0085(mutual exclusive with --inline/--overwrite)")
    val inline: Boolean by option("--inline", "--overwrite").flag().help("replace file contents inline in given file(s)\u0085(mutual exclusive with --postfix)")
    val stdout: Boolean by option("--stdout").flag().help("do not write any files, but print resulting files to stdout (also ignores --backup)")
    val backup: Boolean by option("--backup").flag().help("keep a backup copy of the original file(s) with postfix '.backup'")
    val caseInsensitive: Boolean by option("--case-insensitive", "-i").flag().help("case-insensitive regex matching")
    val ignoreNonexisting: Boolean by option("--ignore--nonexisting").flag().help("ignore files that do not exist")
    val regionStartOpt: String by option("--region-start", "-s").default("").help("leave all lines untouched from start of file (and after --region-end)\u0085up to line matching this RE (unless --omit-before-first-region)")
    val regionEndOpt: String by option("--region-end", "-e").default("").help("leave all lines untouched from line matching this RE\u0085up to the end of the file (or next --region-start) (unless --omit-after-last-region)")
    val omitBeforeFirstRegionStart: Boolean by option("--omit-before-first-region", "-os").flag().help("do not write the lines from start of file up to the first '--region-start' to the output file")
    val omitAfterLastRegionEnd: Boolean by option("--omit-after-last-region", "-oe").flag().help("do not write the lines from after last match of '--region-end' up to the end of file to the output file")
    val omitRegionStartLines: Boolean by option("--omit-region-start-lines", "-osl").flag().help("do not write the line(s) itself found by '--region-start' to the output")
    val omitRegionEndLines: Boolean by option("--omit-region-end-lines", "-oel").flag().help("do not write the line(s) itself found by '--region-end' to the output")
    val replaceRegion: String? by option("--replace-region").help("replace anything between --region-start and --region-end with this\u0085(mutual exclusive with --replace and --remove-region)")
    val removeRegion: Boolean by option("--remove-region").flag().help("just remove anything between --region-start and --region-end\u0085(mutual exclusive with --replace and --replace-region)")
    val windowsCRLFOpt: Boolean by option("--crlf").flag().help("use windows \\r\\n instead of unix \\n")
    val verbose: Boolean by option("--verbose").flag().help("more verbose output and statistics")
    val silent: Boolean by option("--silent").flag().help("as few output as makes sense")
    val args: Set<String> by argument().multiple().unique().help("files to do replacement(s) in")
    var regionStartRE: Regex? = null
    var regionEndRE: Regex? = null
    var rplREs: MutableList<Pair<Regex, String>> = mutableListOf()
    var crlf = "\n"
    var crlfByteSize = 4L
    lateinit var postfix: String

    // some helper functions that are platform specific (kotlin native/multiplatform)
    fun printlnSTDERR(line: String = "") { System.err.println(line) }
    fun printlnSTDOUT(line: String = "") { echo(line) }
    fun byteArrayToUTF8(byteArray: ByteArray) = byteArray.toString(Charsets.UTF_8)

    class ReplaceResult(
        val origFilePath: Path,
        val omitRegionStartLines: Boolean,
        val omitRegionEndLines: Boolean,
        val buffer: Buffer = Buffer(),
        val regionIndexes: MutableList<Region> = mutableListOf(),
        val regionBuffers: MutableList<Buffer> = mutableListOf(),
        //val countAlteredLines: Int = 0,
        //val countRemovedLines: Int = 0,
        //val countAddedLines: Int = 0,
    ) {
        override fun toString() = origFilePath.toString()
        companion object { var CRLF: String = "\n" ; var CRLFBYTESIZE: Long = 4L }
        /** Byte offsets of Regions in Buffer */
        class Region(val befStart: Long, val aftStart: Long, var befEnd: Long, var aftEnd: Long) {
            override fun toString(): String = "$befStart..$befEnd (${befEnd - befStart}) to $aftStart..$aftEnd (${aftEnd - aftStart})"
        }
        val warnings: MutableList<String> = mutableListOf()
        var insideRegion = false
        fun addLine(line: String) {
            buffer.writeUtf8(line).writeUtf8(CRLF)
            if (insideRegion && regionBuffers.isNotEmpty()) {
                regionBuffers.last().writeUtf8(line).writeUtf8(CRLF)
            }
        }
        fun addRegionStart(line: String) {
            val indexBeforeWrite = buffer.size
            buffer.writeUtf8(line).writeUtf8(CRLF)
            val regionBuffer = Buffer()
            regionBuffers.add(regionBuffer)
            regionBuffer.writeUtf8(line).writeUtf8(CRLF)
            if (regionIndexes.isNotEmpty() && regionIndexes.last().aftStart == -1L) {
                warnings.add("$origFilePath consecutive --region-start lines without --region-end in between at lines ${TODO()}")
            } else {
                regionIndexes.add(Region(indexBeforeWrite, buffer.size + CRLFBYTESIZE, -1L, -1L))
            }
            insideRegion = true
        }
        fun addRegionEnd(line: String) {
            val indexBeforeWrite = buffer.size
            buffer.writeUtf8(line).writeUtf8(CRLF)
            if (regionIndexes.isEmpty() || regionIndexes.last().befEnd != -1L) {
                warnings.add("$origFilePath --region-end without matching --region-start before it at line ${TODO()}")
            } else if (regionIndexes.isNotEmpty()) {
                regionIndexes.last().apply { befEnd = indexBeforeWrite ; aftEnd = buffer.size + CRLFBYTESIZE}
                regionBuffers.last().writeUtf8(line).writeUtf8(CRLF)
            }
            insideRegion = false
        }
        fun finalizeRegions() {
            if (regionIndexes.isNotEmpty() && regionIndexes.last().aftStart == -1L) {
                regionIndexes.last().apply { befEnd = buffer.size ; aftEnd = buffer.size }
            }
        }
    }

    override fun run() {
        val fileReplaceResults: List<ReplaceResult> = validateArgs()

        for (rr in fileReplaceResults) {
            // first read complete original file into an okio buffer and remember byte offsets of region start/ends
            FS.source(rr.origFilePath).use { fileSource ->
                fileSource.buffer().use { bufferedFileSource ->
                    var currentLine: String? = bufferedFileSource.readUtf8Line()
                    if (currentLine == null) { rr.warnings.add("${rr.origFilePath} did not have any content !!!") }
                    while (currentLine != null) {
                        if (regionStartRE != null && regionStartRE!!.containsMatchIn(currentLine)) {
                            rr.addRegionStart(currentLine)
                        } else if (regionEndRE != null && regionEndRE!!.containsMatchIn(currentLine)) {
                            rr.addRegionEnd(currentLine)
                        } else {
                            rr.addLine(currentLine)
                        }
                        currentLine = bufferedFileSource.readUtf8Line()
                    }
                    rr.finalizeRegions()
                }
            }

            for (rr in fileReplaceResults) {
                // depending on which parts of the buffer should go to the outFile
                // - take the parts and just write them to out
                // - omit the parts
                // - alter the parts and then write them

            }

            if (verbose) echoInfos(fileReplaceResults)
            echoWarnings(fileReplaceResults)
            writeFiles(fileReplaceResults)
        }
    }


    private fun validateArgs(): MutableList<ReplaceResult> {
        val errors: MutableList<String> = mutableListOf()
        if (args.isEmpty()) {
            printlnSTDERR("no files given!"); throw ProgramResult(1)
        }
        regionStartRE = if (regionStartOpt.isNotEmpty()) if (caseInsensitive) regionStartOpt.toRegex(RegexOption.IGNORE_CASE) else regionStartOpt.toRegex() else null
        regionEndRE = if (regionEndOpt.isNotEmpty()) if (caseInsensitive) regionEndOpt.toRegex(RegexOption.IGNORE_CASE) else regionEndOpt.toRegex() else null
        val fileReplaceResults: MutableList<ReplaceResult> = mutableListOf()
        for (fileString in args) {
            val origFilePath = fileString.toPath()
            val fileMeta = FS.metadataOrNull(origFilePath)
            if ( ! FS.exists(origFilePath)) {
                if ( ! ignoreNonexisting) errors.add(fileString) else printlnSTDERR("$fileString does not exist!")
            } else if (fileMeta?.isDirectory != false) {
                errors.add(if (fileString.endsWith("/") || fileString.endsWith("\\")) fileString else "$fileString/")
            } else {
                fileReplaceResults.add(ReplaceResult(origFilePath, omitRegionStartLines , omitRegionEndLines))
            }
        }
        if (errors.isNotEmpty()) {
            printlnSTDERR("given file does not exist or is a folder/dir: ${errors.joinToString("', '", "'", "'")}"); throw ProgramResult(2)
        }
        if (rplOpts.isEmpty() && ! (removeRegion || replaceRegion != null) ) { errors.add("nothing to replace given") }
        for (reString in rplOpts) {
            val re = if (caseInsensitive) reString.first.toRegex(RegexOption.IGNORE_CASE) else reString.first.toRegex()
            rplREs.add(Pair(re, reString.second))
        }
        if (postfixOpt.isNotBlank() && inline) { errors.add("--postfix and --inline/--overwrite are mutual exclusive!") }
        if ( ! inline && postfixOpt.isBlank()) postfix = ".replaced" else if ( ! inline ) postfix = postfixOpt
        if (windowsCRLFOpt) { crlf = "\r\n" ; crlfByteSize = 8L ; ReplaceResult.CRLF = "\r\n" ; ReplaceResult.CRLFBYTESIZE = 4L }
        if (removeRegion && replaceRegion != null) { errors.add("--remove-region and --replace-region opts are mutual exclusive!") }
        if ( rplREs.isNotEmpty() && (removeRegion || replaceRegion != null) ) {
            errors.add("--replace opt is mutual exclusive to either of --remove-region and --replace-region")
        }
        if (errors.isNotEmpty()) {
            printlnSTDERR("problems with given --replace things: ${errors.joinToString("', '", "'", "'")}"); throw ProgramResult(3)
        }
        return fileReplaceResults
    }

    private fun echoInfos(fileReplaceResults: List<ReplaceResult>) {
        for (rr in fileReplaceResults) {
            printlnSTDOUT("${rr.origFilePath}:")
            printlnSTDOUT("${rr.origFilePath}:".replace(".".toRegex(), "=")) // underline
            if (rr.regionIndexes.isEmpty()) printlnSTDOUT("no regions") else { printlnSTDOUT("regions:") ; printlnSTDOUT("========") }
            if (rr.regionIndexes.isEmpty()) { printlnSTDERR("  no regions.") } else { printlnSTDERR("  regions:") }
            for ( (index, region) in rr.regionIndexes.withIndex()) {
                printlnSTDOUT("  $region")
                val byteArray = ByteArray(rr.regionBuffers[index].size.toInt())
                rr.regionBuffers[index].readFully(byteArray)
                var s = byteArrayToUTF8(byteArray)
                s = s.replace("\n(.)".toRegex(), "\n    $1")
                printlnSTDOUT("    $s")
            }
        }
    }

    private fun echoWarnings(fileReplaceResults: List<ReplaceResult>) {
        if (fileReplaceResults.isEmpty() || !fileReplaceResults.none { it.warnings.isNotEmpty() }) {
            printlnSTDERR("warnings:")
        }
        for (rr in fileReplaceResults) {
            for (warning in rr.warnings) {
                printlnSTDERR("  $warning")
            }
        }
    }

    private fun writeFiles(fileReplaceResults: List<ReplaceResult>) {
        for (rr in fileReplaceResults) {
            if (stdout) {
                printlnSTDERR(rr.origFilePath.toString())
                printlnSTDERR(rr.origFilePath.toString().replace(".".toRegex(), "=")) // underline
                val byteArray = ByteArray(rr.buffer.size.toInt())
                rr.buffer.readFully(byteArray)
                printlnSTDOUT(byteArrayToUTF8(byteArray))
            } else {
                if (backup) {
                    FS.copy(rr.origFilePath, (rr.origFilePath.toString() + ".backup").toPath())
                }
                if (regionStartRE != null &&  rr.regionIndexes.isEmpty()) continue
                val sinkPath = if (inline) {
                    rr.origFilePath
                } else {
                    (rr.origFilePath.toString() + postfix).toPath()
                }
                //FS.write(sinkPath) {
                //    for (line in rr.outLines) {
                //        writeUtf8(line).writeUtf8("\n")
                //    }
                //}
                val fileSink = FS.sink(sinkPath).buffer()
                fileSink.writeAll(rr.buffer)
                fileSink.close()
            }
        }
    }
}

fun Buffer.readUtf8LineCustom(): Pair<String?, Long> {
    val nlIndex = indexOf('\n'.code.toByte())
    val line: String? = when {
        nlIndex != -1L -> readUtf8(nlIndex + 1L) // read including the \n (and also including a potential windows \r before it)
        this.size != 0L -> readUtf8(this.size) // read the remainder of the buffer
        else -> null // there was nothing to read
    }
    return Pair(line, nlIndex)
}
