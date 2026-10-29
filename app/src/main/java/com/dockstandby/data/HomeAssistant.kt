package com.dockstandby.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One Home Assistant entity as StandBy shows it. */
data class HaEntity(val id: String, val name: String, val state: String) {
    val domain get() = id.substringBefore('.')
    val isOn get() = state == "on" || state == "open" || state == "playing"
    /** Scenes and scripts are fired, not toggled. */
    val isAction get() = domain == "scene" || domain == "script"
}

/** Domains StandBy can control with a single tap. */
val haControllableDomains = setOf("light", "switch", "fan", "input_boolean", "scene", "script", "cover", "lock")

/**
 * Minimal Home Assistant REST client (https://developers.home-assistant.io/docs/api/rest).
 * All calls are blocking; run them off the main thread.
 */
class HomeAssistant(baseUrl: String, private val token: String) {
    private val base = baseUrl.trim().trimEnd('/')

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): String {
        val conn = URL("$base$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) error(if (code == 401) "Token rejected (401)" else "HTTP $code")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    /** Every controllable entity, sorted by name. */
    fun states(): List<HaEntity> {
        val arr = JSONArray(request("/api/states"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { o ->
                val id = o.getString("entity_id")
                HaEntity(id, o.optJSONObject("attributes")?.optString("friendly_name")?.ifBlank { null } ?: id, o.optString("state"))
            }
            .filter { it.domain in haControllableDomains }
            .sortedBy { it.name.lowercase() }
    }

    /** Toggles lights/switches/etc., or runs a scene/script. */
    fun activate(e: HaEntity) {
        val service = when {
            e.isAction -> "turn_on"
            e.domain == "lock" -> if (e.state == "locked") "unlock" else "lock"
            e.domain == "cover" -> "toggle"
            else -> "toggle"
        }
        request("/api/services/${e.domain}/$service", "POST", JSONObject().put("entity_id", e.id))
    }
}

/** Result of polling Home Assistant: the chosen entities, or an error to show. */
data class HaSnapshot(val entities: List<HaEntity> = emptyList(), val error: String? = null, val loaded: Boolean = false)

/**
 * Polls the chosen [ids] every [intervalMs] (2 s while the Home page is visible). [refreshKey]
 * forces an immediate refresh, e.g. right after a tap.
 */
@Composable
fun rememberHomeAssistant(s: Settings, intervalMs: Long, refreshKey: Int): State<HaSnapshot> =
    produceState(HaSnapshot(), s.haUrl, s.haToken, s.haEntities, intervalMs, refreshKey) {
        if (s.haUrl.isBlank() || s.haToken.isBlank()) {
            value = HaSnapshot(error = "Connect Home Assistant in setup", loaded = true)
            return@produceState
        }
        val ha = HomeAssistant(s.haUrl, s.haToken)
        while (true) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val byId = ha.states().associateBy { it.id }
                    HaSnapshot(s.haEntities.mapNotNull { byId[it] }, loaded = true)
                }.getOrElse { HaSnapshot(value.entities, it.message ?: "Can't reach Home Assistant", loaded = true) }
            }
            delay(intervalMs)
        }
    }
