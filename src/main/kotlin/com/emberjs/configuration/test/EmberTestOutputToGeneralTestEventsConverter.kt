package com.emberjs.configuration.test

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputLineSplitter
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.openapi.util.Key

class EmberTestOutputToGeneralTestEventsConverter(testFrameworkName: String, consoleProperties: TestConsoleProperties) :
        OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {
    var splitter: OutputLineSplitter;

    private val REGEX_TEAMCITY_LINE = """^##teamcity.*""".toRegex(RegexOption.DOT_MATCHES_ALL)

    init {
        splitter = object : OutputLineSplitter(true) {
            override fun onLineAvailable(text: String, outputType: Key<*>, tcLikeFakeOutput: Boolean) {
                subProcessConsistentText(text, outputType, tcLikeFakeOutput);
            }
        }
    }

    fun subProcessConsistentText(text: String, outputType: Key<*>, tcLikeFakeOutput: Boolean) {
        if (REGEX_TEAMCITY_LINE.matches(text)) {
            super.processConsistentText(text, ProcessOutputTypes.STDOUT, false)
        }
    }

    override fun process(text: String?, outputType: Key<*>?) {
        splitter.process(text, outputType);
    }
}