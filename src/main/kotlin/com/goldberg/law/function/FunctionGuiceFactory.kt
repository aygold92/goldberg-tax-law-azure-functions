package com.goldberg.law.function

import com.goldberg.law.AppModule
import com.google.inject.Guice
import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

class FunctionGuiceFactory : FunctionInstanceInjector {

    @Throws(Exception::class)
    override fun <T> getInstance(functionClass: Class<T>?): T {
        return INJECTOR.getInstance(functionClass)
    }

    companion object {
        init {
            configureLogging()
        }

        private val INJECTOR = Guice.createInjector(AppModule())

        private fun configureLogging() {
            System.setProperty(
                "java.util.logging.SimpleFormatter.format",
                "[%4\$s] %3\$s - %5\$s%6\$s%n"
            )
            val root = LogManager.getLogManager().getLogger("") ?: return
            root.handlers.filterIsInstance<ConsoleHandler>().forEach { root.removeHandler(it) }
            root.addHandler(ConsoleHandler().apply {
                formatter = SimpleFormatter()
                level = Level.ALL
            })
            Logger.getLogger("com.goldberg.law").level = Level.FINE  // DEBUG and above
            Logger.getLogger("com.zaxxer.hikari").level = Level.WARNING
            Logger.getLogger("com.azure").level = Level.WARNING
            Logger.getLogger("com.microsoft").level = Level.WARNING
        }
    }
}