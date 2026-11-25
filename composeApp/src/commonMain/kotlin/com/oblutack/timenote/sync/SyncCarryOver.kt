package com.oblutack.timenote.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Keeps what this app version does not understand.
 *
 * A file in the cloud may hold fields that a newer app version wrote (a rating, a label on a waypoint, ...). This app
 * reads only the fields it knows, and builds the file it uploads from its own database, which has no room for
 * unknown fields. Without this, every upload from an older version would silently delete them.
 *
 * [carryUnknownFields] takes the file this app is about to upload ([outgoing]) and the cloud copy it replaces
 * ([cloudCopy]) and copies every field that exists only in the cloud copy into the outgoing file, at any depth,
 * also inside the timeline events (matched by their id). Everything this app knows is left exactly as it is.
 *
 * The two membership maps ("tags" and "voiceNotes") hold data keyed by id, not field names: an entry that exists only
 * in the cloud copy is a membership this device has not merged, not an unknown field, so it is not copied (merging
 * is the job of the normal sync).
 */
fun carryUnknownFields(outgoing: String, cloudCopy: String?): String {
    if (cloudCopy == null) return outgoing
    val ours = parseObject(outgoing) ?: return outgoing
    val theirs = parseObject(cloudCopy) ?: return outgoing
    val merged = mergeObjects(ours, theirs, keysAreData = false)
    if (merged == ours) return outgoing // nothing unknown: leave the text exactly as it was
    return SyncJson.encodeToString(JsonObject.serializer(), merged)
}

private val MEMBERSHIP_MAPS = setOf("tags", "voiceNotes")

private fun parseObject(text: String): JsonObject? =
    runCatching { SyncJson.parseToJsonElement(text) }.getOrNull() as? JsonObject

private fun mergeObjects(ours: JsonObject, theirs: JsonObject, keysAreData: Boolean): JsonObject {
    val result = LinkedHashMap<String, JsonElement>(ours)
    for ((key, theirValue) in theirs) {
        val ourValue = ours[key]
        result[key] = when {
            ourValue == null -> if (keysAreData) continue else theirValue // unknown field, or an unmerged membership
            else -> mergeValues(ourValue, theirValue, childKeysAreData = !keysAreData && key in MEMBERSHIP_MAPS)
        }
    }
    return JsonObject(result)
}

private fun mergeValues(ours: JsonElement, theirs: JsonElement, childKeysAreData: Boolean): JsonElement = when {
    ours is JsonObject && theirs is JsonObject -> mergeObjects(ours, theirs, childKeysAreData)
    ours is JsonArray && theirs is JsonArray -> mergeArrays(ours, theirs)
    else -> ours // a known value: ours wins, whatever the cloud copy says
}

/** Lists of objects with an "id" (the timeline events): each of our entries takes the unknown fields of its twin. */
private fun mergeArrays(ours: JsonArray, theirs: JsonArray): JsonArray {
    val theirsById = theirs.filterIsInstance<JsonObject>()
        .mapNotNull { item -> (item["id"] as? JsonPrimitive)?.content?.let { it to item } }
        .toMap()
    return JsonArray(ours.map { item ->
        val id = ((item as? JsonObject)?.get("id") as? JsonPrimitive)?.content
        val twin = id?.let(theirsById::get)
        if (item is JsonObject && twin != null) mergeObjects(item, twin, keysAreData = false) else item
    })
}
