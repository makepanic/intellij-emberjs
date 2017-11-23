package com.emberjs.configuration.test

import com.emberjs.icons.EmberIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.extensions.Extensions

class EmberTestConfigurationType : ConfigurationTypeBase(
        "EMBER_TEST_CONFIGURATION",
        "Ember Test",
        "Ember Test Configuration",
        EmberIcons.ICON_16
) {
    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(EmberTestConfigurationFactory(this))
    }

    companion object {
        fun getInstance(): EmberTestConfigurationType {
            return Extensions.findExtension(ConfigurationType.CONFIGURATION_TYPE_EP, EmberTestConfigurationType::class.java)
        }
    }
}