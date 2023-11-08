plugins {
    id("kotlinCommonBuildLogic") // local in ROOT/buildLogic/src/main/kotlin/kotlinCommonBuildLogic.gradle.kts
    kotlin("jvm")
    // add gradle plugins here that "automagically should be applied
    // and then in YOUR build.gradle.kts reference:  plugins { id(<thisFileName>) }
}

//apply(from = "${project.projectDir}/${"buildLogic"}/src/main/kotlin/BuildLogicGlobalCommon.gradle.kts")

// you may configure stuff of plugins that you imported above

// as io.insert-koin:koin-test imported older kotlin version test stuff
configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("org.jetbrains.kotlin:kotlin-test-framework-impl") {
        selectHighestVersion()
    }
}

kotlin {
    jvmToolchain(BuildLogicGlobal.jdkVersion)
    tasks.withType<Test>().configureEach {
        buildLogicJvmTestConfig()
    }
}
afterEvaluate {
    tasks.withType<JavaExec>() {// e.g.: run
        // needed if App wants to read from stdin
        standardInput = System.`in`
    }
}

//task("testUnitTest") {
//    dependsOn("test")
//}

dependencies {
    // versions file: ROOT/buildLogic/libs.versions.toml

}
