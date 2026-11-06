package com.kotlinqualitycompass.cli

import com.kotlinqualitycompass.core.CoverageMeasurement
import com.kotlinqualitycompass.core.CoverageStatus
import com.kotlinqualitycompass.core.FunctionDescriptor
import org.w3c.dom.Element
import java.io.StringReader
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.io.path.exists

class JaCoCoCoverage private constructor(
    private val sourceLines: Map<String, Map<Int, InstructionCount>>,
) {
    fun measure(function: FunctionDescriptor): CoverageMeasurement {
        val candidates = sourceCandidates(Path.of(function.sourcePath))
        val lines = candidates.firstNotNullOfOrNull { sourceLines[it] }
            ?: return CoverageMeasurement(null, CoverageStatus.UNAVAILABLE, "No JaCoCo source file match")
        val selected = lines.filterKeys { it in function.startLine..function.endLine }.values
        if (selected.isEmpty()) {
            return CoverageMeasurement(null, CoverageStatus.UNAVAILABLE, "No JaCoCo lines in function span")
        }
        val total = selected.sumOf { it.missed + it.covered }
        if (total == 0) {
            return CoverageMeasurement(null, CoverageStatus.UNAVAILABLE, "JaCoCo reported no instructions")
        }
        val covered = selected.sumOf { it.covered }
        return CoverageMeasurement(
            percentage = covered * 100.0 / total,
            status = CoverageStatus.APPROXIMATE_LINE_SPAN,
            note = "Derived from JaCoCo source lines; Kotlin generated methods may not map exactly",
        )
    }

    companion object {
        fun read(report: Path): JaCoCoCoverage {
            require(report.exists()) { "JaCoCo report does not exist: $report" }
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                // JaCoCo writes a local report DOCTYPE. External entities remain disabled and the
                // resolver below prevents the parser from reading anything from the filesystem or network.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> InputSource(StringReader("")) }
            }
            val document = builder.parse(report.toFile())
            val result = mutableMapOf<String, MutableMap<Int, InstructionCount>>()
            val packages = document.getElementsByTagName("package")
            for (packageIndex in 0 until packages.length) {
                val packageElement = packages.item(packageIndex) as Element
                val packageName = packageElement.getAttribute("name").trim('/').replace('.', '/')
                val sourceFiles = packageElement.getElementsByTagName("sourcefile")
                for (sourceIndex in 0 until sourceFiles.length) {
                    val sourceElement = sourceFiles.item(sourceIndex) as Element
                    val sourceName = sourceElement.getAttribute("name")
                    val key = listOf(packageName, sourceName).filter { it.isNotBlank() }.joinToString("/")
                    val lines = result.getOrPut(key) { mutableMapOf() }
                    val lineNodes = sourceElement.getElementsByTagName("line")
                    for (lineIndex in 0 until lineNodes.length) {
                        val line = lineNodes.item(lineIndex) as Element
                        val lineNumber = line.getAttribute("nr").toIntOrNull() ?: continue
                        val missed = line.getAttribute("mi").toIntOrNull() ?: 0
                        val covered = line.getAttribute("ci").toIntOrNull() ?: 0
                        lines[lineNumber] = InstructionCount(missed, covered)
                    }
                }
            }
            return JaCoCoCoverage(result)
        }
    }

    private fun sourceCandidates(source: Path): List<String> {
        val normalized = source.toString().replace('\\', '/')
        val sourceRoot = normalized.substringAfter("/src/", "")
        val parts = sourceRoot.split('/')
        val relative = parts.takeIf { it.size >= 3 && it[1] in setOf("kotlin", "java") }
            ?.drop(2)
            ?.joinToString("/")
            ?.takeIf(String::isNotEmpty)
        val basename = source.fileName.toString()
        val basenameMatches = sourceLines.keys.filter { it.substringAfterLast('/') == basename }
        return listOfNotNull(relative) + basenameMatches.singleOrNull()?.let(::listOf).orEmpty()
    }

    private data class InstructionCount(val missed: Int, val covered: Int)
}
