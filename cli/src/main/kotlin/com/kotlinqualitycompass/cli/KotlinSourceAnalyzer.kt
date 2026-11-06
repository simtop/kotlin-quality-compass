package com.kotlinqualitycompass.cli

import com.kotlinqualitycompass.core.FunctionDescriptor
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import java.nio.file.Path
import kotlin.io.path.readText

class KotlinSourceAnalyzer {
    fun discover(file: Path, includePreviews: Boolean = false): List<FunctionDescriptor> {
        val source = file.readText()
        val disposable = Disposer.newDisposable("kotlin-quality-compass")
        return try {
            val psiFile = KtPsiFactory(environment(disposable).project, false)
                .createFile(file.fileName.toString(), source)
            val functions = mutableListOf<FunctionDescriptor>()
            psiFile.accept(object : KtTreeVisitorVoid() {
                override fun visitNamedFunction(function: KtNamedFunction) {
                    if (isAnalyzable(function, includePreviews)) functions += descriptor(file, source, psiFile, function)
                    super.visitNamedFunction(function)
                }
            })
            functions.sortedWith(compareBy<FunctionDescriptor> { it.startLine }.thenBy { it.name })
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun isAnalyzable(function: KtNamedFunction, includePreviews: Boolean): Boolean =
        function.hasBody() && function.bodyExpression != null &&
            function.parents.none { it is KtNamedFunction } &&
            !function.hasModifier(KtTokens.ABSTRACT_KEYWORD) &&
            !function.hasModifier(KtTokens.EXPECT_KEYWORD) &&
            !function.hasModifier(KtTokens.EXTERNAL_KEYWORD) &&
            !isDemoCode(function) && (includePreviews || isApplicationCode(function))

    private fun isApplicationCode(function: KtNamedFunction): Boolean =
        function.parents.none { parent ->
            when (parent) {
                is KtNamedFunction -> false
                is KtClassOrObject ->
                    parent.name?.contains("PreviewParameterProvider") == true ||
                        parent.superTypeListEntries.any {
                            it.text.contains("PreviewParameterProvider")
                        }
                else -> false
            }
        } &&
            function.name?.endsWith("Preview") != true &&
            function.annotationEntries.none {
                it.shortName?.asString()?.contains("Preview") == true
            }

    private fun isDemoCode(function: KtNamedFunction): Boolean =
        function.name?.endsWith("Demo") == true

    private fun descriptor(
        file: Path,
        source: String,
        psiFile: KtFile,
        function: KtNamedFunction,
    ): FunctionDescriptor {
        val body = requireNotNull(function.bodyExpression)
        val range = function.textRange
        val lineMap = psiFile.viewProvider.document
        val owner = function.parents.filterIsInstance<KtClassOrObject>().firstOrNull()
        return FunctionDescriptor(
            sourcePath = file.toString(),
            packageName = psiFile.packageFqName.asString(),
            ownerName = owner?.name,
            name = function.name ?: "<anonymous>",
            isInline = function.hasModifier(KtTokens.INLINE_KEYWORD),
            startLine = lineMap.getLineNumber(range.startOffset) + 1,
            endLine = lineMap.getLineNumber(body.textRange.endOffset) + 1,
            cyclomaticComplexity = complexity(body),
        )
    }

    private fun complexity(body: org.jetbrains.kotlin.psi.KtExpression): Int {
        val counter = ComplexityCounter()
        body.accept(counter)
        return counter.value
    }

    private class ComplexityCounter : KtTreeVisitorVoid() {
        var value: Int = 1
            private set

        override fun visitIfExpression(expression: KtIfExpression) {
            value++
            super.visitIfExpression(expression)
        }

        override fun visitWhenExpression(expression: KtWhenExpression) {
            value += expression.entries.count { !it.isElse }
            super.visitWhenExpression(expression)
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            // Local functions are separate declarations, not control flow in their enclosing
            // function. The outer analyzer also excludes them as report rows.
        }

        override fun visitLoopExpression(loopExpression: KtLoopExpression) {
            value++
            super.visitLoopExpression(loopExpression)
        }

        override fun visitCatchSection(catchClause: KtCatchClause) {
            value++
            super.visitCatchSection(catchClause)
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            if (expression.operationToken == KtTokens.ANDAND ||
                expression.operationToken == KtTokens.OROR
            ) {
                value++
            }
            super.visitBinaryExpression(expression)
        }
    }

    @OptIn(CompilerConfiguration.Internals::class, K1Deprecation::class)
    private fun environment(parent: Disposable): KotlinCoreEnvironment {
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "kotlin-quality-compass")
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        }
        return KotlinCoreEnvironment.createForProduction(
            parent,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
    }
}
