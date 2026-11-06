package com.kotlinqualitycompass.cli

import com.kotlinqualitycompass.core.FunctionDescriptor
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JaCoCoCoverageTest {
    @Test
    fun maps_custom_kmp_source_sets_without_basename_fallback() {
        val report = createTempFile(suffix = ".xml").apply {
            writeText(
                """
                <report name="sample">
                  <package name="sample">
                    <sourcefile name="Ios.kt">
                      <line nr="3" mi="0" ci="4"/>
                    </sourcefile>
                  </package>
                </report>
                """.trimIndent(),
            )
        }
        val function = FunctionDescriptor(
            sourcePath = Path.of("/tmp/sample/src/iosMain/kotlin/sample/Ios.kt").toString(),
            packageName = "sample",
            ownerName = null,
            name = "run",
            startLine = 3,
            endLine = 3,
            cyclomaticComplexity = 1,
        )

        assertEquals(100.0, JaCoCoCoverage.read(report).measure(function).percentage)
    }

    @Test
    fun reads_jacoco_doctype_and_maps_a_function_line_span() {
        val report = createTempFile(suffix = ".xml").apply {
            writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                <report name="sample">
                  <package name="sample">
                    <sourcefile name="Example.kt">
                      <line nr="3" mi="0" ci="4"/>
                      <line nr="4" mi="4" ci="0"/>
                    </sourcefile>
                  </package>
                </report>
                """.trimIndent(),
            )
        }
        val function = FunctionDescriptor(
            sourcePath = Path.of("/tmp/sample/src/main/kotlin/sample/Example.kt").toString(),
            packageName = "sample",
            ownerName = "Example",
            name = "run",
            startLine = 3,
            endLine = 4,
            cyclomaticComplexity = 1,
        )

        val measurement = JaCoCoCoverage.read(report).measure(function)

        assertEquals(50.0, measurement.percentage)
        assertNotNull(measurement.note)
    }
}
