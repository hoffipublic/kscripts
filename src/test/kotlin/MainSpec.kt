
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.datetime.TimeZone
import kotlin.time.Duration

data class ElapsedTimeData(val desc: String, val startDateTimeIso: String, val endDateTimeIso: String, val elapsedTime: Duration)

class MainTest : FunSpec({
    context("startDate and endDate") {
        withData(
            nameFn = { it.desc },
            ElapsedTimeData("FrAfterClosing2MondayBusinessTimes",
                "2021-12-31T20:50:00+01:00",
                "2022-01-03T14:01:01+01:00",
                Duration.parseIsoString("PT6H1M1S")),
            ElapsedTimeData("SuAfterClosing2TuesdayNextWeekBusinessTimes",
                "2022-01-02T20:50:00+01:00",
                "2022-01-11T14:01:01+01:00",
                Duration.parseIsoString("PT78H1M1S")),
        ) { (desc, startDateTimeIso, endDateTimeIso, elapsedTime) ->
            println("${desc}:")
            println(String.format("    startTime: %-40s", toString(startDateTimeIso, TimeZone.of("Europe/Berlin"))))
            println(String.format("      endTime: %-40s", toString(endDateTimeIso, TimeZone.of("Europe/Berlin"))))
            timeElapsed(startDateTimeIso, endDateTimeIso) shouldBe elapsedTime
        }
    }
})

// same as above as BehaviorSpec
class MainTestBDD : BehaviorSpec({

    Given("startDate and endDate") {
        forAll(
            row("FrAfterClosing2MondayBusinessTimes",
                "2021-12-31T20:50:00+01:00",
                "2022-01-03T14:01:01+01:00",
                Duration.parseIsoString("PT6H1M1S")),
            row("SuAfterClosing2TuesdayNextWeekBusinessTimes",
                "2022-01-02T20:50:00+01:00",
                "2022-01-11T14:01:01+01:00",
                Duration.parseIsoString("PT78H1M1S")),
        ) { desc, startDateTimeIso, endDateTimeIso, elapsedTime ->
            async {
                When("$desc") {
                    Then(" timeElapsed shouldBe $elapsedTime") {
                        println("${desc}:")
                        println(String.format("    startTime: %-40s", toString(startDateTimeIso, TimeZone.of("Europe/Berlin"))))
                        println(String.format("      endTime: %-40s", toString(endDateTimeIso, TimeZone.of("Europe/Berlin"))))
                        timeElapsed(startDateTimeIso, endDateTimeIso) shouldBe elapsedTime
                    }
                }
            }
        }
    }
})



//class AppBDDTest : BehaviorSpec({
//    given("the app and numbers 1 to 10") {
//        val app = App()
//        val numbers = (1..10).toList()
//
//        When("numbers are filtered") {
//            then("the filtered result should be '10', '20'") {
//                app.filter2(numbers) shouldBe "'10', '20'"
//            }
//        }
//        When("no numbers are given") {
//            then("the filtered result should be empty") {
//                app.filter2(emptyList()) shouldBe "''"
//            }
//        }
//    }
//})
