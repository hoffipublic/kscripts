package buildLogic.plugins.projectInfosBuildLogicPlugin

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.configurationcache.extensions.serviceOf
import org.gradle.internal.logging.text.StyledTextOutput

class ProjectInfosBuildLogicPlugin : Plugin<Project> {
    override fun apply(targetProject: Project) {
        var projectInfosBuildLogicPluginExtension: ProjectInfosBuildLogicPluginExtension = targetProject.extensions
            .create("projectInfosBuildLogic", ProjectInfosBuildLogicPluginExtension::class.java)
        projectInfosBuildLogicPluginExtension.reportGradleVersion =  targetProject.objects.property(Boolean::class.java)
        projectInfosBuildLogicPluginExtension.reportGradleVersion.set(true) // default value
        projectInfosBuildLogicPluginExtension.reportKotlinVersion =  targetProject.objects.property(Boolean::class.java)
        projectInfosBuildLogicPluginExtension.reportKotlinVersion.set(true) // default value
        projectInfosBuildLogicPluginExtension.reportJvmVersion =  targetProject.objects.property(Boolean::class.java)
        projectInfosBuildLogicPluginExtension.reportJvmVersion.set(true) // default value
        projectInfosBuildLogicPluginExtension.reportSourceCompatibility =  targetProject.objects.property(Boolean::class.java)
        projectInfosBuildLogicPluginExtension.reportSourceCompatibility.set(true) // default value
        projectInfosBuildLogicPluginExtension.reportTargetCompatibility =  targetProject.objects.property(Boolean::class.java)
        projectInfosBuildLogicPluginExtension.reportTargetCompatibility.set(true) // default value
        projectInfosBuildLogicPluginExtension.reportSomeSelectedJarVersions =  targetProject.objects.property(Boolean::class.java)
        projectInfosBuildLogicPluginExtension.reportSomeSelectedJarVersions.set(true) // default value
        projectInfosBuildLogicPluginExtension.reportJarVersionsNameRegex =  targetProject.objects.property(String::class.java)
        projectInfosBuildLogicPluginExtension.reportJarVersionsNameRegex.set("^(spring-cloud-starter|spring-boot-starter|micronaut-core|kotlin-stdlib-jdk[0-9-]+|foundation-desktop)-[0-9].*\$") // default value
        projectInfosBuildLogicPluginExtension.onlyJarVersionNamesByRegex =  targetProject.objects.property(Boolean::class.java)
        projectInfosBuildLogicPluginExtension.onlyJarVersionNamesByRegex.set(false) // default value
        val versionsPrintTaskProvider = targetProject.tasks.register(
            /* name = */ "versionsPrint", /* type = */ VersionsPrintTask::class.java) {
            it.group = "buildLogic"
            it.description = "log projects key versions (gradle java, kotlin, compatibilities, key-jars"
            it.reportGradleVersion.set(projectInfosBuildLogicPluginExtension.reportGradleVersion.get())
            it.reportKotlinVersion.set(projectInfosBuildLogicPluginExtension.reportKotlinVersion.get())
            it.reportJvmVersion.set(projectInfosBuildLogicPluginExtension.reportJvmVersion.get())
            it.reportSourceCompatibility.set(projectInfosBuildLogicPluginExtension.reportSourceCompatibility.get())
            it.reportTargetCompatibility.set(projectInfosBuildLogicPluginExtension.reportTargetCompatibility.get())
            it.reportSomeSelectedJarVersions.set(projectInfosBuildLogicPluginExtension.reportSomeSelectedJarVersions.get())
            it.reportJarVersionsNameRegex.set(projectInfosBuildLogicPluginExtension.reportJarVersionsNameRegex.get())
            it.onlyJarVersionNamesByRegex.set(projectInfosBuildLogicPluginExtension.onlyJarVersionNamesByRegex.get())
        }
        val printClasspathTaskProvider = targetProject.tasks.register(
            /* name = */ "printClasspath", /* type = */ PrintClasspathTask::class.java) {
            it.group = "buildLogic"
            it.description = "print classpath of project to stdout"
        }
    }
}

