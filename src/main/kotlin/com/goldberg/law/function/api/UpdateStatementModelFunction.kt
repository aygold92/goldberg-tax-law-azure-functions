package com.goldberg.law.function.api

import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.database.service.TransactionService
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.UpdateStatementModelRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import java.util.*

class UpdateStatementModelFunction @Inject constructor(
    private val classificationService: ClassificationService,
    private val statementService: StatementService,
    private val transactionService: TransactionService,
    private val db: Database,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), UpdateStatementModelRequest::class.java)

        // check if statementId exists TODO: don't load the whole statement
        val current = statementService.loadBankStatement(req.statementDetails.statementId)

        db.txnSafe {
            if (current.toClassifiedPages() != req.classification) {
                classificationService.updateClassification(req.classificationId, req.classification)
            }
            statementService.updateBankStatement(req.statementDetails)
            transactionService.upsertTransactions(req.statementDetails.statementId, req.upserts)
            transactionService.deleteTransactions(req.deletes)
        }

        request!!.createResponseBuilder(HttpStatus.OK)
//            .body()
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error updating statement $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "UpdateStatementModels"
    }
}