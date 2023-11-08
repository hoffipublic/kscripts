import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
private fun VersionCatalog.findVersionOrThrow(name: String) =
    findVersion(name)
        .orElseThrow { NoSuchElementException("Version $name not found in version catalog") }
        .requiredVersion
private fun VersionCatalog.findLibraryOrThrow(name: String) =
    findLibrary(name)
        .orElseThrow { NoSuchElementException("Library $name not found in version catalog") }

internal val VersionCatalog.jdkVersion: String
    get() = findVersionOrThrow("jdkVersion")
internal val VersionCatalog.kotlinVersion: String
    get() = findVersionOrThrow("kotlin")
internal val VersionCatalog.composeVersion: String
    get() = findVersionOrThrow("compose")

internal val VersionCatalog.versionsPlugin: String
    get() = findVersionOrThrow("gradle-versions-plugin")
internal val VersionCatalog.versionsCatalogUpdate: String
    get() = findVersionOrThrow("gradle-versions-catalogUpdate")
internal val VersionCatalog.versionsFilter: String
    get() = findVersionOrThrow("gradle-versions-filter")
