package com.kotlinqualitycompass.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArgumentsTest {
    @Test
    fun parses_output_and_repeatable_scope_options() {
        val parsed = parseArguments(
            arrayOf(
                "--project", ".",
                "--all",
                "--format", "json",
                "--output", "report.json",
                "--exclude-path", "**/demo/**",
                "--exclude-path", "**/*Preview.kt",
                "--threshold", "12.5",
                "--timeout-seconds", "30",
            ),
        )

        assertEquals(OutputFormat.JSON, parsed.format)
        assertEquals("report.json", parsed.outputFile)
        assertEquals(listOf("**/demo/**", "**/*Preview.kt"), parsed.excludePaths)
        assertEquals(12.5, parsed.threshold)
        assertEquals(30, parsed.timeoutSeconds)
    }

    @Test
    fun rejects_unknown_and_malformed_options() {
        assertFailsWith<IllegalArgumentException> {
            parseArguments(arrayOf("--project", ".", "--all", "--bogus"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseArguments(arrayOf("--project", ".", "--all", "--threshold", "nope"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseArguments(arrayOf("--project", ".", "--all", "--threshold", "NaN"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseArguments(arrayOf("--project", ".", "--all", "--timeout-seconds", "0"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseArguments(arrayOf("--project", ".", "--source", "Source.kt", "--reuse-coverage"))
        }
    }

    @Test
    fun rejects_missing_option_values() {
        assertFailsWith<IllegalArgumentException> {
            parseArguments(arrayOf("--project", ".", "--all", "--format"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseArguments(arrayOf("--project", ".", "--all", "--exclude-path", "--explain-scope"))
        }
    }
}
