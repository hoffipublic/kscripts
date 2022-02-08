import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.panteleyev.jpackageplugin") version "1.3.1"
    application
}

group = "com.hoffi"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

application {
    mainClass.set("MainKt")
}

//task("copyDependencies", Copy::class) {
//    from(configurations.runtimeClasspath).into("$buildDir/jmods")
//}
//
//task("copyJar", Copy::class) {
//    from(tasks.jar).into("$buildDir/jmods")
//}

//tasks.jpackage {
////    dependsOn("build", "copyDependencies", "copyJar")
//    dependsOn("build")
//
//    appName = "kprettyjson"
//    appVersion = project.version.toString()
//    vendor = "Hoffi"
//    copyright = "Copyright (c) 2022 Hoffi"
//    //runtimeImage = System.getProperty("java.home")
//    //module = "org.app.module/org.app.MainClass"
//    destination = "$buildDir/distributions"
//    javaOptions = listOf("-Dfile.encoding=UTF-8")
//
////    mac {
////        icon = "icons/icons.icns"
////    }
////
////    windows {
////        icon = "icons/icons.ico"
////        winMenu = true
////        winDirChooser = true
////    }
//}
