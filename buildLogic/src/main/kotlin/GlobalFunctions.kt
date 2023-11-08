import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import java.io.File

// ============================================================================
// ===       global functions and extension functions                      ====
// ============================================================================
fun <T> ProviderConvertible<T>.v() = this.asProvider().get()
fun <T> Provider<T>.v(): T = get()

/** no project object known in e.g. in settings.gradle.kts */
fun ProjectDescriptor.libsVersionsTomlFile(): File = libsVersionsTomlFile(this.name, this.projectDir)
fun Project.libsVersionsTomlFile(): File = libsVersionsTomlFile(rootProject.name, rootProject.projectDir)
private fun libsVersionsTomlFile(rootProjectName: String, rootProjectDir: File): File {
    var standaloneBuildLogicProject = false
    val tomlFile = if ( (rootProjectName == "buildLogic") && (File(rootProjectDir.parentFile, "settings.gradle.kts").exists()) ) {
        // "normal" buildLogic/ subfolder of a gradle multiproject (as parent also has a settings.gradle.kts file)
        File(rootProjectDir, "../libs.versions.toml").canonicalFile // using file of rootProject this buildLogic composite build is in
    } else {
        // standalone/reference buildLogic/ project
        standaloneBuildLogicProject = true
        println("-> ${rootProjectName} of standalone project buildLogic")
        File(rootProjectDir, "libs.versions.toml")
    }
    if (tomlFile.exists()) {
        if ( rootProjectDir.parentFile.name == "binaryPlugins" ) {
            println("-> ${rootProjectName}: buildLogic/binaryPlugins/${rootProjectName}/settings.gradle.kts using versions of \n\t\t'$tomlFile'")
        } else if (standaloneBuildLogicProject) {
            println("-> ${rootProjectName}: ${rootProjectName}/settings.gradle.kts using versions of \n\t\t'$tomlFile'")
        } else {
            println("-> ${rootProjectName}: ${rootProjectName}/settings.gradle.kts using versions of \n\t\t'$tomlFile'")
        }
    } else {
        if ( rootProjectDir.parentFile.name == "binaryPlugins" ) {
            throw GradleException("${rootProjectName}: buildLogic/binaryPlugins/${rootProjectName}/settings.gradle.kts did not find version information file '$tomlFile'")
        } else {
            throw GradleException("${rootProjectName}: ${rootProjectName}/settings.gradle.kts did not find version information file '$tomlFile'")
        }
    }
    return tomlFile
}
