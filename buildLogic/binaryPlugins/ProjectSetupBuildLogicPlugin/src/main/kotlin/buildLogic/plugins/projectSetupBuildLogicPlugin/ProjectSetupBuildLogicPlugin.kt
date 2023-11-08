package buildLogic.plugins.projectSetupBuildLogicPlugin

import org.gradle.api.*
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

class ProjectSetupBuildLogicPlugin : Plugin<Project> {
    override fun apply(targetProject: Project) {
        if (targetProject != targetProject.rootProject) { throw GradleException("only the rootProject should have 'ProjectSetupBuildLogicPlugin' applied!") }
        var projectSetupBuildLogicPluginExtension: ProjectSetupBuildLogicPluginExtension = targetProject.extensions
            .create("projectSetupBuildLogic", ProjectSetupBuildLogicPluginExtension::class.java)
        projectSetupBuildLogicPluginExtension.basePackage =  targetProject.objects.property(String::class.java)
        projectSetupBuildLogicPluginExtension.basePackage.set("") // default value
        val createSrcBasePackagesTaskProvider = targetProject.tasks.register(
            /* name = */ "createSrcBasePackages", /* type = */ CreateSrcBasePackagesTask::class.java) {
            it.group = "buildLogic"
            it.description = "create default kotlin src/(main|test)/(kotlin/resources)/project/base/package folders"
            it.basePackage.set(projectSetupBuildLogicPluginExtension.basePackage.get())
        }
        val createIntellijScopeSentinelsProvider = targetProject.tasks.register(
            /* name = */ "createIntellijScopeSentinels", /* type = */ CreateIntellijScopeSentinelsTask::class.java) {
            it.group = "buildLogic"
            it.description = "creating `01__PRJNAME/.gitkeep` and `ZZ__PRJNAME` files in each kotlin mpp project,\n" +
                    "as well as `_srcModule_PRJNAME/.gitkeep` and `ZZsrcModule_PRJNAME` files in each main sourceSets of these"
        }
        val setupProvider = targetProject.tasks.register(
            /* name = */ "setup", /* type = */ Setup::class.java) {
            it.group = "buildLogic"
            it.description= " combines createSrcBasePackages and createIntellijScopeSentinels"
        }
        if (targetProject.plugins.hasPlugin("java")) {
            // ...
        }
    }
}

interface ProjectSetupBuildLogicPluginExtension { // @Inject constructor(objects: ObjectFactory) {
    var basePackage: Property<String>
}

internal abstract class Setup : DefaultTask() {
    @TaskAction
    fun run() {
        val createSrcBasePackages: CreateSrcBasePackagesTask = project.tasks.getByName("createSrcBasePackages") as CreateSrcBasePackagesTask
        val createIntellijScopeSentinels: CreateIntellijScopeSentinelsTask = project.tasks.getByName("createIntellijScopeSentinels") as CreateIntellijScopeSentinelsTask
        createSrcBasePackages.run()
        createIntellijScopeSentinels.run()
    }
}


internal abstract class CreateSrcBasePackagesTask : DefaultTask() {
    @get:Input
    abstract val basePackage: Property<String>

    //@get:InputDirectory
    //abstract val rootProject: DirectoryProperty
    //
    //@get:OutputDirectory
    //abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        println("======================================================================")
        println("==============    CreateSrcBasePackagesTask    =======================")
        println("======================================================================")
        println("basePackage: '${basePackage.get()}'")
        println("======================================================================")
        if (basePackage.get().isBlank()) { logger.warn("no 'projectSetupBuildLogic { basePackage.set(\"some.base.package\") }' set in rootProject") ; return }
        if (project != project.rootProject) { throw GradleException("only the rootProject should have 'ProjectSetupBuildLogicPlugin' applied!") }

