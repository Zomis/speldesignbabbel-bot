package net.zomis.speldesignbabbel

import java.time.Instant

const val SUPER_STREAK = "❤️‍🔥"
const val STREAK = "🔥"

data class Timeframe(val startTime: Instant, val endTime: Instant)

data class ThreadStat(
    val id: String,
    var count: Int,
    var messageCount: Int = 0,
    var users: Int = 0,
)

data class ActiveThread(
    val id: String,
    val name: String,
    val messageCount: Int,
    val users: Int,
)


