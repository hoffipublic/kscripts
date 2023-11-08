plugins {
    id("kotlinCommonBuildLogic") // local in ROOT/buildLogic/src/main/kotlin/kotlinCommonBuildLogic.gradle.kts
    kotlin("multiplatform")
    // add gradle plugins here that "automagically should be applied
    // and then in YOUR build.gradle.kts reference:  plugins { id(<thisFileName>) }
}

// you may configure stuff of plugins that you imported above

kotlin {
    jvmToolchain(BuildLogicGlobal.jdkVersion)
    tasks.withType<Test>().configureEach {
        buildLogicJvmTestConfig()
    }
    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
        buildLogicCommonTestConfig("NATIVE")
        // listen to standard out and standard error of the test JVM(s)
        // onOutput { descriptor, event -> logger.lifecycle("Test: " + descriptor + " produced standard out/err: " + event.message ) }
    }

}

//task("testUnitTest") {
//    dependsOn("test")
//}

dependencies {
    // versions file: ROOT/buildLogic/libs.versions.toml

}

