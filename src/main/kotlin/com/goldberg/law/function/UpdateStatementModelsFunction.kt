package com.goldberg.law.function

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.document.model.ManualRecord
import com.goldberg.law.document.model.ManualRecordTable
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
import com.goldberg.law.function.activity.ProcessStatementsActivity
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.activity.ProcessStatementsActivityInput
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.UpdateStatementModelsRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.mapAsync
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class UpdateStatementModelsFunction(
    private val dataManager: AzureStorageDataManager,
    private val processStatementsActivity: ProcessStatementsActivity,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), UpdateStatementModelsRequest::class.java)

        val metadata = dataManager.loadInputMetadata(req.clientName, req.modelDetails.details.filename)
            ?: throw FileNotFoundException("file ${req.clientName}/${req.modelDetails.details.filename} does not exist")

        val details = req.modelDetails.details

        val transactionsGrouped = req.modelDetails.transactions.groupBy { it.filePageNumber }

        // pages represents all the relevant pages
        val newModels = req.modelDetails.pages.mapAsync { page ->
            val manualRecords = transactionsGrouped.getOrDefault(page.filePageNumber, listOf()).map { tr ->
                // TODO: when running the normal document analyzer it will ignore the check page data
                ManualRecord(tr.id, tr.date, tr.description, tr.amount, tr.checkNumber, tr.checkPageData)
            }
            val model = StatementDataModel(
                date = details.statementDate,
                pageNum = page.statementPageNum,
                totalPages = null, // TODO
                batesStamp = page.batesStamp,
                accountNumber = details.accountNumber,
                beginningBalance = details.beginningBalance,
                endingBalance = details.endingBalance,
                interestCharged = details.interestCharged,
                feesCharged = details.feesCharged,
                pageMetadata = PdfDocumentPageMetadata(details.filename, page.filePageNumber, details.classification),
                manualRecordTable = if (manualRecords.isEmpty()) null else ManualRecordTable(manualRecords)
            )
            // TODO: for NFCU the model has 2 statements in one, and this will overwrite the other statement
            dataManager.saveModel(req.clientName, model)
            model
        }

        // load the checks
        val checkModels = req.modelDetails.transactions.filter { it.checkPageData != null }.distinct().mapAsync {
            dataManager.loadModel(req.clientName, it.checkPageData!!)
        }

        val documentDataModels = newModels.map { DocumentDataModelContainer(statementDataModel = it) }
            .union(checkModels.map { DocumentDataModelContainer(it) })


        val ret = processStatementsActivity.processStatementsAndChecks(ProcessStatementsActivityInput(
            ctx.invocationId!!, req.clientName, documentDataModels, mapOf(req.modelDetails.details.filename to metadata), true
        ), ctx)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(ret)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error updating statement $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "UpdateStatementModels"
    }
}