package net.zomis.speldesignbabbel

import java.time.Instant
import java.time.temporal.ChronoUnit

object Streaks {
    fun count(emojis: String): Int = countEmojiStreaks(emojis) + countColonStreaks(emojis)
    fun countColonStreaks(emojis: String): Int {
        val superCount = Regex(COLON_SUPER_STREAK).findAll(emojis).count()
        val count = Regex(COLON_STREAK).findAll(emojis).count()
        return superCount * 5 + count
    }
    fun countEmojiStreaks(emojis: String): Int {
        val superCount = Regex(EMOJI_SUPER_STREAK).findAll(emojis).count()
        val count = Regex(EMOJI_STREAK).findAll(emojis).count() - superCount
        // Super count emoji is 2 emojis in 1, so avoid counting superStreak twice by subtracting superCount from count
        return superCount * 5 + count
    }

    const val EMOJI_SUPER_STREAK = "❤️‍🔥"
    const val EMOJI_STREAK = "🔥"
    const val COLON_SUPER_STREAK = ":heart_on_fire:"
    const val COLON_STREAK = ":fire:"
}

data class Timeframe(val startTime: Instant, val endTime: Instant) {
    fun adjust(amount: Long, unit: ChronoUnit): Timeframe = Timeframe(startTime.plus(amount, unit), endTime.plus(amount, unit))
}

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