        var relPackagePath = basePackage.get().split('.').joinToString("/")
        project.allprojects.forEach { prj ->
            var relProjectDirString = prj.projectDir.toString().removePrefix(project.rootProject.projectDir.toString())
            relProjectDirString = if (relProjectDirString.isNotBlank()) relProjectDirString.removePrefix("/") else "ROOT"
            println("  in project: '${relProjectDirString}' ...")

            val projectPackageDirString = if (relProjectDirString == "ROOT") {
                relPackagePath
            } else {
                "${relPackagePath}/${prj.name.lowercase().replace("_", "").replace("-", "")}"
            }

            prj.pluginManager.let { prjPlulginManager -> when {
                prjPlulginManager.hasPlugin("org.jetbrains.kotlin.jvm") -> {
                    @Suppress("UNCHECKED_CAST")
                    val sourceSetContainer = prj.extensions.getByType(SourceSetContainer::class.java) as NamedDomainObjectContainer<Named>
                    createSrcBasePackages(prj, projectPackageDirString, sourceSetContainer)
                }
                prjPlulginManager.hasPlugin("org.jetbrains.kotlin.multiplatform") -> {
                    val kotlin = prj.extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)!!
                    //val kotlinProjectExtension = kotlin as org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
                    val sourceSetContainer: NamedDomainObjectContainer<KotlinSourceSet> = kotlin.sourceSets
                    createSrcBasePackages(prj, projectPackageDirString, sourceSetContainer)
                }
            } }
            println("  in project: '$relProjectDirString' ok.")
        }
    }

    private fun <E: Named, T: NamedDomainObjectContainer<E>> createSrcBasePackages(prj: Project, projectPackageDirString: String, sourceSetContainer: T) {
        sourceSetContainer.forEach { sourceSet ->
            val ssDir = File("${prj.projectDir}/src/${sourceSet.name}/kotlin")
            if (ssDir.exists() && !File(ssDir, projectPackageDirString).exists()) {
                prj.mkdir("$ssDir/$projectPackageDirString")
            }
        }
    }
}

    /** creating `01__PRJNAME/.gitkeep` and `ZZ__PRJNAME` files in each kotlin mpp project
 * as well as `_srcModule_PRJNAME/.gitkeep` and `ZZsrcModule_PRJNAME` files in each main sourceSets of these
 *
 * .gitignore:
 * <block>
 *  .idea/
 *  !.idea/scopes/
 *  !.idea/fileColors.xml
 * </block>
 *
 * if you had .idea/ ignored before, try
 * <block>
 * git rm --cached .idea/filename
 * git add --forced .idea/filename
 * </block>
 *
 * e.g. define scopes (in Settings... `Scopes`):
 * - scope 00__ (scope with all folders where the name starts with: 0[0-3]__, meaning the first folder
 * - scope src with _src.../ or ZZsrc... (scope with all folders where the name starts with _src)
 * - scope buildfiles (e.g. build.gradle.kts)
 *
 * and then in Settings ... `File Colors` add the scope(s) and give them a color .
 *
 * If you _then_ add folders / files matching the above scope names
 * you can see more clearly which "area" of code in the folder structure you are just looking at the moment .
 */
internal abstract class CreateIntellijScopeSentinelsTask : DefaultTask() {
    @TaskAction
    fun run() {
        println("======================================================================")
        println("==============    CreateIntellijScopeSentinelsTask    ================")
        println("======================================================================")
        project.allprojects.forEach { prj ->
            var relProjectDirString = prj.projectDir.toString().removePrefix(prj.rootProject.projectDir.toString())
            if (relProjectDirString.isBlank()) { relProjectDirString = "ROOT" } else { relProjectDirString = relProjectDirString.removePrefix("/") }
            println("  in project: $relProjectDirString ...")
            val prjNameUppercase = if (prj == prj.rootProject) {
                "ROOT"
            } else {
                prj.name.uppercase()
            }
            prj.pluginManager.let { prjPluginManager ->
                when {
                prjPluginManager.hasPlugin("org.jetbrains.kotlin.jvm") -> {
                    if (prj.name != prj.rootProject.name) {
                        val dir = prj.mkdir("${prj.projectDir}/01__$prjNameUppercase")
                        File(dir, ".gitkeep").createNewFile()
                        File(prj.projectDir, "ZZ__$prjNameUppercase").createNewFile()
                    }
                    val sourceSetContainer = prj.extensions.getByType(SourceSetContainer::class.java)
                    sourceSetContainer.forEach { ss: SourceSet ->
                        val ssDir = if (prj == prj.rootProject) {
                            File("src/${ss.name}")
                        } else {
                            File("${prj.projectDir}/src/${ss.name}")
                        }
                        if (ssDir.exists()) {
                            val mName =
                                ss.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            val dir = prj.mkdir("$ssDir/_src${mName}_$prjNameUppercase")
                            File(dir, ".gitkeep").createNewFile()
                            File(ssDir, "ZZsrc${mName}_$prjNameUppercase").createNewFile()
                        }
                    }
                }
                prjPluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform") -> {
                    if (prj != prj.rootProject) {
                        val dir = prj.mkdir("${prj.projectDir}/01__$prjNameUppercase")
                        File(dir, ".gitkeep").createNewFile()
                        File(prj.projectDir, "ZZ__$prjNameUppercase").createNewFile()
                    }
                    val kotlin = prj.extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)!!
                    //val kotlinProjectExtension = kotlin as org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
                    //prj.kotlin.sourceSets.forEach {
                    kotlin.sourceSets.forEach { topKotlinSourceSet ->
                        val ssDir = if (prj == prj.rootProject) {
                            File("src/${topKotlinSourceSet.name}")
                        } else {
                            File("${prj.projectDir}/src/${topKotlinSourceSet.name}")
                        }
                        if (ssDir.exists()) {
                            var suffix = ""
                            val mName = if (topKotlinSourceSet.name.endsWith("Main")) {
                                topKotlinSourceSet.name.removeSuffix("Main")
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            } else if (topKotlinSourceSet.name.endsWith("Test")) {
                                suffix = "_TEST"
                                topKotlinSourceSet.name.removeSuffix("Test")
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            } else {
                                topKotlinSourceSet.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            }
                            val dir = prj.mkdir("$ssDir/_src${mName}_${prjNameUppercase}${suffix}")
                            File(dir, ".gitkeep").createNewFile()
                            File(ssDir, "ZZsrc${mName}_${prjNameUppercase}${suffix}").createNewFile()
                        }
                    }
                }
            }}
            println("  in project: $relProjectDirString ok.")
        }
    }
}
