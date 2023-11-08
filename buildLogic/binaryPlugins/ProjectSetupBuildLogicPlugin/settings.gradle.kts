rootProject.name = "ProjectSetupBuildLogicPlugin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
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
        create("libs") {
            from(files(File(rootProject.projectDir, "../../libs.versions.toml"))) // buildLogic/libs.versions.toml
        }
    }
}
