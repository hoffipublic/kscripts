import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.KotlinClosure1
import org.gradle.kotlin.dsl.KotlinClosure2

/** applied with kotlinXxxBuildLogic convention plugins
 * or use with:
 * tasks {
 *     withType<Test> {
 *         buildLogicJvmTestConfig()
 *     }
 * }
 */
fun org.gradle.api.tasks.testing.Test.buildLogicJvmTestConfig() {
    // since gradle 8.x JunitPlatform is the default and must not be configured explicitly anymore
    useJUnitPlatform() // but if missing this line, kotlin kotests won't be found and run TODO
    failFast = false
    buildLogicCommonTestConfig("JVM")
}
fun AbstractTestTask.buildLogicCommonTestConfig(targetPlatform: String) {
    ignoreFailures = false
    testLogging {
        showStandardStreams = true
        showCauses = false
        showExceptions = false
        showStackTraces = false
        //exceptionFormat = TestExceptionFormat.FULL
        // better logging in beforeXXX, afterXXX below
        //events(
        //    org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
        //    org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
        //    org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
        //) //, STARTED //, standardOut, standardError)
    }

    val ansiReset = "\u001B[0m"
    val ansiGreen = "\u001B[32m"
    val ansiRed = "\u001B[31m"
    val ansiYellow = "\u001B[33m"

    fun getColoredResultType(resultType: TestResult.ResultType): String {
        return when (resultType) {
            TestResult.ResultType.SUCCESS -> "$ansiGreen $resultType $ansiReset"
            TestResult.ResultType.FAILURE -> "$ansiRed $resultType $ansiReset"
            TestResult.ResultType.SKIPPED -> "$ansiYellow $resultType $ansiReset"
        }
    }

    val variant1 = false
    if (variant1) {
        beforeSuite(KotlinClosure1<TestDescriptor, Unit>({
            if (this.className != null) { // if (this.parent == null) // will match the outermost suite
                println()
                //println(this.className?.substringAfterLast(".").orEmpty())
                println("${this.className?.substringAfterLast(".").orEmpty()} (${this.className})")
            }
        }))
    }
    //beforeTest(KotlinClosure1<TestDescriptor, Unit>({
    //    print("before any test:")
    //}))
    afterTest(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
        if (variant1) {
            println("${desc.displayName} = ${getColoredResultType(result.resultType)}")
        } else {
            println("${desc.className?.substringAfterLast(".")} | ${desc.displayName} = ${getColoredResultType(result.resultType)}")
        }
    }))
    if (variant1) {
        afterSuite(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
            if (desc.parent == null) { // will match the outermost suite
                println("$targetPlatform Result: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)")
            }
        }))
    } else {
        afterSuite(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
            if (desc.parent == null) { // will match the outermost suite
                println("\nTotal Test Results:")
                println("===================")
                val failsDefault = "${result.failedTestCount} failures"
                val fails =
                    if (result.failedTestCount > 0) BuildLogicGlobal.colorString(
                        BuildLogicGlobal.ConsoleColor.RED,
                        failsDefault
                    ) else failsDefault
                val outcome = if (result.resultType.name == "FAILURE") BuildLogicGlobal.colorString(
                    BuildLogicGlobal.ConsoleColor.RED,
                    result.resultType.name
                ) else BuildLogicGlobal.colorString(BuildLogicGlobal.ConsoleColor.GREEN, result.resultType.name)
                println("$targetPlatform Test Results: $outcome (total: ${result.testCount} tests, ${result.successfulTestCount} successes, $fails, ${result.skippedTestCount} skipped)\n")
            }
        }))
    }

    // listen to standard out and standard error of the test JVM(s)
    // onOutput { descriptor, event -> logger.lifecycle("Test: " + descriptor + " produced standard out/err: " + event.message ) }
}
