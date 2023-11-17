@file:Import("helpers_ScriptHelpers.kt")

import com.github.ajalt.clikt.core.CliktCommand
import okio.*

var FS: FileSystem = FileSystem.SYSTEM

// some helper functions that are platform specific (kotlin native/multiplatform)
//fun CliktCommand.printlnSTDERR(line: String = "") { printlnSTDERR(line) }
fun printlnSTDERR(line: String = "") { System.err.println(line) }
fun CliktCommand.printlnSTDOUT(line: String = "") { echo(line) }
fun byteArrayToUTF8(byteArray: ByteArray) = byteArray.toString(Charsets.UTF_8)
fun readFullyToString(path: Path): String {
    val bufferedSource = FS.source(path).buffer()
    val outBuffer = Buffer()
    bufferedSource.readAll(outBuffer)
    bufferedSource.close()
    return readFullyToString(outBuffer)
}
fun readFullyToString(source: BufferedSource, copyBuffer: Boolean = false): String {
    val bufferToReadFrom = if (copyBuffer) source.buffer.copy() else source.buffer
    val byteArray = ByteArray(bufferToReadFrom.size.toInt())
    bufferToReadFrom.readFully(byteArray)
    val s = byteArrayToUTF8(byteArray)
    return s
}


// other helpers
public fun CharSequence?.singleQuote() = if (this == null) "null" else if (this.startsWith('\'')) "$this" else "'$this'"
public fun <T> Iterable<T>.joinToStringSingleQuoted(prefix: String = "", limit: Int = -1, truncated: CharSequence = "...", transform: ((T) -> CharSequence)? = null): String {
    val singleQuotedElements = this.map {element: T ->
        when {
            transform != null -> prefix + transform(element).singleQuote()
            element is CharSequence? -> if (element?.startsWith('\'') == true) prefix + element else prefix + element.singleQuote()
            element is Char -> prefix + "'$element'"
            else -> prefix + element.toString().singleQuote()
        }
    }
    return singleQuotedElements.joinTo(StringBuilder(), limit = limit, truncated = truncated).toString()
}
