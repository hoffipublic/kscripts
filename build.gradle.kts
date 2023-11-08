import org.gradle.configurationcache.extensions.serviceOf

plugins {
    kotlin("jvm") version libs.versions.kotlin.asProvider().get()
    id("VersionsUpgradeBuildLogic")
    //id("org.panteleyev.jpackageplugin") version "1.3.1"
    application
}

group = "com.hoffi"
version = "1.0.0"

application {
    mainClass.set("MainKt")
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:${libs.versions.clikt.get()}")
    implementation("com.squareup.okio:okio:${libs.versions.okio.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:${libs.versions.kotlinx.datetime.get()}")
    implementation("io.github.kscripting:kscript-annotations:1.5.0")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:${libs.versions.kotest.asProvider().get()}")
    testImplementation("io.kotest:kotest-assertions-core:${libs.versions.kotest.asProvider().get()}")
    testImplementation("io.kotest:kotest-framework-datatest-jvm:${libs.versions.kotest.asProvider().get()}")
}

tasks.named<JavaExec>("run") {
    // needed if App wants to read from stdin
    standardInput = System.`in`
}

kotlin {
    jvmToolchain(17)
}

val writeProjectDir by tasks.registering {
    doLast {
        File(project.projectDir, "src/main/resources/ProjectPath.txt").writeText(project.projectDir.toString())
    }
}
tasks.named("build") { finalizedBy(writeProjectDir) }

// Helper tasks to speed up things and don't waste time
//=====================================================
// 'c'ompile 'c'ommon
val cc by tasks.registering {
    dependsOn(
        ":compileKotlin",
        ":compileTestKotlin")
}
