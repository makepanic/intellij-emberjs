package com.emberjs.configuration.test

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NotNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSRecursiveElementVisitor
import com.intellij.lang.javascript.psi.impl.JSCallExpressionImpl
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.annotations.Nullable
import java.io.File
import com.intellij.psi.PsiFile

public class EmberTestRunConfigurationProducer : RunConfigurationProducer<EmberTestConfiguration>(
        EmberTestConfigurationType.getInstance()
) {
    private val UNKNOWN_LINE = -1

    override fun setupConfigurationFromContext(
            configuration: EmberTestConfiguration,
            context: ConfigurationContext,
            sourceElement: Ref<PsiElement>): Boolean {

        val location = sourceElement.get()

        return location != null && location.isValid &&
                setupConfigurationFromContextImpl(configuration, location);
    }

    override fun isConfigurationFromContext(@NotNull configuration: EmberTestConfiguration, context: ConfigurationContext?): Boolean {
        val location = context!!.psiLocation

        return location != null && location.isValid &&
                isConfigurationFromContextImpl(configuration, location)
    }

    fun setupConfigurationFromContextImpl(
            @NotNull configuration: EmberTestConfiguration,
            @NotNull psiElement: PsiElement): Boolean {

        var contextApplicable = false

        if (psiElement is PsiDirectory) {
            // afaik it's not possible to run a test directory
        } else {
            val containingFile = psiElement.containingFile

            val isEmberTestFile = containingFile.name.endsWith("-test.js")
            // if the psiElement is the whole file instead of a text range in the file

            if (ProjectRootsUtil.isInTestSource(containingFile) && isEmberTestFile) {

                val basePath = psiElement.project.basePath

                workingDirectory(psiElement, basePath)?.let {
                    configuration.workingDirectory = it
                    configuration.name = configurationName(containingFile, lineNumber(psiElement), it, basePath)

                    contextApplicable = true
                }

                if (psiElement == containingFile) {
                    // is file -> visit nodes starting from file
                    findModuleTests(psiElement.containingFile)?.let {
                        configuration.options.module.value = it
                        configuration.options.filterOption.value = FilterType.MODULE.value
                        configuration.name = it
                    }
                } else {
                    // try to find test from element
                    findModuleTests(psiElement)?.let {
                        configuration.options.module.value = it
                        configuration.options.filterOption.value = FilterType.MODULE.value
                        configuration.name = it
                    } ?: findTestBlocks(psiElement)?.let {
                        configuration.options.filter.value = it
                        configuration.options.filterOption.value = FilterType.FILTER.value
                        configuration.name = it
                    }
                }
            }
        }

        return contextApplicable

    }

    private fun isConfigurationFromContextImpl(
            @NotNull configuration: EmberTestConfiguration,
            @NotNull psiElement: PsiElement): Boolean {
        val containingFile = psiElement.containingFile
        val vFile = containingFile?.virtualFile ?: return false

        val lineNumber = lineNumber(psiElement)
        val workingDirectory = configuration.workingDirectory

        return StringUtil.equals(
                configuration.name,
                configurationName(
                        containingFile,
                        lineNumber,
                        workingDirectory,
                        psiElement.project.basePath
                )
        )
    }

    private fun lineNumber(psiElement: PsiElement): Int {
        val containingFile = psiElement.containingFile
        val project = containingFile.project
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val document = psiDocumentManager.getDocument(containingFile)
        var documentLineNumber = 0

        if (document != null) {
            val textOffset = psiElement.textOffset
            documentLineNumber = document.getLineNumber(textOffset)
        }

        val lineNumber: Int

        if (documentLineNumber == 0) {
            lineNumber = UNKNOWN_LINE
        } else {
            lineNumber = documentLineNumber + 1
        }

        return lineNumber
    }

    private fun configurationName(file: PsiFileSystemItem,
                                  @Nullable workingDirectory: String?,
                                  @Nullable basePath: String?): String {
        val filePath = file.virtualFile.path
        var suffix: String? = null

        if (workingDirectory != null) {
            val prefix = workingDirectory + File.separator

            if (filePath.startsWith(prefix)) {
                suffix = filePath.substring(prefix.length)
            }

            if (basePath != null && workingDirectory != basePath && workingDirectory.startsWith(basePath)) {
                val otpAppName = File(workingDirectory).getName()

                suffix = otpAppName + " " + suffix
            }
        }

        if (suffix == null) {
            suffix = file.name
        }

        return "Test: " + suffix
    }

    private fun configurationName(file: PsiFileSystemItem,
                                  lineNumber: Int,
                                  workingDirectory: String?,
                                  basePath: String?): String {
        return if (lineNumber == UNKNOWN_LINE) {
            configurationName(file, workingDirectory, basePath)
        } else {
            configurationName(file, workingDirectory, basePath) + ":" + lineNumber
        }
    }

    private fun workingDirectory(directory: PsiDirectory, basePath: String?): String? {
        val workingDirectory: String?

        if (directory.findFile("mix.exs") != null) {
            workingDirectory = directory.virtualFile.path
        } else {
            val parent = directory.parent

            if (parent != null) {
                workingDirectory = workingDirectory(parent, basePath)
            } else {
                workingDirectory = basePath
            }
        }

        return workingDirectory
    }

    private fun workingDirectory(element: PsiElement, basePath: String?): String? {
        val workingDirectory: String?

        if (element is PsiDirectory) {
            workingDirectory = workingDirectory(element, basePath)
        } else if (element is PsiFile) {
            workingDirectory = workingDirectory(element, basePath)
        } else {
            workingDirectory = workingDirectory(element.containingFile, basePath)
        }

        return workingDirectory
    }

    private fun workingDirectory(file: PsiFile, basePath: String?): String? {
        return workingDirectory(file.containingDirectory, basePath)
    }

    companion object {
        val moduleForVariants = arrayOf(
                "moduleFor",
                "moduleForModel",
                "moduleForComponent"
        )
        val testBlockVariants = arrayOf(
                "test"
        )

        fun findModuleTests(element: PsiElement): String? {
            var module: String? = null

            element.acceptChildren(object : JSRecursiveElementVisitor() {
                override fun visitJSCallExpression(node: JSCallExpression?) {
                    super.visitJSCallExpression(node)

                    val callExpression = element.firstChild as? JSCallExpressionImpl ?: return

                    expressionTestModuleName(callExpression)?.let {
                        module = it
                    }
                }
            })

            return module
        }

        fun findTestBlocks(element: PsiElement): String? {
            var module: String? = null

            element.acceptChildren(object : JSRecursiveElementVisitor() {
                override fun visitJSCallExpression(node: JSCallExpression?) {
                    super.visitJSCallExpression(node)

                    val callExpression = element.firstChild as? JSCallExpressionImpl ?: return

                    expressionBlockTestName(callExpression)?.let {
                        module = it
                    }
                }
            })

            return module
        }

        fun expressionTestModuleName(expression: JSCallExpressionImpl): String? {
            if (expression.firstChild.text !in moduleForVariants) return null

            val arguments = expression.argumentList?.arguments ?: return null
            if (arguments.size < 2) return null

            val literalExpression = arguments[1] as? JSLiteralExpression ?: return null
            if (!literalExpression.isQuotedLiteral) return null

            return literalExpression.value as String
        }

        fun expressionBlockTestName(expression: JSCallExpressionImpl): String? {
            if (expression.firstChild.text !in testBlockVariants) return null

            val arguments = expression.argumentList?.arguments ?: return null
            if (arguments.isEmpty()) return null

            val literalExpression = arguments[0] as? JSLiteralExpression ?: return null
            if (!literalExpression.isQuotedLiteral) return null

            return literalExpression.value as String
        }

        fun elementTestModuleName(element: PsiElement?): String? {
            val callExpression = element?.firstChild as? JSCallExpressionImpl ?: return null

            return expressionTestModuleName(callExpression)
        }

        fun elementBlockTestName(element: PsiElement?): String? {
            val callExpression = element?.firstChild as? JSCallExpressionImpl ?: return null

            return expressionBlockTestName(callExpression)
        }
    }
}
