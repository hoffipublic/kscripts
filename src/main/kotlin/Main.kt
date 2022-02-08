
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection


fun main(args: Array<String>) {
    val REParOpen = "[{(\\[]"
    val REParClos = "[})\\]]"
    val RECombineParsOpen = Regex("(?<=$REParOpen)\\s*\\R\\s*(?=$REParOpen)", RegexOption.MULTILINE)
    val RECombineParsClos = Regex("(?<=$REParClos)\\s*\\R\\s*(?=$REParClos)", RegexOption.MULTILINE)
    val RECompressCurlies = Regex("},\\s*\\R\\s+\\{", RegexOption.MULTILINE)

    val clipboard: Clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
    //val s = clipboard.getData(DataFlavor.getTextPlainUnicodeFlavor()) as String
    var s = clipboard.getData(DataFlavor.stringFlavor) as String

    s = s.replace(RECombineParsOpen, "") // combine opening parenthesises on separate lines
    s = s.replace(RECombineParsClos, "") // same on closing ones
    s = s.replace(RECompressCurlies, "},{")

    clipboard.setContents(StringSelection(s),null)
}
