package com.blocknize.desktop

import com.blocknize.app.domain.ScheduleBlock
import com.blocknize.app.domain.ScheduleTemplate
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

data class DesktopScheduleData(
    val templates: List<ScheduleTemplate>,
    val selectedTemplateId: String,
    val activeTemplateId: String?
) {
    val selectedTemplate: ScheduleTemplate
        get() = templates.first { it.id == selectedTemplateId }

    companion object {
        fun empty(): DesktopScheduleData {
            val initial = ScheduleTemplate("local-week", "Mi semana", emptyList())
            return DesktopScheduleData(listOf(initial), initial.id, activeTemplateId = null)
        }
    }
}

class DesktopScheduleStore(
    private val file: Path = Path.of(System.getProperty("user.home"), ".blocknize", "desktop-schedule.properties")
) {
    fun load(): DesktopScheduleData {
        if (Files.notExists(file)) return DesktopScheduleData.empty()
        DesktopFileSecurity.restrict(file)
        val properties = Properties().also { values -> Files.newInputStream(file).use { input -> values.load(input) } }
        return when (properties.getProperty("format")) {
            LEGACY_FORMAT -> migrateLegacy(properties)
            FORMAT_VERSION -> loadCurrent(properties)
            else -> error("El formato del horario local no es compatible.")
        }
    }

    fun save(data: DesktopScheduleData) {
        require(data.templates.isNotEmpty()) { "Debe conservarse al menos una semana." }
        require(data.templates.size <= MAX_TEMPLATES) { "Hay demasiadas semanas locales." }
        require(data.templates.all(ScheduleTemplate::isValid)) { "Una semana contiene datos no válidos." }
        require(data.templates.map(ScheduleTemplate::id).distinct().size == data.templates.size) { "Hay semanas duplicadas." }
        require(data.selectedTemplateId in data.templates.map(ScheduleTemplate::id)) { "La semana seleccionada no existe." }
        require(data.activeTemplateId == null || data.activeTemplateId in data.templates.map(ScheduleTemplate::id)) {
            "La semana activa no existe."
        }
        require(data.templates.sumOf { it.blocks.size } <= MAX_BLOCKS) { "El horario tiene demasiados bloques." }

        val properties = Properties().apply {
            setProperty("format", FORMAT_VERSION)
            setProperty("selectedTemplateId", data.selectedTemplateId)
            setProperty("activeTemplateId", data.activeTemplateId.orEmpty())
            setProperty("template.count", data.templates.size.toString())
            data.templates.forEachIndexed { templateIndex, template ->
                val prefix = "template.$templateIndex"
                setProperty("$prefix.id", template.id)
                setProperty("$prefix.name", template.name)
                setProperty("$prefix.block.count", template.blocks.size.toString())
                template.blocks.forEachIndexed { blockIndex, block ->
                    val blockPrefix = "$prefix.block.$blockIndex"
                    setProperty("$blockPrefix.id", block.id)
                    setProperty("$blockPrefix.title", block.title)
                    setProperty("$blockPrefix.description", block.description)
                    setProperty("$blockPrefix.tagName", block.tagName.orEmpty())
                    setProperty("$blockPrefix.daysMask", block.daysMask.toString())
                    setProperty("$blockPrefix.startMinute", block.startMinute.toString())
                    setProperty("$blockPrefix.endMinute", block.endMinute.toString())
                }
            }
        }
        writeAtomically(properties)
    }

    private fun loadCurrent(properties: Properties): DesktopScheduleData {
        val templateCount = properties.required("template.count").toInt()
        require(templateCount in 1..MAX_TEMPLATES) { "El archivo local contiene un número de semanas no válido." }
        val templates = (0 until templateCount).map { templateIndex ->
            val prefix = "template.$templateIndex"
            val blockCount = properties.required("$prefix.block.count").toInt()
            require(blockCount in 0..MAX_BLOCKS) { "El archivo local contiene demasiados bloques." }
            val blocks = (0 until blockCount).map { blockIndex ->
                val blockPrefix = "$prefix.block.$blockIndex"
                ScheduleBlock(
                    id = properties.required("$blockPrefix.id"),
                    title = properties.required("$blockPrefix.title"),
                    description = properties.getProperty("$blockPrefix.description").orEmpty(),
                    tagName = properties.getProperty("$blockPrefix.tagName").orEmpty().ifBlank { null },
                    daysMask = properties.required("$blockPrefix.daysMask").toInt(),
                    startMinute = properties.required("$blockPrefix.startMinute").toInt(),
                    endMinute = properties.required("$blockPrefix.endMinute").toInt()
                )
            }
            ScheduleTemplate(properties.required("$prefix.id"), properties.required("$prefix.name"), blocks)
        }
        val selectedId = properties.required("selectedTemplateId")
        val activeId = properties.getProperty("activeTemplateId").orEmpty().ifBlank { null }
        val data = DesktopScheduleData(templates, selectedId, activeId)
        require(templates.all(ScheduleTemplate::isValid)) { "El archivo local contiene una semana no válida." }
        require(templates.map(ScheduleTemplate::id).distinct().size == templates.size) { "El archivo local contiene semanas duplicadas." }
        require(selectedId in templates.map(ScheduleTemplate::id)) { "La semana seleccionada no existe." }
        require(activeId == null || activeId in templates.map(ScheduleTemplate::id)) { "La semana activa no existe." }
        require(templates.sumOf { it.blocks.size } <= MAX_BLOCKS) { "El archivo local contiene demasiados bloques." }
        return data
    }

    private fun migrateLegacy(properties: Properties): DesktopScheduleData {
        val count = properties.required("block.count").toInt()
        require(count in 0..MAX_BLOCKS) { "El archivo local contiene demasiados bloques." }
        val blocks = (0 until count).map { index ->
            val day = properties.required("block.$index.dayOfWeek").toInt()
            require(day in 1..7) { "El archivo local contiene un día no válido." }
            ScheduleBlock(
                id = properties.required("block.$index.id"),
                title = properties.required("block.$index.title"),
                description = "",
                tagName = null,
                daysMask = 1 shl (day - 1),
                startMinute = properties.required("block.$index.startMinute").toInt(),
                endMinute = properties.required("block.$index.endMinute").toInt()
            )
        }
        val template = ScheduleTemplate("local-week", "Mi semana", blocks)
        require(template.isValid) { "El horario anterior contiene datos no válidos." }
        return DesktopScheduleData(listOf(template), template.id, activeTemplateId = null).also(::save)
    }

    private fun writeAtomically(properties: Properties) {
        Files.createDirectories(file.parent)
        DesktopFileSecurity.restrict(file.parent)
        val temporaryFile = Files.createTempFile(file.parent, "desktop-schedule-", ".tmp")
        try {
            Files.newOutputStream(temporaryFile).use { properties.store(it, "BlockNize desktop schedule") }
            DesktopFileSecurity.restrict(temporaryFile)
            try {
                Files.move(temporaryFile, file, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryFile, file, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }

    private fun Properties.required(key: String): String = getProperty(key)
        ?: error("Falta el campo $key en el horario local.")

    private companion object {
        const val LEGACY_FORMAT = "1"
        const val FORMAT_VERSION = "2"
        const val MAX_TEMPLATES = 100
        const val MAX_BLOCKS = 10_000
    }
}
