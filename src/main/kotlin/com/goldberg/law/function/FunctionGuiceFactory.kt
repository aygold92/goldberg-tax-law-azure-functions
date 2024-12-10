package com.goldberg.law.function

import com.goldberg.law.AppEnvironmentSettings
import com.goldberg.law.AppModule
import com.goldberg.law.AzureConfiguration
import com.google.inject.Guice
import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector

class FunctionGuiceFactory : FunctionInstanceInjector {

    @Throws(Exception::class)
    override fun <T> getInstance(functionClass: Class<T>?): T {
        return INJECTOR.getInstance(functionClass)
    }

    companion object {
        private val INJECTOR = Guice.createInjector(AppModule(AppEnvironmentSettings(AzureConfiguration.valueOf(System.getenv("AzureConfigurationStage")))))
    }
}