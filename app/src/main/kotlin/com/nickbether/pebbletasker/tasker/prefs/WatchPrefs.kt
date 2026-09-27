package com.nickbether.pebbletasker.tasker.prefs

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/** One listed option. [value] is in the watch.setPref wire form and is passed back unchanged. */
data class PrefOption(val value: String, val label: String)

/** A watch preference as described by watch.listPrefs / watch.getPref. */
data class PrefInfo(
    val key: String,
    val label: String,
    val description: String? = null,
    val type: String = "",
    val value: String? = null,
    val default: String? = null,
    val support: String = SUPPORT_UNKNOWN,
    val options: List<PrefOption> = emptyList(),
    val min: String? = null,
    val max: String? = null,
    val unit: String? = null,
) {
    enum class Editor { BOOLEAN, CHOICE, NUMBER, TEXT }

    /** Which value editor fits this preference. */
    val editor: Editor get() = when {
        options.isNotEmpty() -> Editor.CHOICE
        type.lowercase() in setOf("bool", "boolean") -> Editor.BOOLEAN
        type.lowercase() in setOf("int", "integer", "long", "number", "float", "double", "short", "byte") -> Editor.NUMBER
        else -> Editor.TEXT
    }

    /** Choices for the value field's pick-list. */
    val valueChoices: List<Pair<String, String>> get() = when (editor) {
        Editor.CHOICE -> options.map { (if (it.label == it.value) it.value else "${it.label}  (${it.value})") to it.value }
        Editor.BOOLEAN -> listOf("On (true)" to "true", "Off (false)" to "false")
        else -> emptyList()
    }

    /** Short range text for numeric editors, e.g. "0-100 %". */
    val rangeText: String? get() {
        if (min == null && max == null) return unit
        return listOfNotNull("${min ?: ""}-${max ?: ""}", unit).joinToString(" ")
    }

    /** Advisory check of a literal value; null when acceptable or not checkable (%variables). */
    fun problemWith(value: String): String? {
        val v = value.trim()
        if (v.isEmpty() || v.contains('%')) return null
        return when (editor) {
            Editor.CHOICE -> if (options.any { it.value == v }) null else "Not one of the listed options"
            Editor.BOOLEAN -> if (v.lowercase() in setOf("true", "false", "on", "off", "1", "0")) null else "Expected true or false"
            Editor.NUMBER -> {
                val n = v.toDoubleOrNull() ?: return "Expected a number"
                val lo = min?.toDoubleOrNull()
                val hi = max?.toDoubleOrNull()
                if ((lo != null && n < lo) || (hi != null && n > hi)) "Outside ${rangeText ?: "the allowed range"}" else null
            }
            Editor.TEXT -> null
        }
    }

    companion object {
        const val SUPPORT_SUPPORTED = "supported"
        const val SUPPORT_UNSUPPORTED = "unsupported"
        const val SUPPORT_UNKNOWN = "unknown"
    }
}

object WatchPrefs {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Wire string for a JSON value: primitives as their content, structures as compact JSON. */
    private fun JsonElement?.wire(): String? = when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> content
        else -> toString()
    }

    private fun parseOptions(element: JsonElement?): List<PrefOption> {
        val array = when (element) {
            is JsonArray -> element
            is JsonPrimitive -> if (element.isString) runCatching { json.parseToJsonElement(element.content).jsonArray }.getOrNull() else null
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.content.let { PrefOption(it, it) }
                is JsonObject -> {
                    val value = item["value"].wire() ?: return@mapNotNull null
                    val label = (item["label"] ?: item["name"] ?: item["title"]).wire() ?: value
                    PrefOption(value, label)
                }
                else -> null
            }
        }
    }

    private fun fromObject(o: JsonObject, keyField: String): PrefInfo? {
        val key = (o[keyField] ?: o["key"] ?: o["pref_key"]).wire()?.takeIf { it.isNotBlank() } ?: return null
        return PrefInfo(
            key = key,
            label = o["label"].wire()?.ifBlank { null } ?: key,
            description = o["description"].wire()?.ifBlank { null },
            type = o["type"].wire().orEmpty(),
            value = o["value"].wire(),
            default = o["default"].wire(),
            support = o["support"].wire()?.lowercase() ?: PrefInfo.SUPPORT_UNKNOWN,
            options = parseOptions(o["options"]),
            min = o["min"].wire(),
            max = o["max"].wire(),
            unit = o["unit"].wire()?.ifBlank { null },
        )
    }

    /** Parses watch.listPrefs `prefs`. Malformed entries are skipped; malformed JSON gives an empty list. */
    fun parseList(raw: String?): List<PrefInfo> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { (it as? JsonObject)?.let { o -> fromObject(o, "key") } }
    }

    /** Parses the flat watch.getPref result. */
    fun fromFlat(data: Map<String, String>): PrefInfo? {
        val key = data["pref_key"]?.takeIf { it.isNotBlank() } ?: return null
        return PrefInfo(
            key = key,
            label = data["label"]?.ifBlank { null } ?: key,
            description = data["description"]?.ifBlank { null },
            type = data["type"].orEmpty(),
            value = data["value"],
            default = data["default"],
            support = data["support"]?.lowercase() ?: PrefInfo.SUPPORT_UNKNOWN,
            options = parseOptions(data["options"]?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }),
            min = data["min"],
            max = data["max"],
            unit = data["unit"]?.ifBlank { null },
        )
    }

    /** Display order: supported, then unknown, then unsupported; alphabetical by label within. */
    fun grouped(prefs: List<PrefInfo>): List<Pair<String, List<PrefInfo>>> {
        val order = listOf(PrefInfo.SUPPORT_SUPPORTED, PrefInfo.SUPPORT_UNKNOWN, PrefInfo.SUPPORT_UNSUPPORTED)
        val byGroup = prefs.groupBy { if (it.support in order) it.support else PrefInfo.SUPPORT_UNKNOWN }
        return order.mapNotNull { g -> byGroup[g]?.sortedBy { it.label.lowercase() }?.let { g to it } }
    }

    // --- per-watch cache, so configuration works offline ---

    private const val PREFS_FILE = "pb_watch_prefs"
    private fun cacheKey(watch: String?) = "list:" + watch?.trim().orEmpty()

    fun cached(context: Context, watch: String?): List<PrefInfo> {
        val sp = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val raw = sp.getString(cacheKey(watch), null) ?: sp.getString(cacheKey(null), null)
        return parseList(raw)
    }

    fun store(context: Context, watch: String?, raw: String) {
        if (parseList(raw).isEmpty()) return
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit()
            .putString(cacheKey(watch), raw)
            // The active-watch list doubles as the fallback for editors without a selector.
            .putString(cacheKey(null), raw)
            .apply()
    }
}
