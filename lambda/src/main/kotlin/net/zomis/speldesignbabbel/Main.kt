package net.zomis.speldesignbabbel

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

private val botToken = requireEnv("DISCORD_BOT_TOKEN")
private val outputChannel = requireEnv("OUTPUT_CHANNEL")
private val inputChannel = requireEnv("INPUT_CHANNEL")
private val server = requireEnv("DISCORD_SERVER")

private val json = Json { ignoreUnknownKeys = true }
private val httpClient = HttpClient.newHttpClient()

private const val SUPER_STREAK = "❤️‍🔥"
private const val STREAK = "🔥"
private const val DISCORD_EPOCH = 1_420_070_400_000L
private val stockholmZone = ZoneId.of("Europe/Stockholm")

private fun requireEnv(name: String): String {
    val value = System.getenv(name)
    if (value.isNullOrBlank()) {
        System.err.println("Missing one or more environment variables: DISCORD_SERVER, INPUT_CHANNEL, OUTPUT_CHANNEL, DISCORD_BOT_TOKEN")
        throw IllegalStateException("Missing environment variables")
    }
    return value
}

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

class Handler : RequestHandler<Map<String, Any?>, Map<String, Any>> {
    override fun handleRequest(input: Map<String, Any?>?, context: Context): Map<String, Any> {
        println("Running real thing")
        val startTime = getStartOfWeekBefore(getStartOfWeek())
        val endTime = getStartOfWeek()
        println("Start time $startTime")
        println("End time $endTime")
        val updateMessage = makeUpdateMessage(Timeframe(startTime, endTime))
        println(updateMessage)
        postMessage(updateMessage)

        return mapOf(
            "statusCode" to 200,
            "body" to """{"message":"Lambda done"}""",
        )
    }
}

fun main() {
    val startTime = getStartOfWeekBefore(getStartOfWeek())
    val endTime = getStartOfWeek()
    println("Start time $startTime")
    println("End time $endTime")

    val updateMessage = makeUpdateMessage(Timeframe(startTime, endTime))
    println(updateMessage)
}

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

fun discordFetch(url: String): String {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("https://discord.com/api/$url"))
        .GET()
        .header("Content-Type", "application/json")
        .header("Authorization", "Bot $botToken")
        .header("User-Agent", "DiscordBot (https://www.zomis.net, 1)")
        .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

fun activeThreads() = json.parseToJsonElement(discordFetch("channels/$inputChannel/threads/active"))
    .jsonObject["threads"]!!
    .jsonArray

fun dateToSnowflake(date: Instant): Long {
    return (date.toEpochMilli() - DISCORD_EPOCH) shl 22
}

fun threadActiveInTime(id: String, timeframe: Timeframe): Pair<Int, Int> {
    val snowflake = dateToSnowflake(timeframe.startTime)
    val messages = json.parseToJsonElement(discordFetch("channels/$id/messages?after=$snowflake")).jsonArray
    println(messages)

    var messageCount = 0
    val users = mutableSetOf<String>()
    for (message in messages) {
        val obj = message.jsonObject
        val timestamp = Instant.parse(obj["timestamp"]!!.jsonPrimitive.content)
        if (isInTimeframe(timestamp, timeframe)) {
            messageCount++
            users.add(obj["author"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        }
    }
    return messageCount to users.size
}

fun getActiveThreads(timeframe: Timeframe): List<ActiveThread> {
    val arr = mutableListOf<ActiveThread>()
    for (threadElement in activeThreads()) {
        val thread = threadElement.jsonObject
        val id = thread["id"]!!.jsonPrimitive.content
        val name = thread["name"]?.jsonPrimitive?.contentOrNull ?: id
        val (messageCount, users) = threadActiveInTime(id, timeframe)
        if (messageCount > 0) {
            arr.add(ActiveThread(id, name, messageCount, users))
            println("Active: $id $name ($messageCount messages by $users users)")
        }
    }
    return arr
}

fun getLastStats(timeframe: Timeframe): MutableList<ThreadStat> {
    val snowflake = dateToSnowflake(timeframe.startTime)
    val messages = json.parseToJsonElement(discordFetch("channels/$outputChannel/messages?after=$snowflake")).jsonArray

    val match = messages
        .map { it.jsonObject }
        .first { it["content"]!!.jsonPrimitive.content.contains("active posts in the game design forums") }

    val lines = match["content"]!!.jsonPrimitive.content.split('\n')
    val arr = mutableListOf<ThreadStat>()
    val prefix = "https://discord.com/channels/$server/"
    for (line in lines) {
        if (!line.startsWith(prefix)) continue
        val afterPrefix = line.removePrefix(prefix)
        val spaceIndex = afterPrefix.indexOf(' ')
        val threadId: String
        val emojis: String
        if (spaceIndex >= 0) {
            threadId = afterPrefix.substring(0, spaceIndex)
            emojis = afterPrefix.substring(spaceIndex)
        } else {
            threadId = afterPrefix
            emojis = ""
        }
        val superCount = Regex(SUPER_STREAK).findAll(emojis).count()
        val count = Regex(STREAK).findAll(emojis).count() - superCount
        arr.add(ThreadStat(id = threadId, count = superCount * 5 + count))
    }
    return arr
}

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

fun postMessage(text: String): String {
    val payload = buildJsonObject {
        put("content", text)
    }.toString()

    val request = HttpRequest.newBuilder()
        .uri(URI.create("https://discord.com/api/channels/$outputChannel/messages"))
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bot $botToken")
        .header("User-Agent", "DiscordBot (https://www.zomis.net, 1)")
        .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}
