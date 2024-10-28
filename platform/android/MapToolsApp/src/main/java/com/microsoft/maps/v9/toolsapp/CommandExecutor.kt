package com.microsoft.maps.v9.toolsapp

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class Scene(var lat: Double, var lon: Double, var zoom: Double)
data class Style(var title: String, var value: String)

/**
 *
 */
interface CommandExecutor {
    /**
     * Enable / disable automation mode.
     * @param enable True disable all user interactions; otherwise false.
     */
    fun setAutomationMode(enable: Boolean)

    /**
     * Set the map scene.
     * @param scene
     */
    fun setScene(scene: Scene)

    /**
     * Set the map theme
     * @param themeName (aerial,aerialWithOverlay,roadLight,roadDark,roadCanvasLight,roadHighContrastLight,roadHighContrastDark,vibrantLight,vibrantDark)
     */
    fun setTheme(themeName: String)

    /**
     * Update custom style list.
     */
    fun updateStyles(styles: List<Style>)

    /**
     * Set the map style
     * @param index The custom style index
     */
    fun setStyle(index: Int)

    fun extractLabels()
}

/**
 *
 */
class CommandEvaluator(private val executor: CommandExecutor) {
    /**
     *
     */
    data class Item(var type: String, var args: Any?)

    private val _mapper = jacksonObjectMapper()

    fun evaluate(commandString: String) {
        val items: List<Item> = _mapper.readValue(commandString)
        items.forEach { evaluateItem(it) }
    }

    private fun evaluateItem(item: Item) {
        when (item.type) {
            "setAutomationMode" -> {
                val enable = if (item.args is Boolean) item.args as Boolean else throw IllegalArgumentException("Invalid args type")
                executor.setAutomationMode(enable)
            }
            "setScene" -> {
                val args = if (item.args is Map<*, *>) item.args as Map<*, *> else throw IllegalArgumentException("Invalid args type")
                val scene: Scene = _mapper.convertValue(args)
                executor.setScene(scene)
            }
            "setTheme" -> {
                val themeName = if (item.args is String) item.args as String else throw IllegalArgumentException("Invalid args type")
                executor.setTheme(themeName)
            }
            "setStyle" -> {
                val index = if (item.args is Int) item.args as Int else throw IllegalArgumentException("Invalid args type")
                executor.setStyle(index)
            }
            "updateStyles" -> {
                val args = if (item.args is List<*>) item.args as List<*> else throw IllegalArgumentException("Invalid args type")
                val styles: List<Style> = _mapper.convertValue(args)
                executor.updateStyles(styles)
            }
            "extractLabels" -> {
                executor.extractLabels()
            }
            else -> throw IllegalArgumentException("Unknown command type")
        }
    }
}
