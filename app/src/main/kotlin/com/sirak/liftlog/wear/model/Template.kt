package com.sirak.liftlog.wear.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WatchTemplateSet(
    val type: String = "normal",
    val weight: Double? = null,
    val reps: Int? = null,
    val duration: Int? = null,
    val distance: Double? = null,
    val jumps: Int? = null,
    val steps: Int? = null,
)

data class WatchTemplateExercise(
    val name: String,
    val fields: List<String> = listOf("weight", "reps"),
    val sets: List<WatchTemplateSet> = listOf(WatchTemplateSet()),
    val restSeconds: Int = 0,
    val supersetId: String? = null,
)

data class WatchTemplate(
    val id: String,
    val name: String,
    val lastUsedAt: Long? = null,
    val exercises: List<WatchTemplateExercise> = emptyList(),
)

data class TemplateCatalog(
    val unit: String = "lb",
    val templates: List<WatchTemplate> = emptyList(),
)

fun parseTemplateCatalog(json: String): TemplateCatalog? = runCatching {
    val obj = JSONObject(json)
    val arr = obj.optJSONArray("templates") ?: JSONArray()
    val templates = List(arr.length()) { index -> arr.getJSONObject(index).toTemplate() }
        .sortedByDescending { it.lastUsedAt ?: 0L }
    TemplateCatalog(
        unit = obj.optString("unit", "lb").ifBlank { "lb" },
        templates = templates,
    )
}.getOrNull()

/** Builds a live workout whose ids the phone can adopt, so both devices edit the same sets. */
fun WatchTemplate.toWorkout(unit: String, startedAt: Long): WatchWorkout {
    val supersets = mutableMapOf<String, String>()
    return WatchWorkout(
        id = startedAt.toString(),
        name = name,
        startedAt = startedAt,
        unit = if (unit == "kg") "kg" else "lb",
        exercises = exercises.map { exercise ->
            val sets = exercise.sets.ifEmpty { listOf(WatchTemplateSet()) }
            WatchExercise(
                name = exercise.name,
                fields = exercise.fields.ifEmpty { listOf("weight", "reps") },
                restSeconds = exercise.restSeconds,
                supersetId = exercise.supersetId?.let { supersets.getOrPut(it) { UUID.randomUUID().toString() } },
                sets = sets.map { set ->
                    WatchSet(
                        type = set.type.ifBlank { "normal" },
                        weight = set.weight,
                        reps = set.reps,
                        duration = set.duration,
                        distance = set.distance,
                        jumps = set.jumps,
                        steps = set.steps,
                    )
                },
            )
        },
    )
}

fun WatchWorkout.startRequest(templateId: String): String = JSONObject().apply {
    put("templateId", templateId)
    put("startedAt", startedAt)
    put("exercises", JSONArray().apply {
        exercises.forEach { exercise ->
            put(JSONObject().apply {
                put("id", exercise.id)
                putNullable("supersetId", exercise.supersetId)
                put("sets", JSONArray().apply {
                    exercise.sets.forEach { set ->
                        put(JSONObject().put("id", set.id))
                    }
                })
            })
        }
    })
}.toString()

private fun JSONObject.toTemplate(): WatchTemplate {
    val exerciseArr = optJSONArray("exercises") ?: JSONArray()
    return WatchTemplate(
        id = optString("id"),
        name = optString("name", "Template").ifBlank { "Template" },
        lastUsedAt = optLongOrNull("lastUsedAt"),
        exercises = List(exerciseArr.length()) { exerciseArr.getJSONObject(it).toTemplateExercise() },
    )
}

private fun JSONObject.toTemplateExercise(): WatchTemplateExercise {
    val fieldArr = optJSONArray("fields")
    val fields = if (fieldArr == null || fieldArr.length() == 0) {
        listOf("weight", "reps")
    } else {
        List(fieldArr.length()) { fieldArr.getString(it) }
    }
    val setArr = optJSONArray("sets") ?: JSONArray()
    val sets = if (setArr.length() == 0) {
        listOf(WatchTemplateSet())
    } else {
        List(setArr.length()) { setArr.getJSONObject(it).toTemplateSet() }
    }
    return WatchTemplateExercise(
        name = optString("name", "Exercise"),
        fields = fields,
        sets = sets,
        restSeconds = optInt("restSeconds", 0).coerceAtLeast(0),
        supersetId = optString("supersetId", "").ifBlank { null },
    )
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

private fun JSONObject.toTemplateSet(): WatchTemplateSet = WatchTemplateSet(
    type = optString("type", "normal"),
    weight = optDoubleOrNull("weight"),
    reps = optIntOrNull("reps"),
    duration = optIntOrNull("duration"),
    distance = optDoubleOrNull("distance"),
    jumps = optIntOrNull("jumps"),
    steps = optIntOrNull("steps"),
)
