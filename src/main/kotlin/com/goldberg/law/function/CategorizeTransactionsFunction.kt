package com.goldberg.law.function

import com.goldberg.law.categorization.TransactionCategorizer
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.CategorizeTransactionsRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class CategorizeTransactionsFunction(
    private val transactionCategorizer: TransactionCategorizer,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), CategorizeTransactionsRequest::class.java)

        val result = transactionCategorizer.batchCategorizeTransactions(req.transactions)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(result)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error categorizing transactions" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "CategorizeTransactions"
    }
}