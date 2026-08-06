package net.zomis.speldesignbabbel

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.*


fun formatEmoji(count: Int): String {
    val superStreaks = count / 5
    val streaks = count % 5
    val s = buildString {
        repeat(superStreaks) { append(":heart_on_fire:") }
        repeat(streaks) { append(":fire:") }
    }
    return if (s.isEmpty()) s else "$s "
}

fun formatMessage(timeframe: Timeframe, newStats: List<ThreadStat>): String {
    val stats = newStats.joinToString("\n") { s ->
        "https://discord.com/channels/906297567011291177/${s.id} ${formatEmoji(s.count)}(${s.messageCount} msgs, ${s.users} users)"
    }
    val fmt = DateTimeFormatter.ISO_DATE
    val startDay = LocalDateTime.ofInstant(timeframe.startTime, ZoneId.systemDefault())
    val endDay = LocalDateTime.ofInstant(timeframe.endTime, ZoneId.systemDefault()).minusHours(6)
    val weekFields = WeekFields.of(Locale.getDefault())
    val weekNumber = startDay.get(weekFields.weekOfWeekBasedYear())
    val timeString = "week $weekNumber (${startDay.format(fmt)} - ${endDay.format(fmt)})"

    return "<@&906328017255673946>\n" +
            "Active posts in the game design forums for $timeString\n" +
            "$stats\n" +
            "\n" +
            ":fire: = Hot streak!\n:heart_on_fire: = Super hot streak! (= 5 :fire: )"
}

fun formatDisappearingMessage(disappearedMessage: String): String {
    if (disappearedMessage.isEmpty()) return ""
    return "\n\nOld streaks not active this week:\n$disappearedMessage"
}

fun makeUpdateMessage(timeframe: Timeframe): String {
    var stats = getLastStats(timeframe)
    println("Previous stats:")
    stats.forEach {
        println(it)
    }

    val active = getActiveThreads(timeframe)

    val disappearedStats = stats.filter { stat -> active.none { it.id == stat.id } && stat.count > 0 }
    val disappearedMessage = disappearedStats.joinToString("\n") { s ->
        "https://discord.com/channels/906297567011291177/${s.id} lost its streak of ${s.count}"
    }
    stats = stats.filter { thread -> active.any { it.id == thread.id } }.toMutableList()
    stats.forEach { it.count++ }
    for (a in active) {
        val existingStat = stats.find { it.id == a.id }
        if (existingStat == null) {
            println("Newcomer! ${a.name}")
            stats.add(ThreadStat(id = a.id, count = 0, messageCount = a.messageCount, users = a.users))
        } else {
            existingStat.messageCount = a.messageCount
            existingStat.users = a.users
        }
    }
    println(formatDisappearingMessage(disappearedMessage))
    println("New stats:")
    stats.forEach {
        println(it)
    }
    return formatMessage(timeframe, stats)
}
