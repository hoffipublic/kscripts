// the following kscript annotation directives are only needed, if you want to call this via kscript without using Main.kt
//@file:EntryPoint("ReplaceInFilesKt")       // ending in <filename>Kt if main is global (not in class companion)
@file:EntryPoint("ReplaceInFilesEntryPoint") // ending in <classname> (without Kt) if main is static in class companion
@file:DependsOn("io.github.kscripting:kscript-annotations:1.5.0")
@file:DependsOn("com.squareup.okio:okio:3.6.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.1")

@file:Import("helpers_ScriptHelpers.kt")

import DependsOn
import EntryPoint
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

/** companion main function in separate class as project might have multiple main classes and anything extending CliktCommand cannot have it */
class ReplaceInFilesEntryPoint {
    /** make sure the multiopt subcommands come first on cmdline and the action subcommand (here 'doWithUsers' after them)
     * cmdline e.g.: kscript <thisFilename.kt> user --login hoffi user --login admin --age 42 doWithUsers fileArg1 fileArg2
     */
    companion object { @JvmStatic fun main(args: Array<out String>) {
        printlnSTDERR(ReplaceInFilesEntryPoint::class.simpleName!!)
        //val mainCliktCommand = ReplaceInFilesEntryPointApp().subcommands(ReplaceInFiles(), UserOptGroup())
        val mainCliktCommand = ReplaceInFiles()
        mainCliktCommand.main(args)
    }}
    //class ReplaceInFilesEntryPointApp : CliktCommand("app", allowMultipleSubcommands = true) { override fun run() { } }
}


class ReplaceInFiles() : CliktCommand(name = "replaceInFiles") {
    val rplOpts: List<Pair<String, String>> by option("--replace", "-rpl").pair().multiple().help("pairs of regex and replacement strings.\u0085Only first regex that matches will be replaced (unless you explicitly specify --all)\u0085Replacement may have back references to regex groups via \$1, \$2, ...\u0085Mutual exclusive to either of --replace-region and --remove-region")
    val allReplacements: Boolean by option("--all", "-a").flag().help("if multiple --replace pairs are give execute them all\u0085(otherwise only the first matching --replace will be executed)")
    val caseInsensitive: Boolean by option("--case-insensitive", "-i").flag().help("case-insensitive regex matching")
    val inline: Boolean by option("--inline", "--overwrite").flag().help("replace file contents inline in given file(s)\u0085(mutual exclusive with --postfix)")
    val stdout: Boolean by option("--stdout").flag().help("do not write any files, but print resulting files to stdout (also ignores --backup)")
    val backup: Boolean by option("--backup").flag().help("keep a backup copy of the original file(s) with postfix '.backup'")
    val ignoreNonexisting: Boolean by option("--ignore-nonexisting").flag().help("ignore files that do not exist")
    val postfixOpt: String by option("--postfix", "-p").default("").help("postfix for output file(s)\u0085default: '.replaced'\u0085(mutual exclusive with --inline/--overwrite)")
    val atomicWriteAllFiles : Boolean by option("--atomic-write-all-files", "--atomic").flag().help("first read and do replacements in ALL files (keeping them in RAM)\u0085write ALL files as last step")
    val regionStartOpt: String by option("--region-start", "-s").default("").help("leave all lines untouched from start of file (and after --region-end)\u0085up to line matching this RE (except --omit-before-first-region)")
    val regionEndOpt: String by option("--region-end", "-e").default("").help("leave all lines untouched from line matching this RE\u0085up to the end of the file (or next --region-start) (except --omit-after-last-region)")
    val lenientRegionsOpt: Boolean by option("--lenient-regions").flag(default = false).help("do not do any replacements in a file if --region-start and --region-end are not paired nicely (default true)\u0085if given --region-end without a matching --region-start will treat anything up to there as a valid region\u0085or a --region-start without a matching --region-end will treat anything from there up to end of the file as a valid region")
    val noReplacementsIfNoRegions: Boolean by option("--ignore-files-with-no-regions", "--fr").flag().help("leave files with no regions untouched")
    val noReplacementsInFileIfWarnings: Boolean by option("--ignore-files-with-warnings", "--if").flag().help("leave files with warnings untouched")
    val omitRegionStartLines: Boolean by option("--omit-region-start-lines", "-osl").flag().help("do not write the line(s) itself found by '--region-start' to the output")
    val omitRegionEndLines: Boolean by option("--omit-region-end-lines", "-oel").flag().help("do not write the line(s) itself found by '--region-end' to the output")
    val omitBeforeFirstRegionStart: Boolean by option("--omit-before-first-region", "-os").flag().help("do not write the lines from start of file up to the first '--region-start' to the output file")
    val omitAfterLastRegionEnd: Boolean by option("--omit-after-last-region", "-oe").flag().help("do not write the lines from after last match of '--region-end' up to the end of file to the output file")
    val replaceRegion: String? by option("--replace-region").help("replace anything between --region-start and --region-end with this\u0085(mutual exclusive with --replace and --remove-region)")
    val removeRegion: Boolean by option("--remove-region").flag().help("just remove anything between --region-start and --region-end\u0085(mutual exclusive with --replace and --replace-region)")
    val windowsCRLFOpt: Boolean by option("--crlf").flag().help("use windows \\r\\n instead of unix \\n")
    val verbose: Boolean by option("--verbose").flag().help("more verbose output and statistics")
    val silent: Boolean by option("--silent").flag().help("as few output as makes sense")
    val args: Set<String> by argument().multiple().unique().help("files to do replacement(s) in")
    var lenientRegions: Boolean = false
    var regionStartRE: Regex? = null
    var regionEndRE: Regex? = null
    var rplREs: MutableList<Pair<Regex, String>> = mutableListOf()
    lateinit var postfix: String

