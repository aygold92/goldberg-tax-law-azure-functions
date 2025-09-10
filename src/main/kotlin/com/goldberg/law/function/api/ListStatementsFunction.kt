package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.model.request.ListStatementsRequest
import com.goldberg.law.function.model.metadata.StatementMetadata
import com.goldberg.law.function.model.metadata.BankStatementMetadata
import com.goldberg.law.document.model.output.BankStatementKey
import com.goldberg.law.util.OBJECT_MAPPER
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ListStatementsFunction(
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
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), ListStatementsRequest::class.java)

        // Fetch all statement metadata for the client
        val statementsMap: Map<String, StatementMetadata> = dataManager.listStatementsWithMetadata(req.clientName)

        val result = statementsMap.map { (filename, metadata) ->
            val key = parseBankStatementKey(filename)
            BankStatementMetadata(key, metadata)
        }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(result)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error listing statements for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to ex.message))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ListStatements"
    }

    private fun parseBankStatementKey(filename: String): BankStatementKey? {
        val base = filename.removeSuffix(".json")
        return try {
            BankStatementKey.fromString(base)
        } catch (e: Exception) {
            null
        }
    }
} 