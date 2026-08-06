package net.zomis.speldesignbabbel

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.*


private const val DISCORD_EPOCH = 1_420_070_400_000L

class Handler : RequestHandler<Map<String, Any?>, Map<String, Any>> {
    override fun handleRequest(input: Map<String, Any?>?, context: Context): Map<String, Any> {
        println("Running real thing")
        val startTime = getStartOfWeekBefore(getStartOfWeek().atStartOfDay().atZone(stockholmZone).toInstant())
        val endTime = getStartOfWeek().atStartOfDay().atZone(stockholmZone).toInstant()
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
    val startTime = getStartOfWeek()
    val endTime = getStartOfWeekAfter(startTime)
//    val startTime = LocalDate.of(2026, Month.JULY, 27)
//    val endTime = startTime.plusWeeks(1).atStartOfDay()
    val startInstant = startTime.atStartOfDay().toInstant(stockholmZone.toZoneOffset())
    val endInstant = startTime.findWeekEnd().toInstant(stockholmZone.toZoneOffset())

    println("Start time $startTime -- $startInstant")
    println("End time $endTime -- $endInstant")

    val updateMessage = makeUpdateMessage(Timeframe(startInstant, endInstant))
    println(updateMessage)
}

fun dateToSnowflake(date: Instant): Long {
    return (date.toEpochMilli() - DISCORD_EPOCH) shl 22
}

fun threadActiveInTime(id: String, timeframe: Timeframe): Pair<Int, Int> {
    val snowflake = dateToSnowflake(timeframe.startTime)
    val snowflakeEnd = dateToSnowflake(timeframe.endTime)
    val messages = json.parseToJsonElement(discordFetch("channels/$id/messages?after=$snowflake&before=$snowflakeEnd")).jsonArray
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
    val findRelevantThreads = findRelevantThreads(timeframe)
    for (threadElement in findRelevantThreads) {
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
    val messages = json.parseToJsonElement(discordFetch("channels/$outputChannel/messages?after=$snowflake"))
        .jsonArray//.sortedBy { it.jsonObject["timestamp"]!!.jsonPrimitive.content }

    val match = messages
        .first { it.jsonObject["content"]!!.jsonPrimitive.content.contains("posts in the game design forums") }
    val allMessages = messages.drop(messages.indexOf(match))
        .takeWhile {
            val content = it.jsonObject["content"]!!.jsonPrimitive.content
            val followup = content.contains("msgs") && !content.contains("posts in the game design forums")
            followup || it == match
        }

    val lines = allMessages.flatMap { it.jsonObject["content"]!!.jsonPrimitive.content.split('\n') }
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
