package com.oblutack.timenote

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The look of the app is decided in one place (`ui/theme`). These tests read the source files and fail when a screen goes
 * around it, so a later change cannot quietly bring the scattered colours and sizes back.
 */
class ThemeRulesTest {
    private val sources: List<File> by lazy {
        // unit tests run with the module folder as the working directory
        val root = listOf(File("src/commonMain/kotlin"), File("composeApp/src/commonMain/kotlin")).first { it.isDirectory }
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun isTheme(file: File) = file.invariantSeparatorsPath.contains("/ui/theme/")

    @Test fun sourcesWereFound() = assertTrue(sources.size > 50, "found only ${sources.size} source files, the tests below would check nothing")

    @Test fun noColourIsWrittenAsANumberOutsideTheTheme() {
        val literal = Regex("""Color\(\s*0x[0-9A-Fa-f]{8}\s*\)""")
        val offenders = sources.filterNot(::isTheme).flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line -> if (literal.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null }
        }
        assertTrue(offenders.isEmpty(), "Use a role (TnTheme.colors.…) or Palette.… instead of a colour number:\n" + offenders.joinToString("\n"))
    }

    @Test fun textSizesComeFromTheScale() {
        val named = Regex("""fontSize\s*=\s*(10|12|13|14|16|18|20|24)\.sp\b""")
        val offenders = sources.filterNot(::isTheme).flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line -> if (named.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null }
        }
        assertTrue(offenders.isEmpty(), "Use TnType.… instead of a size number:\n" + offenders.joinToString("\n"))
    }

    @Test fun cornersComeFromTheShapes() {
        val named = Regex("""RoundedCornerShape\((50|(8|12|16|24)\.dp)\)""")
        val offenders = sources.filterNot(::isTheme).flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line -> if (named.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null }
        }
        assertTrue(offenders.isEmpty(), "Use TnShape.… instead of a corner size:\n" + offenders.joinToString("\n"))
    }
}
