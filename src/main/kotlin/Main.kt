
import kotlinx.datetime.*
import kotlinx.datetime.DayOfWeek
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit
import kotlin.time.toDuration


fun main(args: Array<String>) {
//    prettyjson()
    timeElapsed("2021-12-31T20:50:00+01:00", "2022-02-03T14:01:01+01:00")
}

fun timeElapsed(startDateTimeIso: String, endDateTimeIso: String, onlyWithinServiceTimes: Boolean = true, timeZoneIdString: String = "Europe/Berlin", desc: String = ""): Duration {
    val timeZone = TimeZone.of(timeZoneIdString)
    val morningTime =  8 // o'clock
    val closingTime = 20 // o'clock
    val serviceHours = closingTime - morningTime
    val serviceHoursMillis = serviceHours * 3_600_000L

    val startInstant = Instant.parse(startDateTimeIso)
    val startDateTime = startInstant.toLocalDateTime(timeZone)
    val endInstant = Instant.parse(endDateTimeIso)
    //val endDateTime = endInstant.toLocalDateTime(timeZone)

    // we first eliminate an edge case, for if the issue was solved on the same day or next day until morning
    // anything else goes into the "multi-days issue" algorithm below
    val sameDayMorningDateTime = with(startDateTime) { LocalDateTime(year, month, dayOfMonth, morningTime, 0) }
    val sameDayMorningInstant = sameDayMorningDateTime.toInstant(timeZone)
    if (endInstant < sameDayMorningInstant) {
        // issue was opened and finished before morning
        if (onlyWithinServiceTimes) {
            return Duration.ZERO
        } else {
            return (endInstant.toEpochMilliseconds() - startInstant.toEpochMilliseconds()).toDuration(DurationUnit.MILLISECONDS)
        }
    }
    val sameDayClosingDateTime = with(startDateTime) { LocalDateTime(year, month, dayOfMonth, closingTime, 0) }
    val sameDayClosingInstant = sameDayClosingDateTime.toInstant(timeZone)
    if(endInstant < sameDayMorningInstant + 24.hours) {
        // same day solved or solved until next day morning
        // endTime - sameDayMorning
        if (onlyWithinServiceTimes) {
            if (startDateTime.dayOfWeek == DayOfWeek.SATURDAY || startDateTime.dayOfWeek == DayOfWeek.SUNDAY) return Duration.ZERO
            if (startInstant < sameDayMorningInstant) {
                // start before morning
                if (endInstant > sameDayClosingInstant) {
                    return serviceHoursMillis.toDuration(DurationUnit.MILLISECONDS)
                } else {
                    return (endInstant.toEpochMilliseconds() - sameDayMorningInstant.toEpochMilliseconds()).toDuration(DurationUnit.MILLISECONDS)
                }
            } else {
                // start after morning
                if (startInstant > sameDayClosingInstant) {
                    return Duration.ZERO
                } else {
                    if (endInstant > sameDayClosingInstant) {
                        return (sameDayClosingInstant.toEpochMilliseconds() - startInstant.toEpochMilliseconds()).toDuration(DurationUnit.MILLISECONDS)
                    } else {
                        return (endInstant.toEpochMilliseconds()            - startInstant.toEpochMilliseconds()).toDuration(DurationUnit.MILLISECONDS)
                    }
                }
            }
        } else {
            if ( startInstant < sameDayMorningInstant) {
                return (endInstant.toEpochMilliseconds() - sameDayMorningInstant.toEpochMilliseconds()).toDuration(DurationUnit.MILLISECONDS)
            } else {
                return (endInstant.toEpochMilliseconds() - startInstant.toEpochMilliseconds()).toDuration(DurationUnit.MILLISECONDS)
            }
        }
    }

    // =====================
    // "multi-days issue
    // =====================
    // actualStart is either within business Times
    // or at the morning of the same day
    // or at the morning of the next day
    var startOffsetFromMorning = Duration.ZERO
    var actualStartInstant = when {
        startDateTime.hour < morningTime -> {
            sameDayMorningInstant
        }
        startDateTime.hour >= closingTime -> {
            sameDayMorningInstant + 1.days
        }
        else -> {
            // start was within business times
            if (startDateTime.dayOfWeek != DayOfWeek.SATURDAY && startDateTime.dayOfWeek != DayOfWeek.SUNDAY) {
                startOffsetFromMorning = startInstant - sameDayMorningInstant
            }
            // we do our algorithm always from morningTime
            // and adjust the startOffset at the end of it
            sameDayMorningInstant
        }
    }
    var actualStartDateTime = actualStartInstant.toLocalDateTime(timeZone)
    var iterWeekday = actualStartDateTime.dayOfWeek.ordinal // Mo. = 0, Su. = 6
    // if actualStart now is on a weekend, shift it to monday morning
    when(actualStartDateTime.dayOfWeek) {
        DayOfWeek.SATURDAY -> {
            actualStartInstant += 2.days
            actualStartDateTime = actualStartInstant.toLocalDateTime(timeZone)
            iterWeekday = 0 // Monday
        }
        DayOfWeek.SUNDAY -> {
            actualStartInstant += 1.days
            actualStartDateTime = actualStartInstant.toLocalDateTime(timeZone)
            iterWeekday = 0 // Monday
        }
        else -> {}
    }

    // so at this point, actualStartInstant is always at morning time
    // either of the same day, or the morning of the next workday
    // only if start was within business time, startOffset duration is > 0

    var iterInstant = actualStartInstant   // time part is always morning time
    var durationMillis = 0L
    while (true) {
        if ( (iterInstant + 1.days) <= endInstant) {
            // not solved on this iter day (up to next morning, as iterInstant is always morning time)
            durationMillis += serviceHoursMillis
        } else {
            // now at day of solving (up to next morning, as iterInstant is always morning time)
            // startOffset was > 0 (start - sameDayMorning) if start was within business times, 0 otherwise
            val endDateTime = endInstant.toLocalDateTime(timeZone)
            if (onlyWithinServiceTimes) {
                if (endDateTime.dayOfWeek == DayOfWeek.SATURDAY || endDateTime.dayOfWeek == DayOfWeek.SUNDAY ||
                    (endDateTime.dayOfWeek == DayOfWeek.MONDAY && endDateTime.hour < morningTime)
                ) {
                    // solved at a saturday or sunday or a monday before morning
                    durationMillis -= startOffsetFromMorning.inWholeMilliseconds
                    break
                }
                with (endDateTime) {
                    val closingDateTime = LocalDateTime(year, month, dayOfMonth, closingTime, 0)
                    if (endDateTime > closingDateTime) {
                        durationMillis += (closingDateTime.toInstant(timeZone) - iterInstant - startOffsetFromMorning).inWholeMilliseconds
                    } else {
                        durationMillis += (endInstant                          - iterInstant - startOffsetFromMorning).inWholeMilliseconds
                    }
                }
            } else {
                if (endDateTime.dayOfWeek == DayOfWeek.SATURDAY || endDateTime.dayOfWeek == DayOfWeek.SUNDAY ||
                    (endDateTime.dayOfWeek == DayOfWeek.MONDAY && endDateTime.hour < morningTime)
                ) {
                    durationMillis -= startOffsetFromMorning.inWholeMilliseconds
                } else {
                    durationMillis += (endInstant - iterInstant - startOffsetFromMorning).inWholeMilliseconds.coerceAtLeast(0L)
                }
            }
            break
        }
        when (iterWeekday) {
            // doing this at the end of the loop, for the edge-case that ticket is solved on a weekend
            5 -> { durationMillis -= serviceHoursMillis ; iterWeekday += 1 } // Saturday
            6 -> { durationMillis -= serviceHoursMillis ; iterWeekday = 0} // Sunday
            else -> iterWeekday += 1
        }
        iterInstant += 1.days
    }

    println("  actualStart: ${toString(actualStartInstant, timeZone)}")

    val duration = durationMillis.toDuration(DurationUnit.MILLISECONDS)
    println("     duration: ${duration} (${duration.inWholeMilliseconds}ms) ${duration.toIsoString()}")
    return duration
}
fun toString(isoDateTime: String, timeZone: TimeZone) = toString(Instant.parse(isoDateTime), timeZone)
fun toString(instant: Instant, timeZone: TimeZone) = toString(instant.toLocalDateTime(timeZone), timeZone)
fun toString(dateTime: LocalDateTime, timeZone: TimeZone): String {
    return "${dateTime.dayOfWeek.toString()[0]}${dateTime.dayOfWeek.toString()[1].lowercase()}${dateTime.toString()}[${timeZone.toString()}]"
}

fun prettyjson() {
    val REParOpen = "[{(\\[]"
    val REParClos = "[})\\]]"
    val RECombineParsOpen = Regex("(?<=$REParOpen)\\s*\\R\\s*(?=$REParOpen)", RegexOption.MULTILINE)
    val RECombineParsClos = Regex("(?<=$REParClos)\\s*\\R\\s*(?=$REParClos)", RegexOption.MULTILINE)
    val RECompressCurlies = Regex("},\\s*\\R\\s+\\{", RegexOption.MULTILINE)

    val clipboard: Clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
    //val s = clipboard.getData(DataFlavor.getTextPlainUnicodeFlavor()) as String
    var s = clipboard.getData(DataFlavor.stringFlavor) as String

    s = s.replace(RECombineParsOpen, "") // combine opening parenthesises on separate lines
    s = s.replace(RECombineParsClos, "") // same on closing ones
    s = s.replace(RECompressCurlies, "},{")

    clipboard.setContents(StringSelection(s),null)
}
