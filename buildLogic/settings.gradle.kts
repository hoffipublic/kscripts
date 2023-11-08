rootProject.name = "buildLogic"

pluginManagement {
    //includeBuild("buildLogic") // TODO uncomment in your toplevel rootProject/settings.gradle.kts (not here(!) as that would be a recursion)
    // special case of included builds are builds that define Gradle plugins.
    // These builds should be included using the includeBuild statement inside the pluginManagement {} block of the settings file.
    // Using this mechanism, the included build may also contribute a settings plugin that can be applied in the settings file itself.
    // ===================================================================================
    // THESE INCLUDES will NOT(!!!) propagate the plugin to be defined in the ROOT project
    // BUT ONLY for this buildLogic/ sub-composite-build
    // ===================================================================================
    //// TO USE SOME OF THESE in your Project, you have to import it in settings.gradle.kts of THAT project
    //// via pluginManagement { includeBuild("buildLogic/binaryPlugins/ProjectSetupBuildLogicPlugin") }
    includeBuild("binaryPlugins/ProjectInfosBuildLogicPlugin")
    includeBuild("binaryPlugins/ProjectSetupBuildLogicPlugin")
    repositories {
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // FAIL_ON_PROJECT_REPOS or PREFER_PROJECT or PREFER_SETTINGS)
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    versionCatalogs {
        //println("buildLogic/settings.gradle.kts searching for libs.versions.toml")
        create("libs") {
            if (rootProject.name == "buildLogic") {
                from(files(File(rootProject.projectDir, "libs.versions.toml"))) // that's where libs.versions.toml is located in the standalone master buildLogic git repo project))
            } else {
                from(files(File(rootProject.projectDir, "buildLogic/libs.versions.toml"))) // this is the standard case
            }
        }
    }
}