    class ReplaceResult(
        val origFilePath: Path,
        val hasRegions: Boolean,
        val lenientRegions: Boolean,
        val hasRegionStartREGiven: Boolean,
        val hasRegionEndREGiven: Boolean,
        val origBuffer: Buffer = Buffer(),
        val outBuffer: Buffer = Buffer(),
        val regionIndexes: MutableList<Region> = mutableListOf(),
        val regionBuffersOrig: MutableList<Buffer> = mutableListOf(),
        val regionBuffersMod: MutableList<Buffer> = mutableListOf(),
    ) {
        override fun toString() = origFilePath.toString()
        companion object { var CRLF: String = "\n" ; var CRLFBYTESIZE: Long = run { val b = Buffer(); b.writeUtf8(CRLF); b.close(); b.size } }

        /** Byte offsets of Regions in Buffer */
        class Region(val startLineBegin: Long, val startLineEnd: Long, var trailLineBegin: Long, var trailLineEnd: Long, var sizeToNextRegionBegin: Long) {
            override fun toString(): String = "$startLineBegin..$startLineEnd(#$startLineSize) to $trailLineBegin..$trailLineEnd(#$trailLineSize) overall (#$overallSize)"
            val startLineSize: Long
                get() = startLineEnd - startLineBegin
            val trailLineSize: Long
                get() = trailLineEnd - trailLineBegin
            val overallSize: Long
                get() = trailLineEnd - startLineBegin
        }

        var insideRegion = false
        var skipFile = false
        var strictRegionsViolation: Boolean = false
        private val warnings: MutableList<String> = mutableListOf()
        private val messages: MutableList<String> = mutableListOf()
        val hasNoRegions: Boolean
            get() = !hasRegions

        fun addLineNoRegions(line: String) {
            outBuffer.writeUtf8(line).writeUtf8(CRLF)
        }

        fun addLine(line: String) {
            origBuffer.writeUtf8(line).writeUtf8(CRLF)
            if (insideRegion && regionBuffersOrig.isNotEmpty()) {
                regionBuffersOrig.last().writeUtf8(line).writeUtf8(CRLF)
            }
        }
        fun addRegionStart(line: String) {
            val indexBeforeWrite = origBuffer.size
            origBuffer.writeUtf8(line).writeUtf8(CRLF)
            val regionBuffer = Buffer()
            regionBuffersOrig.add(regionBuffer)
            regionBuffer.writeUtf8(line).writeUtf8(CRLF)

            if (regionIndexes.isNotEmpty()) {
                if (regionIndexes.last().trailLineBegin == -1L) {
                    if (hasRegionEndREGiven) {
                        addWarning("has consecutive --region-start lines without --region-end in between at line ${"TBD"}")
                    } else {
                        addWarning("has multiple --region-start lines (preceding region still in effect)")
                    }
                    strictRegionsViolation = true
                } else {
                    // next region
                    // adjust sizeToNextRegionBegin in preceding region
                    regionIndexes.last().sizeToNextRegionBegin = indexBeforeWrite - regionIndexes.last().trailLineEnd
                    regionIndexes.add(Region(indexBeforeWrite, origBuffer.size, -1L, -1L, -1L))
                }
            } else {
                // first region
                regionIndexes.add(Region(indexBeforeWrite, origBuffer.size, -1L, -1L, -1L))
            }
            insideRegion = true
        }
        fun addRegionEnd(line: String) {
            val indexBeforeWrite = origBuffer.size
            origBuffer.writeUtf8(line).writeUtf8(CRLF)
            if (regionIndexes.isEmpty()) {
                if (lenientRegions) {
                    if (hasRegionStartREGiven) {
                        addWarning("detected first --region-end without matching --region-start before it (will treat it as region starting at beginning of file) at line ${"TBD"}")
                    }
                } else {
                    addWarning("detected first --region-end without matching --region-start before it at line ${"TBD"}")
                    strictRegionsViolation = true
                }
                regionIndexes.add(Region(0L, 0L, indexBeforeWrite, origBuffer.size, -1L))
                // copy the already read stuff to region Buffers
                val regionBuffer = Buffer()
                regionBuffersOrig.add(regionBuffer)
                origBuffer.copyTo(regionBuffer, 0L, origBuffer.size)
            } else if (regionIndexes.last().trailLineBegin != -1L) {
                if (! lenientRegions) {
                    addWarning("--region-end without matching --region-start before it at line ${"TBD"}")
                    strictRegionsViolation = true
                } else {
                    addWarning("has multiple --region-end lines")
                }
            } else {
                regionIndexes.last().apply { trailLineBegin = indexBeforeWrite ; trailLineEnd = origBuffer.size }
                regionBuffersOrig.last().writeUtf8(line).writeUtf8(CRLF)
            }
            insideRegion = false
        }
        fun finalizeRegions() {
            if (regionIndexes.isNotEmpty() && regionIndexes.last().trailLineBegin == -1L) {
                if (! lenientRegions) {
                    addWarning("(last) --region-start without a --region-end")
                } else {
                    addWarning("(last) --region-start without a --region-end (will treat as region up to end of file) at line ${"TBD"}")
                    regionIndexes.last().apply { trailLineBegin = origBuffer.size ; trailLineEnd = origBuffer.size }
                }
                strictRegionsViolation = true
            }
        }
        fun finish() {
            origBuffer.close()
            outBuffer.close()
            regionBuffersOrig.forEach { it.close() }
            regionBuffersMod.forEach { it.close() }
        }
        fun addWarning(s: String) {
            warnings.add("$origFilePath: $s")
        }
        val hasWarnings: Boolean
            get() = warnings.isNotEmpty()
        fun addMessage(s: String) {
            messages.add("$origFilePath: $s")
        }
        val hasMessages: Boolean
            get() = messages.isNotEmpty()

        fun echoWarnings() {
            if (hasWarnings) { printlnSTDERR("warnings:") }
            for (warning in warnings) {
                printlnSTDERR("  $warning")
            }
        }
        fun echoMessages() {
            if (hasMessages) { printlnSTDERR("messages:") }
            for (message in messages) {
                printlnSTDERR("  $message")
            }
        }
    } // class ReplaceResult

