package org.ungoogled.patches.samsungcalculator

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.ungoogled.patches.samsungcalculator.SamsungCalculatorConstants.COMPATIBILITY_SAMSUNG_CALCULATOR
import org.w3c.dom.Element
import java.io.File

private val HEX_COLOUR = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

private data class CalculatorColourOption(val key: String, val title: String, val description: String, val default: String)

private val CALCULATOR_COLOURS = listOf(
    CalculatorColourOption("main", "Main / accent colour", "Primary accents, highlights and themed controls.", "#3E91FF"),
    CalculatorColourOption("background", "Background colour", "Calculator screens, panels and surfaces.", "#FFFFFF"),
    CalculatorColourOption("buttons", "Button colour", "Calculator keys, operators and action buttons.", "#F1F3F4"),
    CalculatorColourOption("text", "Text colour", "Display, labels, digits and icons.", "#202124"),
)

/**
 * Lets the patcher set Samsung Calculator's resource palette in patch settings.
 * Resource names are classified semantically, so the same choices cover light/night
 * variants and the app's primary, background, button and text colors.
 */
@Suppress("unused")
val samsungCalculatorColorsPatch = resourcePatch(
    name = "Custom colors",
    description = "Choose custom main, background, button and text colors for Samsung Calculator (hex colors).",
    default = false,
) {
    compatibleWith(COMPATIBILITY_SAMSUNG_CALCULATOR)

    val options = CALCULATOR_COLOURS.associate { option ->
        option.key to stringOption(
            key = option.key,
            default = option.default,
            title = option.title,
            description = "${option.description} Enter #RRGGBB or #AARRGGBB.",
            required = true,
        ) { it != null && HEX_COLOUR.matches(it) }
    }

    execute {
        // Decode all values qualifiers (including night and device-specific palettes) before editing.
        decode("res/values*/*.xml")
        val res = this["res"]
        val valuesFiles = res.walkTopDown()
            .filter { file -> file.isFile && file.extension.equals("xml", ignoreCase = true) &&
                file.parentFile?.name?.startsWith("values") == true }
            .toList()
        if (valuesFiles.isEmpty()) throw PatchException("no decoded values XML files found")

        fun bucket(name: String): String? {
            val key = name.lowercase()
            return when {
                listOf("text", "foreground", "label", "display", "digit", "number", "hint", "icon").any(key::contains) -> "text"
                listOf("button", "key", "operator", "equal", "control").any(key::contains) -> "buttons"
                listOf("background", "surface", "window", "screen", "panel").any(key::contains) -> "background"
                listOf("primary", "accent", "brand", "main", "theme", "tint", "highlight").any(key::contains) -> "main"
                else -> null
            }
        }

        val changed = mutableMapOf<String, Int>()
        for (file in valuesFiles) {
            val relative = file.relativeTo(res).invariantSeparatorsPath
            document("res/$relative").use { xml ->
                val entries = xml.getElementsByTagName("color")
                for (index in 0 until entries.length) {
                    val entry = entries.item(index) as? Element ?: continue
                    val optionKey = bucket(entry.getAttribute("name")) ?: continue
                    val colour = options.getValue(optionKey).value
                        ?: throw PatchException("no value supplied for $optionKey color")
                    if (entry.textContent.trim() != colour) {
                        entry.textContent = colour
                        changed[optionKey] = (changed[optionKey] ?: 0) + 1
                    }
                }
            }
        }

        if (changed.isEmpty()) {
            throw PatchException("no semantic color resources found; Samsung Calculator's resource palette may have changed")
        }
        java.util.logging.Logger.getLogger("SamsungCalculator").info("Custom color resources changed: $changed")
    }
}
