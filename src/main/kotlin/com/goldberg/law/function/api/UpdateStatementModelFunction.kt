package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.document.model.ManualRecord
import com.goldberg.law.document.model.ManualRecordTable
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.BatesStampTable
import com.goldberg.law.document.model.input.tables.BatesStampTableRow
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.function.activity.ProcessStatementsActivity
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.activity.ProcessStatementsActivityInput
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.UpdateStatementModelRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.mapAsync
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class UpdateStatementModelFunction(
    private val dataManager: AzureStorageDataManager,
    private val processStatementsActivity: ProcessStatementsActivity,
    private val putDocumentClassificationFunction: PutDocumentClassificationFunction,
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

        val metadata = dataManager.loadInputMetadata(req.clientName, req.modelDetails.details.filename)
            ?: throw FileNotFoundException("file ${req.clientName}/${req.modelDetails.details.filename} does not exist")


        val details = req.modelDetails.details

        val pages = req.modelDetails.pages.map { it.filePageNumber }.toSet()
        val pdfMetadata = ClassifiedPdfMetadata(details.filename, pages, details.classification)

        val model = StatementDataModel(
            date = details.statementDate,
            accountNumber = details.accountNumber,
            beginningBalance = details.beginningBalance,
            endingBalance = details.endingBalance,
            interestCharged = details.interestCharged,
            feesCharged = details.feesCharged,
            pageMetadata = pdfMetadata,
            manualRecordTable = ManualRecordTable(req.modelDetails.transactions.map { tr ->
                // TODO: should I get rid of PdfPageData in checkPageData?
                ManualRecord(tr.id, tr.date, tr.description, tr.amount, tr.checkNumber, tr.checkPdfMetadata, tr.filePageNumber)
            }),
            batesStamps = BatesStampTable(req.modelDetails.pages.mapNotNull { if (it.batesStamp != null) BatesStampTableRow(it.batesStamp, it.filePageNumber) else null })
        )

        dataManager.saveModel(req.clientName, model)

        putDocumentClassificationFunction.overwriteClassificationIndividual(req.clientName, listOf(pdfMetadata))

        // load the checks
        val checkModels = req.modelDetails.transactions.filter { it.checkPdfMetadata != null && it.checkPdfMetadata.filename.length > 0 }.distinct().mapAsync {
            dataManager.loadModel(req.clientName, it.checkPdfMetadata!!)
        }

        val documentDataModels = listOf(DocumentDataModelContainer(statementDataModel = model))
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