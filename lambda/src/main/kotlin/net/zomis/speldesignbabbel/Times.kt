package net.zomis.speldesignbabbel

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

private val stockholmZone = ZoneId.of("Europe/Stockholm")

fun getStartOfWeek(): Instant {
    val stockholmNow = ZonedDateTime.now(stockholmZone)
    return stockholmNow
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .toLocalDate()
        .atStartOfDay(stockholmZone)
        .toInstant()
}

fun getStartOfWeekBefore(time: Instant): Instant {
    return LocalDate.ofInstant(time, stockholmZone)
        .minusWeeks(1)
        .atStartOfDay(stockholmZone)
        .toInstant()
}

fun getStartOfWeekAfter(time: Instant): Instant {
    return LocalDate.ofInstant(time, stockholmZone)
        .plusWeeks(1)
        .atStartOfDay(stockholmZone)
        .toInstant()
}

fun isInTimeframe(time: Instant, timeframe: Timeframe): Boolean {
    return !time.isBefore(timeframe.startTime) && time.isBefore(timeframe.endTime)
}
