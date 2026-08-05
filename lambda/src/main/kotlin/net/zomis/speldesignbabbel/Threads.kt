package net.zomis.speldesignbabbel

fun formatEmoji(count: Int): String {
    val superStreaks = count / 5
    val streaks = count % 5
    val s = buildString {
        repeat(superStreaks) { append(SUPER_STREAK) }
        repeat(streaks) { append(STREAK) }
    }
    return if (s.isEmpty()) s else "$s "
}

fun formatMessage(newStats: List<ThreadStat>): String {
    val stats = newStats.joinToString("\n") { s ->
        "https://discord.com/channels/906297567011291177/${s.id} ${formatEmoji(s.count)}(${s.messageCount} msgs, ${s.users} users)"
    }
    return "<@&906328017255673946>\nLast weeks active posts in the game design forums:\n$stats\n\n🔥 = Hot streak!\n❤️‍🔥 = Super hot streak! (= 5 🔥 )"
}

fun formatDisappearingMessage(disappearedMessage: String): String {
    if (disappearedMessage.isEmpty()) return ""
    return "\n\nOld streaks not active this week:\n$disappearedMessage"
}

fun makeUpdateMessage(timeframe: Timeframe): String {
    var stats = getLastStats(timeframe)
    println("Previous stats $stats")

    val active = getActiveThreads(timeframe)

    val disappearedStats = stats.filter { stat -> active.none { it.id == stat.id } && stat.count > 0 }
    val disappearedMessage = disappearedStats.joinToString("\n") { s ->
        "https://discord.com/channels/906297567011291177/${s.id} lost its streak of ${formatEmoji(s.count)}"
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
    return formatMessage(stats)
}
