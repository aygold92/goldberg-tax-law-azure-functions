package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.BankStatementKey
import com.goldberg.law.function.model.request.LoadBankStatementRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class LoadBankStatementFunction(
    private val dataManager: AzureStorageDataManager,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), LoadBankStatementRequest::class.java)

        val key = BankStatementKey(req.accountNumber, req.classification, req.date?.replace("/", "_")).toString()
        val statement: BankStatement = dataManager.loadBankStatement(req.clientName, key)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(statement)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error loading bank statement for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to ex.message))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "LoadBankStatement"
    }
} 