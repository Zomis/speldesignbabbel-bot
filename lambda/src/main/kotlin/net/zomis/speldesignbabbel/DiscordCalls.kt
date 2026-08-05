package net.zomis.speldesignbabbel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

val botToken = requireEnv("DISCORD_BOT_TOKEN")
val outputChannel = requireEnv("OUTPUT_CHANNEL")
val inputChannel = requireEnv("INPUT_CHANNEL")
val server = requireEnv("DISCORD_SERVER")

private fun requireEnv(name: String): String {
    val value = System.getenv(name)
    if (value.isNullOrBlank()) {
        System.err.println("Missing one or more environment variables: DISCORD_SERVER, INPUT_CHANNEL, OUTPUT_CHANNEL, DISCORD_BOT_TOKEN")
        throw IllegalStateException("Missing environment variables")
    }
    return value
}

val json = Json { ignoreUnknownKeys = true }
private val httpClient = HttpClient.newHttpClient()

fun activeThreads() = json.parseToJsonElement(discordFetch("channels/$inputChannel/threads/active"))
    .jsonObject["threads"]!!
    .jsonArray


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