    override fun run() {
        val fileReplaceResults: List<ReplaceResult> = validateArgs()

        for (rr in fileReplaceResults) {
            // first read complete original file into an okio buffer and remember byte offsets of region start/ends
            FS.source(rr.origFilePath).use { fileSource ->
                fileSource.buffer().use { bufferedFileSource ->
                    var currentLine: String? = bufferedFileSource.readUtf8Line()
                    if (currentLine == null) { rr.addWarning("${rr.origFilePath} did not have any content !!!") }
                    while (currentLine != null) {
                        if (regionStartRE != null && regionStartRE!!.containsMatchIn(currentLine!!)) {
                            rr.addRegionStart(currentLine!!)
                        } else if (regionEndRE != null && regionEndRE!!.containsMatchIn(currentLine!!)) {
                            rr.addRegionEnd(currentLine!!)
                        } else {
                            rr.addLine(currentLine!!)
                        }
                        currentLine = bufferedFileSource.readUtf8Line()
                    }
                    rr.finalizeRegions()
                }
            } // origFilePath read into rr.buffer and regions into rr.regionBuffers

            if ( ! lenientRegions && rr.strictRegionsViolation) {
                rr.addWarning("nothing replaced as non --lenient-regions and region violations detected")
                rr.skipFile = true
            }
            if (noReplacementsInFileIfWarnings && rr.hasWarnings) {
                rr.addWarning("nothing replaced as --ignore-files-with-warnings")
                rr.skipFile = true
            }
            if (rr.regionIndexes.isEmpty() && noReplacementsIfNoRegions) {
                rr.addWarning("nothing replaced as --ignore-files-with-no-regions")
                rr.skipFile = true
            }
            if (rr.skipFile) {
                rr.echoMessages()
                rr.echoWarnings()
                rr.finish()
                continue
            }

            if ( (regionStartRE == null && regionEndRE == null) ) {
                // --replace no regionRE given --> replace in all lines of file
                var currentLine = rr.origBuffer.readUtf8Line()
                while (currentLine != null) {
                    val modifiedLine = applyREs(currentLine!!)
                    rr.addLineNoRegions(modifiedLine)
                    currentLine = rr.origBuffer.readUtf8Line()
                }
            } else {
                // --replace in regions

                // write stuff before first start-region
                if (omitBeforeFirstRegionStart) {
                    // do not write stuff before first region
                    rr.origBuffer.skip(rr.regionIndexes.first().startLineBegin)
                } else {
                    rr.outBuffer.write(rr.origBuffer, rr.regionIndexes.first().startLineBegin)
                }

                // replace stuff inside each region
                for ( (i, region) in rr.regionIndexes.withIndex()) {
                    rr.regionBuffersMod.add(Buffer())
                    if (removeRegion || replaceRegion != null) {
                        // --replace--region and --remove-region instead but no --replace in region
                        if (omitRegionStartLines) {
                            rr.origBuffer.skip(region.startLineSize)
                        } else {
                            rr.origBuffer.copyTo(rr.regionBuffersMod.last(), 0, region.startLineSize)
                            rr.outBuffer.write(rr.origBuffer, region.startLineSize)
                        }
                        rr.origBuffer.skip(region.trailLineBegin - region.startLineEnd) // consume region (w/o sentinels)
                        if (replaceRegion != null) {
                            rr.outBuffer.writeUtf8(replaceRegion!!)
                            rr.regionBuffersMod.last().writeUtf8(replaceRegion!!)
                            if ( ! replaceRegion!!.endsWith("\n")) {
                                rr.outBuffer.writeUtf8(ReplaceResult.CRLF)
                                rr.regionBuffersMod.last().writeUtf8(ReplaceResult.CRLF)
                            }
                        }
                        if (omitRegionEndLines) {
                            rr.origBuffer.skip(region.trailLineSize)
                        } else {
                            rr.origBuffer.copyTo(rr.regionBuffersMod.last(), 0, region.trailLineSize)
                            rr.outBuffer.write(rr.origBuffer, region.trailLineSize)
                        }
                    } else {

                        // normal --replace in region code
                        // eat-up region-start line itself
                        if (omitRegionStartLines) {
                            rr.origBuffer.skip(region.startLineSize)
                        } else {
                            rr.origBuffer.copyTo(rr.regionBuffersMod.last(), 0, region.startLineSize)
                            rr.outBuffer.write(rr.origBuffer, region.startLineSize)

                        }

                        // modify and write current region (after --region-start)
                        var currentLine = rr.origBuffer.readUtf8Line()
                        while (currentLine != null) {
                            if (regionEndRE != null && regionEndRE!!.containsMatchIn(currentLine!!)) {
                                if ( ! omitRegionEndLines) {
                                    rr.regionBuffersMod.last().writeUtf8(currentLine!!).writeUtf8(ReplaceResult.CRLF)
                                    rr.outBuffer.writeUtf8(currentLine!!).writeUtf8(ReplaceResult.CRLF)
                                }
                                break
                            } else {
                                // ==============================================================
                                val modifiedLine = applyREs(currentLine!!)
                                rr.outBuffer.writeUtf8(modifiedLine).writeUtf8(ReplaceResult.CRLF)
                                rr.regionBuffersMod.last().writeUtf8(modifiedLine).writeUtf8(ReplaceResult.CRLF)
                                // ==============================================================
                                currentLine = rr.origBuffer.readUtf8Line()

                            }
                        } // while inside region
                    } // region dealt with ready.

                    if (i+1 == rr.regionIndexes.size) {
                        // this was the last region
                        if (omitAfterLastRegionEnd) {
                            rr.origBuffer.clear()
                        } else {
                            rr.outBuffer.writeAll(rr.origBuffer)
                        }
                    } else {
                        // write stuff up-to before next start-region
                        rr.outBuffer.write(rr.origBuffer, region.sizeToNextRegionBegin)
                    }
                }
            } // else --replace with fully given regions

            if ( ! silent) {
                rr.echoMessages()
                rr.echoWarnings()
                if (verbose) echoInfos(rr)
            }
            if (! atomicWriteAllFiles) {
                writeFile(rr)
                rr.finish()
            }
        } // foreach rr

        if (atomicWriteAllFiles) {
            writeAllFiles(fileReplaceResults)
            for (rr in fileReplaceResults) {
                rr.finish()
            }
        }
    }

