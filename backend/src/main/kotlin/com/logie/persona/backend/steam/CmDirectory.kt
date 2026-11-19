package com.logie.persona.backend.steam

import `in`.dragonbra.javasteam.steam.discovery.ServerRecord
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The service's list of web servers, ordered so that the first few are on port 443 and spread over different data centres. */
internal object CmDirectory {
    private const val LIST = "https://api.steampowered.com/ISteamDirectory/GetCMListForConnect/v1/?cellid=0&cmtype=websockets&format=json"

    /** Used when the list can't be fetched (these are the service's own host names, which have been stable). */
    val FALLBACK = listOf("cmp1-lax1", "cmp1-sea1", "cmp1-ord1", "cmp1-iad1", "cmp1-atl3", "cmp1-dfw2", "cmp1-lhr1", "ext2-par1").map { "$it.steamserver.net:443" }

    /** The endpoints in [json] that are on port 443, least loaded first, one per data centre before a second from any. */
    fun parse(json: String): List<String> {
        val list = JSONObject(json).getJSONObject("response").getJSONArray("serverlist")
        data class Entry(val endpoint: String, val dc: String, val load: Double)
        val entries = (0 until list.length()).map { list.getJSONObject(it) }
            .filter { it.optString("endpoint").endsWith(":443") && it.optString("type", "websockets") == "websockets" }
            .map { Entry(it.getString("endpoint"), it.optString("dc"), it.optDouble("wtd_load", it.optDouble("load", 50.0))) }
            .sortedBy { it.load }
        val seenDc = HashSet<String>(); val first = ArrayList<String>(); val rest = ArrayList<String>()
        for (e in entries) if (seenDc.add(e.dc)) first += e.endpoint else rest += e.endpoint
        return first + rest
    }

    fun fetch(timeoutMs: Int = 8_000): List<String> {
        val c = URL(LIST).openConnection() as HttpURLConnection
        try { c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs; return parse(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
    }

    fun webServers(): List<ServerRecord> {
        val endpoints = (try { fetch() } catch (e: Exception) { emptyList() }).ifEmpty { FALLBACK }
        return endpoints.map { ServerRecord.createWebSocketServer(it) }
    }
}
