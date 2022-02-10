
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.datetime.TimeZone
import kotlin.time.Duration

data class ElapsedTimeData(val desc: String, val startDateTimeIso: String, val endDateTimeIso: String, val elapsedTime: Duration, val onlyWithinServiceTimes: Boolean)

class MainTest : FunSpec({
    context("startDate and endDate") {
        withData(
            nameFn = { it.desc },
            // same-day
            ElapsedTimeData("TuSameDayFullyBeforeMorningFalse",
                "2022-01-03T06:50:00+01:00",
                "2022-01-03T07:01:01+01:00",
                Duration.parseIsoString("PT11M1S"), false),
            ElapsedTimeData("TuSameDayFullyBeforeMorning",
                "2022-01-03T06:50:00+01:00",
                "2022-01-03T07:01:01+01:00",
                Duration.parseIsoString("PT0S"), true),
            ElapsedTimeData("TuSameDayFullyAfterClosingFalse",
                "2022-01-03T22:50:00+01:00",
                "2022-01-03T23:01:01+01:00",
                Duration.parseIsoString("PT11M1S"), false),
            ElapsedTimeData("TuSameDayFullyAfterClosing",
                "2022-01-03T22:50:00+01:00",
                "2022-01-03T23:01:01+01:00",
                Duration.parseIsoString("PT0S"), true),

            ElapsedTimeData("SameDayFullyWeekend",
                "2022-01-01T09:50:00+01:00",
                "2022-01-02T04:01:01+01:00",
                Duration.parseIsoString("PT0S"), true),

            ElapsedTimeData("TuSameDayStartBeforeMorningFalse",
                "2022-01-03T06:50:00+01:00",
                "2022-01-03T11:01:01+01:00",
                Duration.parseIsoString("PT3H1M1S"), false),
            ElapsedTimeData("TuSameDayStartBeforeMorning",
                "2022-01-03T06:50:00+01:00",
                "2022-01-03T11:01:01+01:00",
                Duration.parseIsoString("PT3H1M1S"), true),
            ElapsedTimeData("TuSameDayStartBeforeMorningEndAfterClosingFalse",
                "2022-01-03T06:50:00+01:00",
                "2022-01-03T22:01:01+01:00",
                Duration.parseIsoString("PT14H1M1S"), false),
            ElapsedTimeData("TuSameDayStartBeforeMorningEndAfterClosing",
                "2022-01-03T06:50:00+01:00",
                "2022-01-03T22:01:01+01:00",
                Duration.parseIsoString("PT12H"), true),

            ElapsedTimeData("TuSameDayEndAfterClosingFalse",
                "2022-01-03T09:50:00+01:00",
                "2022-01-03T22:01:01+01:00",
                Duration.parseIsoString("PT12H11M1S"), false),
            ElapsedTimeData("TuSameDayEndAfterClosing",
                "2022-01-03T09:50:00+01:00",
                "2022-01-03T22:01:01+01:00",
                Duration.parseIsoString("PT10H10M"), true),

            ElapsedTimeData("TuSameDayWithinBusinessTimesFalse",
                "2022-01-03T09:50:00+01:00",
                "2022-01-03T17:01:01+01:00",
                Duration.parseIsoString("PT7H11M1S"), false),
            ElapsedTimeData("TuSameDayWithinBusinessTimes",
                "2022-01-03T09:50:00+01:00",
                "2022-01-03T17:01:01+01:00",
                Duration.parseIsoString("PT7H11M1S"), true),

            // Multi-Days
            ElapsedTimeData("FrAfterClosing2MondayBusinessTimesFalse",
                "2021-12-31T20:50:00+01:00",
                "2022-01-03T14:01:01+01:00",
                Duration.parseIsoString("PT6H1M1S"), false),
            ElapsedTimeData("FrAfterClosing2MondayBusinessTimes",
                "2021-12-31T20:50:00+01:00",
                "2022-01-03T14:01:01+01:00",
                Duration.parseIsoString("PT6H1M1S"), true),
            ElapsedTimeData("SuAfterClosing2TuesdayNextWeekBusinessTimesFalse",
                "2022-01-02T20:50:00+01:00",
                "2022-01-11T14:01:01+01:00",
                Duration.parseIsoString("PT78H1M1S"), false),
            ElapsedTimeData("SuAfterClosing2TuesdayNextWeekBusinessTimes",
                "2022-01-02T20:50:00+01:00",
                "2022-01-11T14:01:01+01:00",
                Duration.parseIsoString("PT78H1M1S"), true),

            ElapsedTimeData("TuThWithinBusinessTimesFalse",
                "2022-01-04T09:50:00+01:00",
                "2022-01-06T14:01:01+01:00",
                Duration.parseIsoString("PT28H11M1S"), false),
            ElapsedTimeData("TuThWithinBusinessTimes",
                "2022-01-04T09:50:00+01:00",
                "2022-01-06T14:01:01+01:00",
                Duration.parseIsoString("PT28H11M1S"), true),
            ElapsedTimeData("TuTh+1WithinBusinessTimesFalse",
                "2022-01-04T09:50:00+01:00",
                "2022-01-13T14:01:01+01:00",
                Duration.parseIsoString("PT88H11M1S"), false),
            ElapsedTimeData("TuTh+1WithinBusinessTimes",
                "2022-01-04T09:50:00+01:00",
                "2022-01-13T14:01:01+01:00",
                Duration.parseIsoString("PT88H11M1S"), true),

            ElapsedTimeData("SaSuFullyWeekendFalse",
                "2022-01-01T09:50:00+01:00",
                "2022-01-03T04:01:01+01:00",
                Duration.parseIsoString("PT0H"), false),
            ElapsedTimeData("SaSuFullyWeekend",
                "2022-01-01T09:50:00+01:00",
                "2022-01-03T04:01:01+01:00",
                Duration.parseIsoString("PT0H"), true),
            ElapsedTimeData("SaSu+1FullyWeekendFalse",
                "2022-01-01T09:50:00+01:00",
                "2022-01-10T04:01:01+01:00",
                Duration.parseIsoString("PT60H"), false),
            ElapsedTimeData("SaSu+1FullyWeekend",
                "2022-01-01T09:50:00+01:00",
                "2022-01-10T04:01:01+01:00",
                Duration.parseIsoString("PT60H"), true),

            ElapsedTimeData("FrBusinessTimesCompletedWeekendFalse",
                "2022-01-07T09:50:00+01:00",
                "2022-01-10T04:01:01+01:00",
                Duration.parseIsoString("PT10H10M"), false),
            ElapsedTimeData("FrBusinessTimesCompletedWeekend",
                "2022-01-07T09:50:00+01:00",
                "2022-01-10T04:01:01+01:00",
                Duration.parseIsoString("PT10H10M"), true),
        ) { (desc, startDateTimeIso, endDateTimeIso, elapsedTime, onlyWithinServiceTimes) ->
            println("${desc}:")
            println(String.format("    startTime: %-40s", toString(startDateTimeIso, TimeZone.of("Europe/Berlin"))))
            println(String.format("      endTime: %-40s", toString(endDateTimeIso, TimeZone.of("Europe/Berlin"))))
            timeElapsed(startDateTimeIso, endDateTimeIso, onlyWithinServiceTimes, desc = desc) shouldBe elapsedTime
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
                Duration.parseIsoString("PT6H1M1S"), false),
            row("SuAfterClosing2TuesdayNextWeekBusinessTimes",
                "2022-01-02T20:50:00+01:00",
                "2022-01-11T14:01:01+01:00",
                Duration.parseIsoString("PT78H1M1S"), false),
        ) { desc, startDateTimeIso, endDateTimeIso, elapsedTime, onlyWithinServiceTimes ->
            async {
                When("$desc") {
                    Then(" timeElapsed shouldBe $elapsedTime") {
                        println("${desc}:")
                        println(String.format("    startTime: %-40s", toString(startDateTimeIso, TimeZone.of("Europe/Berlin"))))
                        println(String.format("      endTime: %-40s", toString(endDateTimeIso, TimeZone.of("Europe/Berlin"))))
                        timeElapsed(startDateTimeIso, endDateTimeIso, onlyWithinServiceTimes, desc = desc) shouldBe elapsedTime
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