    private fun applyREs(currentLine: String): String {
        var modifiedLine1 = currentLine
        for (pair in rplREs) {
            modifiedLine1 = modifiedLine1.replace(pair.first, pair.second) // regex replace in THIS line !!!
            if (!allReplacements && (modifiedLine1 != currentLine)) {
                break // at most ONE given regex should alter the line
            }
        }
        return modifiedLine1
    }

    private fun validateArgs(): MutableList<ReplaceResult> {
        val errors: MutableList<String> = mutableListOf()
        if (args.isEmpty()) {
            printlnSTDERR("no files given!"); throw ProgramResult(1)
        }
        val hasRegions = regionStartOpt.isNotEmpty() || regionEndOpt.isNotEmpty()
        if ( (regionStartOpt.isEmpty() && regionEndOpt.isNotEmpty()) || (regionStartOpt.isNotEmpty() && regionEndOpt.isEmpty()) ) {
            lenientRegions = true
        } else {
            lenientRegions = lenientRegionsOpt
        }
        val fileReplaceResults: MutableList<ReplaceResult> = mutableListOf()
        for (fileString in args) {
            val origFilePath = fileString.toPath()
            val fileMeta = FS.metadataOrNull(origFilePath)
            if ( ! FS.exists(origFilePath)) {
                if ( ! ignoreNonexisting) errors.add(fileString) else printlnSTDERR("$fileString does not exist!")
            } else if (fileMeta?.isDirectory != false) {
                errors.add(if (fileString.endsWith("/") || fileString.endsWith("\\")) fileString else "$fileString/")
            } else {
                fileReplaceResults.add(
                    ReplaceResult(
                        origFilePath,
                        hasRegions,
                        lenientRegions = lenientRegions,
                        hasRegionStartREGiven = regionStartOpt.isNotEmpty(),
                        hasRegionEndREGiven = regionEndOpt.isNotEmpty(),
                    )
                )
            }
        }
        if (errors.isNotEmpty()) {
            printlnSTDERR("given file does not exist or is a folder/dir: ${errors.joinToStringSingleQuoted()}"); throw ProgramResult(2)
        }
        if (removeRegion && replaceRegion != null) { errors.add("--remove-region and --replace-region opts are mutual exclusive!") }
        if ( rplOpts.isNotEmpty() &&   (removeRegion || replaceRegion != null) ) { errors.add("--replace opt is mutual exclusive to either of --remove-region and --replace-region") }
        if ( rplOpts.isEmpty()    && ! (removeRegion || replaceRegion != null) ) { errors.add("nothing to replace given") }
        if ( (removeRegion || replaceRegion != null) && ! hasRegions ) { errors.add("neither --region-start nor --region-end given for --remove-region or --replace-region") }
        if (postfixOpt.isNotBlank() &&   inline) { errors.add("--postfix and --inline/--overwrite are mutual exclusive!") }
        if ( hasRegions && ! lenientRegions && (regionStartOpt.isEmpty() || regionEndOpt.isEmpty()) ) { errors.add("non --lenient-regions needs both of --region-start and --region-end to be defined\n    if you only want to replace after --region-start or only before --region-end then you have to set --lenient-regions") }
        // convert opts and adjust defaults
        if (postfixOpt.isBlank()    && ! inline) postfix = ".replaced" else if ( ! inline ) postfix = postfixOpt
        if (windowsCRLFOpt) { ReplaceResult.CRLF = "\r\n" ; ReplaceResult.CRLFBYTESIZE = run { val b = Buffer(); b.writeUtf8(ReplaceResult.CRLF); b.close(); b.size } }
        regionStartRE = if (regionStartOpt.isNotEmpty()) if (caseInsensitive) regionStartOpt.toRegex(RegexOption.IGNORE_CASE) else regionStartOpt.toRegex() else null
        regionEndRE = if (regionEndOpt.isNotEmpty()) if (caseInsensitive) regionEndOpt.toRegex(RegexOption.IGNORE_CASE) else regionEndOpt.toRegex() else null
        for (reString in rplOpts) {
            val re = if (caseInsensitive) reString.first.toRegex(RegexOption.IGNORE_CASE) else reString.first.toRegex()
            rplREs.add(Pair(re, reString.second))
        }
        // check if any errors, report and exit
        if (errors.isNotEmpty()) {
            printlnSTDERR("problems with given --replace things:${errors.joinToStringSingleQuoted("\n    ")}"); throw ProgramResult(3)
        }
        return fileReplaceResults
    }

