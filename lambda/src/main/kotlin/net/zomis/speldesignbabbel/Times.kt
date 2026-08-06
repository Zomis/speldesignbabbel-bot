package net.zomis.speldesignbabbel

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

val stockholmZone = ZoneId.of("Europe/Stockholm")

fun getStartOfWeek(): LocalDate {
    val stockholmNow = ZonedDateTime.now(stockholmZone)
    return stockholmNow
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .toLocalDate()
}

fun getStartOfWeekBefore(time: Instant): Instant {
    return LocalDate.ofInstant(time, stockholmZone)
        .minusWeeks(1)
        .atStartOfDay(stockholmZone)
        .toInstant()
}

fun getStartOfWeekAfter(time: LocalDate): Instant {
    return time.findWeekEnd().atZone(stockholmZone).toInstant()
}

fun isInTimeframe(time: Instant, timeframe: Timeframe): Boolean {
    return !time.isBefore(timeframe.startTime) && time.isBefore(timeframe.endTime)
}

fun LocalDate.findWeekEnd(): LocalDateTime {
    var date = this.atStartOfDay()
    do {
        date = date.plusDays(1)
    } while (date.dayOfWeek != DayOfWeek.MONDAY)
    return date
}

fun ZoneId.toZoneOffset(): ZoneOffset {
    val instant = Instant.now() //can be LocalDateTime
    return this.rules.getOffset(instant)
}
