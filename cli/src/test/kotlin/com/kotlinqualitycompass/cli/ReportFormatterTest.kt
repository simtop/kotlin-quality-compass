package com.kotlinqualitycompass.cli

import com.kotlinqualitycompass.core.CoverageMeasurement
import com.kotlinqualitycompass.core.CoverageStatus
import com.kotlinqualitycompass.core.FunctionDescriptor
import com.kotlinqualitycompass.core.FunctionMetric
import kotlin.test.Test
import kotlin.test.assertContains

class ReportFormatterTest {
    @Test
    fun json_contains_schema_summary_notes_and_safe_escaping() {
        val metric = FunctionMetric(
            function = FunctionDescriptor(
                sourcePath = "feature/src/main/kotlin/sample/Example.kt",
                packageName = "sample",
                ownerName = "Example\tOwner",
                name = "run\"now",
                startLine = 3,
                endLine = 8,
                cyclomaticComplexity = 2,
            ),
            coverage = CoverageMeasurement(
                percentage = 50.0,
                status = CoverageStatus.APPROXIMATE_LINE_SPAN,
                note = "line\r\nnote",
            ),
            score = 2.25,
        )

        val report = ReportFormatter.json(listOf(metric), threshold = 2.0)

        assertContains(report, "\"schemaVersion\": 1")
        assertContains(report, "\"functionCount\": 1")
        assertContains(report, "\"thresholdExceeded\": true")
        assertContains(report, "Example\\tOwner.run\\\"now")
        assertContains(report, "line\\r\\nnote")
    }

    @Test
    fun text_uses_project_relative_source_path() {
        val metric = FunctionMetric(
            function = FunctionDescriptor(
                sourcePath = "feature/src/main/kotlin/sample/Example.kt",
                packageName = "sample",
                ownerName = null,
                name = "run",
                startLine = 3,
                endLine = 3,
                cyclomaticComplexity = 1,
            ),
            coverage = CoverageMeasurement(100.0, CoverageStatus.APPROXIMATE_LINE_SPAN),
            score = 1.0,
        )

        val report = ReportFormatter.text(listOf(metric), threshold = 8.0)

        assertContains(report, "feature/src/main/kotlin/sample/Example.kt:3")
    }
}
