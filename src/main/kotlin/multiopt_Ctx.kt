import com.github.ajalt.clikt.core.CliktCommand

/** making sure that any root's context obj (which should be a Set of the sealed class AContextValue)
 * can only be in that Set ONCE for any of these sealed classes */
abstract class ACtxValEqualsHashCode {
    //    abstract override fun equals(other: Any?): Boolean
//    abstract override fun hashCode(): Int
    override fun hashCode(): Int = this::class.simpleName.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AContextValue) return false
        return hashCode() == other.hashCode()
    }

}
/** CliktCommand.currentContext.obj is a MutableSet containing at most ONE of each of these */
sealed class AContextValue : ACtxValEqualsHashCode() {
    class Users : AContextValue() { val userOptGroups: MutableMap<String, UserOptGroup> = mutableMapOf() }
}
/**
 * BEWARE!!! the returned instance might not be the same as the passed in one
 * making sure that:<br/>
 * a) root-CliktCommand's context obj is != null and a `MutableSet<AContextValue>`<br/>
 * b) the Set contains a given AContextValue at most once
 */
inline fun <reified T : AContextValue> CliktCommand.getOrSetInRootContextObj(protoInstance: T): T {
    val rootCtx = currentContext.findRoot()
    val rootCtxObj: MutableSet<AContextValue> = if (rootCtx.obj == null) {
        mutableSetOf<AContextValue>().also { rootCtx.obj = it }
    } else {
        @Suppress("UNCHECKED_CAST")
        rootCtx.obj as MutableSet<AContextValue>
    }
    return if( rootCtxObj.add(protoInstance) ) protoInstance else rootCtxObj.first { it.hashCode() == protoInstance.hashCode() } as T
}
