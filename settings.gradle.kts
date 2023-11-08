
rootProject.name = "kscripts"

pluginManagement {
    includeBuild("buildLogic")
    //// special case of included builds are builds that define Gradle plugins.
    //// These builds should be included using the includeBuild statement inside the pluginManagement {} block of the settings file.
    //// Using this mechanism, the included build may also contribute a settings plugin that can be applied in the settings file itself.
    //includeBuild("buildLogic/binaryPlugins/ProjectInfosBuildLogicPlugin")
    //includeBuild("buildLogic/binaryPlugins/ProjectSetupBuildLogicPlugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // FAIL_ON_PROJECT_REPOS or PREFER_PROJECT or PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            if (rootProject.name == "buildLogic") {
                from(files(File(rootProject.projectDir, "libs.versions.toml"))) // that's where libs.versions.toml is located in the standalone master buildLogic git repo project))
            } else {
                from(files(File(rootProject.projectDir, "buildLogic/libs.versions.toml"))) // this is the standard case
            }
        }
    }
}
