package com.blocknize.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TransferBlock(
    val title: String,
    val description: String,
    val tagName: String?,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int
)

data class TransferTemplate(val name: String, val blocks: List<TransferBlock>) {
    val dayCount: Int get() = blocks.fold(0) { mask, block -> mask or block.daysMask }.countOneBits()
    val overlapCount: Int get() = blocks.indices.sumOf { first ->
        (first + 1 until blocks.size).count { second ->
            val a = blocks[first]; val b = blocks[second]
            a.daysMask and b.daysMask != 0 && a.startMinute < b.endMinute && b.startMinute < a.endMinute
        }
    }
}

object TemplateTransferCodec {
    const val MAX_BYTES = 1_048_576
    const val MAX_BLOCKS = 500

    fun encode(templateName: String, blocks: List<BlockRow>): String {
        val tasks = JSONArray()
        val schedule = JSONArray()
        blocks.forEach { block ->
            tasks.put(JSONObject().apply {
                put("taskId", block.taskId)
                put("title", block.title)
                put("description", block.description)
                put("tag", block.tagName ?: JSONObject.NULL)
            })
            schedule.put(JSONObject().apply {
                put("taskId", block.taskId)
                put("daysMask", block.daysMask)
                put("startMinute", block.startMinute)
                put("endMinute", block.endMinute)
            })
        }
        return JSONObject().put("format", "blocknize-template")
            .put("schemaVersion", 1).put("name", templateName)
            .put("tasks", tasks).put("schedule", schedule).toString(2)
    }

    fun decode(json: String): TransferTemplate {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "El archivo supera el tamaño máximo de 1 MB." }
        val root = try { JSONObject(json) } catch (_: Exception) { error("El archivo no contiene un JSON válido.") }
        require(root.optString("format") == "blocknize-template") { "El archivo no es una plantilla de BlockNize." }
        require(root.optInt("schemaVersion", -1) == 1) { "La versión de esta plantilla no es compatible." }
        val name = root.optString("name").trim()
        require(name.isNotEmpty() && name.length <= 80) { "El nombre debe tener entre 1 y 80 caracteres." }
        val tasksArray = root.optJSONArray("tasks") ?: error("Falta la lista de tareas.")
        val schedule = root.optJSONArray("schedule") ?: error("Falta la programación semanal.")
        require(tasksArray.length() <= MAX_BLOCKS && schedule.length() <= MAX_BLOCKS) { "La plantilla supera el límite de $MAX_BLOCKS bloques." }

        data class Task(val title: String, val description: String, val tag: String?)
        val tasks = mutableMapOf<String, Task>()
        for (index in 0 until tasksArray.length()) {
            val task = tasksArray.optJSONObject(index) ?: error("La tarea ${index + 1} no es válida.")
            val id = task.optString("taskId")
            require(id.isNotBlank() && id.length <= 128 && id !in tasks) { "Hay identificadores de tarea vacíos o duplicados." }
            val title = task.optString("title").trim()
            require(title.isNotEmpty() && title.length <= 200) { "El título de la tarea ${index + 1} no es válido." }
            val description = task.optString("description", "")
            require(description.length <= 2_000) { "La descripción de una tarea es demasiado larga." }
            val tag = if (task.isNull("tag")) null else task.optString("tag").trim().takeIf(String::isNotEmpty)
            require(tag == null || tag.length <= 80) { "El nombre de una etiqueta es demasiado largo." }
            tasks[id] = Task(title, description, tag)
        }

        val used = mutableSetOf<String>()
        val blocks = (0 until schedule.length()).map { index ->
            val item = schedule.optJSONObject(index) ?: error("El bloque ${index + 1} no es válido.")
            val taskId = item.optString("taskId")
            require(taskId in tasks && used.add(taskId)) { "Hay una referencia de tarea inválida o duplicada." }
            val days = item.optInt("daysMask", -1)
            val start = item.optInt("startMinute", -1)
            val end = item.optInt("endMinute", -1)
            require(days in 1..127) { "El bloque ${index + 1} debe tener al menos un día válido." }
            require(start in 0..1439 && end in 1..1440 && start < end) { "El horario del bloque ${index + 1} no es válido." }
            val task = tasks.getValue(taskId)
            TransferBlock(task.title, task.description, task.tag, days, start, end)
        }
        require(used.size == tasks.size) { "Hay tareas sin bloque asociado." }
        return TransferTemplate(name, blocks)
    }

    fun newId(): String = UUID.randomUUID().toString()
}
