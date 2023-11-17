import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem

fun newFakeFileSystem(): FakeFileSystem {
    val FFS = FakeFileSystem()
    FS = FFS
    return FFS
}