    private fun echoAllInfos(fileReplaceResults: List<ReplaceResult>) {
        for (rr in fileReplaceResults) {
            echoInfos(rr)
        }
    }
    private fun echoInfos(rr: ReplaceResult) {
        printlnSTDOUT("${rr.origFilePath}:")
        printlnSTDOUT("${rr.origFilePath}:".replace(".".toRegex(), "=")) // underline
        if (rr.regionIndexes.isEmpty()) {
            printlnSTDERR("  no regions.")
        } else {
            printlnSTDERR("  regions:⌄⌄⌄⌄⌄⌄⌄⌄⌄⌄")
        }
        for ( (index, region) in rr.regionIndexes.withIndex()) {
            printlnSTDOUT("    orig region ${index+1}: $region")
            var s = readFullyToString(rr.regionBuffersOrig[index], true)
            s = s.replace("\n(.)".toRegex(), "\n      $1")
            printlnSTDOUT("      $s")
            printlnSTDOUT("    modified region ${index+1}:")
            s = readFullyToString(rr.regionBuffersMod[index], true)
            s = s.replace("\n(.)".toRegex(), "\n      $1")
            printlnSTDOUT("      $s")
        }
        if (rr.regionIndexes.isNotEmpty()) { printlnSTDERR("  ^^^^^^^^^^") }
    }

    private fun writeAllFiles(fileReplaceResults: List<ReplaceResult>) {
        for (rr in fileReplaceResults) {
            writeFile(rr)
        }
    }
    private fun writeFile(rr: ReplaceResult) {
        if (rr.skipFile) return
        if (stdout) {
            printlnSTDERR(rr.origFilePath.toString())
            printlnSTDERR(rr.origFilePath.toString().replace(".".toRegex(), "=")) // underline
            val byteArray = ByteArray(rr.origBuffer.size.toInt())
            rr.origBuffer.readFully(byteArray)
            printlnSTDOUT(byteArrayToUTF8(byteArray))
        } else {
            if (backup) {
                FS.copy(rr.origFilePath, (rr.origFilePath.toString() + ".backup").toPath())
            }
// HERE            if (regionStartRE != null && rr.regionIndexes.isEmpty()) continue
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
            val written = fileSink.writeAll(rr.outBuffer)
            fileSink.close()
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
