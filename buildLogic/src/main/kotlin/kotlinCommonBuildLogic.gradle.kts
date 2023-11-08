
// val libs and its values defined in ROOT/buildLogic/src/main/kotlin/VersionCatalogsExtensions.kt
BuildLogicGlobal.jdkVersion = libs.jdkVersion.toInt()
BuildLogicGlobal.kotlinVersion = libs.kotlinVersion
BuildLogicGlobal.composeVersion = libs.composeVersion

//// we did these things more elegant in the buildLogic/src/main/kotlin/... convention plugins
//
//plugins.withType(JavaBasePlugin::class).configureEach {
//    logger.lifecycle("running buildLogic/.../kotlinCommonBuildLogic.gradle.kts JavaBasePlugin::class ...")
//    // the project has the Java plugin
//    project.extensions.getByType<JavaPluginExtension>().apply {
//        toolchain {
//            languageVersion.set(JavaLanguageVersion.of(BuildLogicGlobal.jdkVersion))
//        }
//    }
//}
//
//plugins.withType(org.jetbrains.kotlin.gradle.plugin.DefaultKotlinBasePlugin::class).configureEach {
//    logger.lifecycle("running buildLogic/.../kotlinCommonBuildLogic.gradle.kts DefaultKotlinBasePlugin::class ...")
//    project.extensions.getByType<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension>().apply {
//        jvmToolchain(BuildLogicGlobal.jdkVersion)
//    }
//    tasks.withType<Test>().configureEach {
//        logger.lifecycle("running buildLogic configure tasks.withType<Test> with buildLogicJvmTestConfig() task: ${String.format("%-15s", this.taskIdentity.name)} of project: ${this.project.name}")
//        buildLogicJvmTestConfig() // useJUnitPlatform()
//    }
//    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>() {
//        logger.lifecycle("running buildLogic configure tasks.withType<KotlinNativeTest> with buildLogicCommonTestConfig() task: ${String.format("%-15s", this.taskIdentity.name)} of project: ${this.project.name}")
//        buildLogicCommonTestConfig("NATIVE")
//    }
//}