interface ProjectInfosBuildLogicPluginExtension { // @Inject constructor(objects: ObjectFactory) {
    var reportGradleVersion: Property<Boolean>
    var reportKotlinVersion: Property<Boolean>
    var reportJvmVersion: Property<Boolean>
    var reportSourceCompatibility: Property<Boolean>
    var reportTargetCompatibility: Property<Boolean>
    var reportSomeSelectedJarVersions: Property<Boolean>
    var reportJarVersionsNameRegex: Property<String>
    var onlyJarVersionNamesByRegex: Property<Boolean>
}

internal abstract class VersionsPrintTask : DefaultTask() {
    @get:Input abstract val reportGradleVersion: Property<Boolean>
    @get:Input abstract val reportKotlinVersion: Property<Boolean>
    @get:Input abstract val reportJvmVersion: Property<Boolean>
    @get:Input abstract val reportSourceCompatibility: Property<Boolean>
    @get:Input abstract val reportTargetCompatibility: Property<Boolean>
    @get:Input abstract val reportSomeSelectedJarVersions: Property<Boolean>
    @get:Input abstract val reportJarVersionsNameRegex: Property<String>
    @get:Input abstract val onlyJarVersionNamesByRegex: Property<Boolean>

    //@get:InputDirectory
    //abstract val rootProject: DirectoryProperty
    //
    //@get:OutputDirectory
    //abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        //val kotlin = project.extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)!!
        val kotlin = project.extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension::class.java)
        val java = project.extensions.findByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
        val foreground = ConsoleColor.YELLOW
        val background = ConsoleColor.DEFAULT
        if (reportGradleVersion.get() && ! onlyJarVersionNamesByRegex.get()) {
            printlnColor(foreground, "Gradle version: " + project.gradle.gradleVersion, background)
        }
        if (reportKotlinVersion.get() && ! onlyJarVersionNamesByRegex.get()) {
            if (kotlin != null) printlnColor(foreground, "Kotlin version: " + kotlin.coreLibrariesVersion) else printlnColor(foreground, "Kotlin version: rootProject not a kotlin project")
        }
        if (java != null) {
            if (reportJvmVersion.get() && ! onlyJarVersionNamesByRegex.get()) {
                printlnColor(foreground, "javac  version: " + org.gradle.internal.jvm.Jvm.current(), background) // + " with compiler args: " + options.compilerArgs, backgroundColor = ConsoleColor.DARK_GRAY)
            }
            if (reportSourceCompatibility.get() && ! onlyJarVersionNamesByRegex.get()) {
                printlnColor(foreground, "       srcComp: " + java.sourceCompatibility, background)
            }
            if (reportTargetCompatibility.get() && ! onlyJarVersionNamesByRegex.get()) {
                printlnColor(foreground, "       tgtComp: " + java.targetCompatibility, background)
            }
        } else println("javac  version: rootProject not a jvm    project")
        if (reportSomeSelectedJarVersions.get()) {
            printlnColor(foreground, "versions of assorted core dependencies:", background)
            val regex = Regex(pattern = reportJarVersionsNameRegex.get())
            project.pluginManager.let { prjPluginManager -> when {
                prjPluginManager.hasPlugin("org.jetbrains.kotlin.jvm") -> {
                    if (project.subprojects.size > 0) {
                        project.configurations.getByName("compileClasspath").files.map { it.nameWithoutExtension }.filter { it.matches(regex) }
                            .forEach { printlnColor(foreground, String.format("%-25s: %s", project.name, it), background) }
                    } else {
                        project.configurations.getByName("compileClasspath").files.map { it.nameWithoutExtension }.filter { it.matches(regex) }
                            .forEach { printlnColor(foreground, "  $it", background) }
                    }
                }
                prjPluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform") -> {
                    if (project.subprojects.size > 0) {
                        project.configurations.getByName("jvmCompileClasspath").files.map { it.nameWithoutExtension }.filter { it.matches(regex) }
                            .forEach { printlnColor(foreground, String.format("%-25s: %s", project.name, it), background) }
                    } else {
                        project.configurations.getByName("jvmCompileClasspath").files.map { it.nameWithoutExtension }.filter { it.matches(regex) }
                            .forEach { printlnColor(foreground, "  $it", background) }
                    }
                }
            }}
        }
    }
}

