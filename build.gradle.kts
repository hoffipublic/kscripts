import org.gradle.configurationcache.extensions.serviceOf

plugins {
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    //id("org.panteleyev.jpackageplugin") version "1.3.1"
    application
}

group = "com.hoffi"
version = "1.0.0"

repositories {
    mavenCentral()
}

application {
    mainClass.set("MainKt")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")
    implementation("com.github.ajalt.clikt:clikt:3.4.0")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.1.0")
    testImplementation("io.kotest:kotest-assertions-core:5.1.0")
    testImplementation("io.kotest:kotest-framework-datatest-jvm:5.1.0")
}

tasks.named<JavaExec>("run") {
    // needed if App wants to read from stdin
    standardInput = System.`in`
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Test> {
    // classpath += developmentOnly

    useJUnitPlatform {
        //includeEngines("junit-jupiter", "spek2")
        // includeTags "fast"
        // excludeTags "app", "integration", "messaging", "slow", "trivial"
    }
    failFast = false
    ignoreFailures = false
    // reports.html.isEnabled = true

    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
        ) //, STARTED //, standardOut, standardError)
    }

    addTestListener(object : TestListener {
        override fun beforeTest(descriptor: TestDescriptor?) {
            logger.lifecycle("Running $descriptor")
        }

        override fun beforeSuite(p0: TestDescriptor?) = Unit
        override fun afterTest(desc: TestDescriptor, result: TestResult) = Unit
        override fun afterSuite(desc: TestDescriptor, result: TestResult) {
            if (desc.parent == null) { // will match the outermost suite
                println("\nTotal Test Results:")
                println("===================")
                val failsDefault = "${result.failedTestCount} failures"
                val fails =
                    if (result.failedTestCount > 0) colorString(ConsoleColor.RED, failsDefault) else failsDefault
                val outcome = if (result.resultType.name == "FAILURE") colorString(
                    ConsoleColor.RED,
                    result.resultType.name
                ) else colorString(ConsoleColor.GREEN, result.resultType.name)
                println("Test Results: $outcome (total: ${result.testCount} tests, ${result.successfulTestCount} successes, $fails, ${result.skippedTestCount} skipped)\n")
            }
        }
    })
    // listen to standard out and standard error of the test JVM(s)
    // onOutput { descriptor, event -> logger.lifecycle("Test: " + descriptor + " produced standard out/err: " + event.message ) }
}


// Helper tasks to speed up things and don't waste time
//=====================================================
// 'c'ompile 'c'ommon
val cc by tasks.registering {
    dependsOn(
        ":compileKotlin",
        ":compileTestKotlin")
}

// ################################################################################################
// #####    pure informational stuff on stdout    #################################################
// ################################################################################################
//tasks.register<CheckVersionsTask>("checkVersions") // implemented in buildSrc/src/main/kotlin/Deps.kt
//tasks.register("printClasspath") {
//    group = "misc"
//    description = "print classpath"
//    doLast {
//        // filters only existing and non-empty dirs
//        project.configurations.getByName("runtimeClasspath").files
//            .filter { it.isDirectory && (it?.listFiles()?.isNotEmpty() ?: false) || it.isFile }
//            .forEach{ println(it) }
//    }
//}
tasks.register("versionsPrint") {
    group = "misc"
    description = "extract spring boot versions from dependency jars"
    doLast {
        val foreground = ConsoleColor.YELLOW
        val background = ConsoleColor.DEFAULT
        val shadowJar by tasks.getting(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class)
        printlnColor(foreground, "  fat/uber jar: build/lib/${shadowJar.archiveFileName.get()}", background)
        printlnColor(foreground, "Gradle version: " + project.gradle.gradleVersion, background)
        printColor(foreground, "Kotlin version: " + kotlin.coreLibrariesVersion)
        println()
        printlnColor(foreground, "javac  version: " + org.gradle.internal.jvm.Jvm.current(), background) // + " with compiler args: " + options.compilerArgs, backgroundColor = ConsoleColor.DARK_GRAY)
        printlnColor(foreground, "       srcComp: " + java.sourceCompatibility, background)
        printlnColor(foreground, "       tgtComp: " + java.targetCompatibility, background)
        printlnColor(foreground, "versions of core dependencies:", background)
        val regex = Regex(pattern = "^(spring-cloud-starter|spring-boot-starter|micronaut-core|kotlin-stdlib-jdk[0-9-]+|foundation-desktop)-[0-9].*$")
        if (subprojects.size > 0) {
            configurations.compileClasspath.get().map { it.nameWithoutExtension }.filter { it.matches(regex) }
                .forEach { printlnColor(foreground, String.format("%-25s: %s", project.name, it), background) }
        } else {
            configurations.compileClasspath.get().map { it.nameWithoutExtension }.filter { it.matches(regex) }
                .forEach { printlnColor(foreground, "  $it", background) }
        }
    }
}
val build by tasks.existing {
    val versionsPrint by tasks.existing
    finalizedBy(versionsPrint)
}

fun colorString(color: ConsoleColor, s: String, backgroundColor: ConsoleColor = ConsoleColor.DEFAULT) : String {
    return when (backgroundColor) {
        ConsoleColor.DEFAULT -> Color.foreground(s, color)
        else -> Color.foreground(Color.background(s, backgroundColor), color)
    }
}
enum class ConsoleColor(baseCode: Int) {
    BLACK(30),
    RED(31),
    GREEN(32),
    YELLOW(33),
    BLUE(34),
    MAGENTA(35),
    CYAN(36),
    LIGHT_GRAY(37),

    DARK_GRAY(90),
    LIGHT_RED(91),
    LIGHT_GREEN(92),
    LIGHT_YELLOW(93),
    LIGHT_BLUE(94),
    LIGHT_MAGENTA(95),
    LIGHT_CYAN(96),
    WHITE(97),

    DEFAULT(-1);

    /** ANSI modifier string to apply the color to the text itself */
    val foreground: String = "\u001B[${baseCode}m"
    /** ANSI modifier string to apply the color the text's background */
    val background: String = "\u001B[${baseCode + 10}m"
}

internal object Color {
    val RESET = "\u001B[0m"
    fun foreground(string: String, color: ConsoleColor) = color(string, color.foreground)
    fun background(string: String, color: ConsoleColor) = color(string, color.background)
    private fun color(string: String, ansiString: String) = "$ansiString$string$RESET"
}
/**
 * printlnColor(ConsoleColor.GREEN, "some given colored output")
 */
fun printlnColor(color: ConsoleColor, s: String, backgroundColor: ConsoleColor = ConsoleColor.DEFAULT) = printColor(color, "$s\n", backgroundColor)
fun printColor(color: ConsoleColor, s: String, backgroundColor: ConsoleColor = ConsoleColor.DEFAULT) {
    print(colorString(color, s, backgroundColor))
}
fun printlnColor(project: Project, style: org.gradle.internal.logging.text.StyledTextOutput.Style, s: String) {
    // import org.gradle.configurationcache.extensions.serviceOf
    // import org.gradle.internal.logging.text.StyledTextOutput
    val out = project.serviceOf<org.gradle.internal.logging.text.StyledTextOutputFactory>().create("an-output")
    out.style(style).println(s)
}


