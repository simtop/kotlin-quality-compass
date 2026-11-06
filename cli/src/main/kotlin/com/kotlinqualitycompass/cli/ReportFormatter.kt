package com.kotlinqualitycompass.cli

import com.kotlinqualitycompass.core.FunctionMetric
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Path
import java.util.Locale

object ReportFormatter {
    fun text(metrics: List<FunctionMetric>, threshold: Double): String =
        StringWriter().also { writeText(metrics, threshold, it) }.toString()

    fun json(metrics: List<FunctionMetric>, threshold: Double): String =
        StringWriter().also { writeJson(metrics, threshold, it) }.toString()

    fun write(format: OutputFormat, metrics: List<FunctionMetric>, threshold: Double, writer: Writer) {
        when (format) {
            OutputFormat.TEXT -> writeText(metrics, threshold, writer)
            OutputFormat.JSON -> writeJson(metrics, threshold, writer)
        }
        writer.flush()
    }

    fun writeText(metrics: List<FunctionMetric>, threshold: Double, writer: Writer) {
        writer.appendLine("Kotlin Quality Compass")
        writer.appendLine("Function metrics (CRAP-style score threshold: $threshold)")
        writer.appendLine("Function                         File                                        CC    Cov%     Score")
        writer.appendLine("-".repeat(105))
        metrics.forEach { metric ->
            val function = metric.function
            val identity = listOfNotNull(function.ownerName, function.name).joinToString(".")
            writer.appendLine(
                String.format(
                    Locale.ROOT,
                    "%-32s %-43s %4d %7s %9s",
                    identity.take(32),
                    "${Path.of(function.sourcePath).toString().replace('\\', '/')}:${function.startLine}".take(43),
                    function.cyclomaticComplexity,
                    metric.coverage.percentage?.let { "%.1f".format(Locale.ROOT, it) } ?: "N/A",
                    metric.score?.let { "%.2f".format(Locale.ROOT, it) } ?: "N/A",
                ),
            )
        }
        val worst = metrics.mapNotNull { it.score }.maxOrNull()
        writer.appendLine()
        writer.appendLine(
            "Summary: ${metrics.size} functions, scored=${metrics.count { it.score != null }}, " +
                "unavailable=${metrics.count { it.score == null }}, " +
                "worst score=${worst?.let { "%.2f".format(Locale.ROOT, it) } ?: "N/A"}",
        )
        metrics.filter { it.coverage.note != null }.distinctBy { it.coverage.note }.forEach {
            writer.appendLine("Note: ${it.coverage.note}")
        }
        if (worst != null && worst > threshold) writer.appendLine("Threshold exceeded")
    }

    fun writeJson(metrics: List<FunctionMetric>, threshold: Double, writer: Writer) {
        val worst = metrics.mapNotNull { it.score }.maxOrNull()
        val scored = metrics.count { it.score != null }
        writer.appendLine("{")
        writer.appendLine("  \"schemaVersion\": 1,")
        writer.appendLine("  \"tool\": \"kotlin-quality-compass\",")
        writer.appendLine("  \"threshold\": $threshold,")
        writer.appendLine("  \"summary\": {")
        writer.appendLine("    \"functionCount\": ${metrics.size},")
        writer.appendLine("    \"scoredFunctionCount\": $scored,")
        writer.appendLine("    \"unavailableFunctionCount\": ${metrics.size - scored},")
        writer.appendLine("    \"worstScore\": ${worst ?: "null"},")
        writer.appendLine("    \"thresholdExceeded\": ${worst != null && worst > threshold}")
        writer.appendLine("  },")
        writer.appendLine("  \"metrics\": [")
        metrics.forEachIndexed { index, metric ->
            val function = metric.function
            val comma = if (index == metrics.lastIndex) "" else ","
            writer.appendLine("    {")
            writer.appendLine("      \"source\": \"${escape(function.sourcePath)}\",")
            writer.appendLine(
                "      \"function\": \"${escape(listOfNotNull(function.ownerName, function.name).joinToString("."))}\",",
            )
            writer.appendLine("      \"startLine\": ${function.startLine},")
            writer.appendLine("      \"endLine\": ${function.endLine},")
            writer.appendLine("      \"cyclomaticComplexity\": ${function.cyclomaticComplexity},")
            writer.appendLine("      \"coveragePercentage\": ${metric.coverage.percentage ?: "null"},")
            writer.appendLine("      \"coverageStatus\": \"${metric.coverage.status}\",")
            writer.appendLine("      \"coverageNote\": ${metric.coverage.note?.let { "\"${escape(it)}\"" } ?: "null"},")
            writer.appendLine("      \"score\": ${metric.score ?: "null"}")
            writer.appendLine("    }$comma")
        }
        writer.appendLine("  ]")
        writer.appendLine("}")
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> append("\\u%04x".format(Locale.ROOT, character.code))
                else -> append(character)
            }
        }
    }
}