internal abstract class PrintClasspathTask : DefaultTask() {
    @TaskAction
    fun run() {
        project.pluginManager.let { prjPluginManager -> when {
            prjPluginManager.hasPlugin("org.jetbrains.kotlin.jvm") -> {
                // filters only existing and non-empty dirs
                project.configurations.getByName("runtimeClasspath").files
                    .filter { it.isDirectory && (it?.listFiles()?.isNotEmpty() ?: false) || it.isFile }
                    .forEach { println(it) }
            }
            prjPluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform") -> {
                val targets = listOf(
                    "jvmRuntimeClasspath",
                    // "kotlinCompilerClasspath"
                )
                targets.forEach { targetConfiguration ->
                    println("$targetConfiguration:")
                    println("=".repeat("$targetConfiguration:".length))
                    project.configurations
                        .getByName(targetConfiguration).files
                        // filters only existing and non-empty dirs
                        .filter { it.isDirectory && (it?.listFiles()?.isNotEmpty() ?: false) || it.isFile }
                        .forEach { println(it) }
                }
            }
        } }
    }
}

// colored console output
val ESCAPE = '\u001B'
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
    val foreground: String = "$ESCAPE[${baseCode}m"
    /** ANSI modifier string to apply the color the text's background */
    val background: String = "$ESCAPE[${baseCode + 10}m"
}

internal object Color {
    val RESET = "$ESCAPE[0m"

    fun foreground(string: String, color: ConsoleColor) = color(string, color.foreground)
    fun background(string: String, color: ConsoleColor) = color(string, color.background)
    private fun color(string: String, ansiString: String) = "$ansiString$string$RESET"
}
/** appearing on Solarized color theme like:
 * style Normal:
 * style Header:         white
 * style UserInput:      white
 * style Identifier:     yellow
 * style Description:    orange
 * style ProgressStatus: orange
 * style Success:        yellow
 * style SuccessHeader:  DarkGray
 * style Failure:        red
 * style FailureHeader:  LightRed
 * style Info:           orange
 *
 *         printlnColor(project, org.gradle.internal.logging.text.StyledTextOutput.Style.Failure, "some given output in Failure color")
 */
fun printlnColor(project: Project, style: StyledTextOutput.Style, s: String) {
    // import org.gradle.configurationcache.extensions.serviceOf
    // import org.gradle.internal.logging.text.StyledTextOutput
    val out = project.serviceOf<org.gradle.internal.logging.text.StyledTextOutputFactory>().create("an-output")
    out.style(style).println(s)
}

/**
 * printlnColor(ConsoleColor.GREEN, "some given colored output")
 */
fun printlnColor(color: ConsoleColor, s: String, backgroundColor: ConsoleColor = ConsoleColor.DEFAULT) = printColor(color, "$s\n", backgroundColor)
fun printColor(color: ConsoleColor, s: String, backgroundColor: ConsoleColor = ConsoleColor.DEFAULT) {
    print(colorString(color, s, backgroundColor))
}
fun colorString(color: ConsoleColor, s: String, backgroundColor: ConsoleColor = ConsoleColor.DEFAULT) : String {
    return when (backgroundColor) {
        ConsoleColor.DEFAULT -> Color.foreground(s, color)
        else -> Color.foreground(Color.background(s, backgroundColor), color)
    }
}
