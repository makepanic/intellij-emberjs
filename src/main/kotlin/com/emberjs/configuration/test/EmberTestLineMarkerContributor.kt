package com.emberjs.configuration.test

import com.emberjs.configuration.test.EmberTestRunConfigurationProducer.Companion.elementTestModuleName
import com.emberjs.configuration.test.EmberTestRunConfigurationProducer.Companion.elementBlockTestName
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function

class EmberTestLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {

        if (elementTestModuleName(element) != null || elementBlockTestName(element) != null) {

            val tooltipProvider: Function<PsiElement, String> = Function({ "Run Test" })

            return Info(
                    AllIcons.RunConfigurations.TestState.Run,
                    tooltipProvider,
                    // `1` here will prefer test configuration over application configuration,
                    // when both a applicable. Usually configurations are ordered by their target
                    // PSI elements (smaller element means more specific), but this is not the case here.
                    *ExecutorAction.getActions(1)
            )
        }

        return null
    }

}