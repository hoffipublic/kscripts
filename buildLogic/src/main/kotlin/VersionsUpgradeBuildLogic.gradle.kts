// only to be applied to ROOT projects and not(!) to multiproject subprojects!
plugins {
    id("com.github.ben-manes.versions")
    id("nl.littlerobots.version-catalog-update")
    id("se.ascp.gradle.gradle-versions-filter")
}

versionCatalogUpdate {
    if (rootProject.name == "buildLogic") {
        catalogFile.set(File(rootProject.projectDir, "libs.versions.toml")) // that's where libs.versions.toml is located in the standalone master buildLogic git repo project
    } else {
        catalogFile.set(File(rootProject.projectDir, "buildLogic/libs.versions.toml")) // this is the standard case
    }
    keep {
        keepUnusedVersions.set(true)
        keepUnusedLibraries.set(true)
        keepUnusedPlugins.set(true)
    }
}
versionsFilter {
    outPutFormatter.set("json,plain") // you at least need "json"
    strategy.set(se.ascp.gradle.Strategy.EXCLUSIVE)
    exclusiveQualifiers.addAll("beta","rc","cr","m","preview","b" )
    checkForGradleUpdate.set(true)
    log.set(false)
}
