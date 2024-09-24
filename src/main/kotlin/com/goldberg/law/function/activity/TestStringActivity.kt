package com.goldberg.law.function.activity

import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging

class TestStringActivity {
    private val logger = KotlinLogging.logger {}
    @FunctionName(FUNCTION_NAME)
    fun testStringActivity(@DurableActivityTrigger(name = "input") input: String, context: ExecutionContext): String {
        logger.info { "[${context.invocationId}] processing $input" }
        return (input + input).also {
            logger.info { "[${context.invocationId}] returning $it" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "TestStringActivity"
    }
}