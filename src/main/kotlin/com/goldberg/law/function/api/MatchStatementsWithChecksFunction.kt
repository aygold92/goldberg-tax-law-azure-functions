package com.goldberg.law.function.api

import com.goldberg.law.database.service.TransactionService
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.MatchStatementsWithChecksRequest
import com.goldberg.law.function.api.model.TransactionCheckMatch
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class MatchStatementsWithChecksFunction @Inject constructor(
    private val transactionService: TransactionService,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), MatchStatementsWithChecksRequest::class.java)

        if (req.transactionCheckMatches.isEmpty()) {
            val matchingTransactionsChecks = transactionService.findMatchingChecks(req.clientId)
            transactionService.linkTransactionsWithChecks(matchingTransactionsChecks.map { tr ->
                TransactionCheckMatch(
                    tr.transactionId,
                    tr.checkId!!,
                )
            })
            request!!.createResponseBuilder(HttpStatus.OK)
                .body(matchingTransactionsChecks)
                .build()
        } else {
            transactionService.linkTransactionsWithChecks(req.transactionCheckMatches)
            request!!.createResponseBuilder(HttpStatus.OK)
                .body(req.transactionCheckMatches)
                .build()
        }
    } catch (ex: Exception) {
        logger.error(ex) { "Error matching statements with checks $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "MatchStatementsWithChecks"
    }
}

