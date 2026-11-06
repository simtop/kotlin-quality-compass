package com.kotlinqualitycompass.cli

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinSourceAnalyzerTest {
    @Test
    fun finds_functions_and_counts_kotlin_control_flow() {
        val source = createTempFile(suffix = ".kt").apply {
            writeText(
                """
                package sample

                class Example {
                    fun decide(value: Int): Boolean {
                        fun local() = if (value == 42) true else false
                        return if (value > 0 && value < 10) {
                            true
                        } else {
                            false
                        }
                    }
                }
                """.trimIndent(),
            )
        }

        val functions = KotlinSourceAnalyzer().discover(source)

        assertEquals(listOf("decide"), functions.map { it.name })
        assertEquals(3, functions.single().cyclomaticComplexity)
        assertTrue(functions.single().endLine >= functions.single().startLine)
    }

    @Test
    fun understands_a_common_main_source_set_and_ignores_expect_declarations() {
        val source = Path.of("../fixtures/kmp/src/commonMain/kotlin/fixture/KmpLogic.kt").normalize()

        val functions = KotlinSourceAnalyzer().discover(source)

        assertEquals(listOf("classify"), functions.map { it.name })
        assertEquals(3, functions.single().cyclomaticComplexity)
    }

    @Test
    fun records_inline_functions_for_coverage_attribution() {
        val source = createTempFile(suffix = ".kt").apply {
            writeText("inline fun transform(value: Int) = value + 1")
        }

        val function = KotlinSourceAnalyzer().discover(source).single()

        assertTrue(function.isInline)
    }

    @Test
    fun excludes_preview_functions_and_preview_parameter_providers_by_default() {
        val source = createTempFile(suffix = ".kt").apply {
            writeText(
                """
                annotation class Preview

                class Samples : PreviewParameterProvider {
                    fun values() = true && false
                }

                @Preview
                fun screenPreview() = true && false

                fun catalogDemo() = true && false

                fun production() = true && false
                """.trimIndent(),
            )
        }

        val functions = KotlinSourceAnalyzer().discover(source)

        assertEquals(listOf("production"), functions.map { it.name })
        assertEquals(3, KotlinSourceAnalyzer().discover(source, includePreviews = true).size)
    }
}
