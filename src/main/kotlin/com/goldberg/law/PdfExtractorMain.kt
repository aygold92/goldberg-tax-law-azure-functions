package com.goldberg.law

import com.fasterxml.jackson.core.type.TypeReference
import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.document.*
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.PdfDocumentPage
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.getDocumentName
import com.goldberg.law.util.toStringDetailed
import com.google.inject.Guice
import com.google.inject.Injector
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject


class PdfExtractorMain @Inject constructor(
    private val dataManager: DataManager,
    private val classifier: DocumentClassifier,
    private val dataExtractor: DocumentDataExtractor,
    private val statementCreator: DocumentStatementCreator,
    private val accountNormalizer: AccountNormalizer,
    private val checkToStatementMatcher: CheckToStatementMatcher,
    private val accountSummaryCreator: AccountSummaryCreator,
    numWorkers: Int
) {
    private val logger = KotlinLogging.logger {}
    val workerPoolDispatcher = Dispatchers.IO.limitedParallelism(numWorkers)

    fun readPreloadedDoc(filename: String): String =
        Files.readString(Paths.get(javaClass.getResource("$PATH_TO_DOCS/$filename")!!.toURI()))

    fun loadModels(filename: String): List<StatementDataModel> {
        val docString = readPreloadedDoc(filename)
        return OBJECT_MAPPER.readValue(docString, object: TypeReference<List<StatementDataModel>>() {}).also {
            logger.debug { "successfully loaded $filename" }
        }
    }

    fun loadChecks(filename: String): List<CheckDataModel> {
        val docString = readPreloadedDoc(filename)
        return OBJECT_MAPPER.readValue(docString, object: TypeReference<List<CheckDataModel>>() {}).also {
            logger.debug { "successfully loaded $filename" }
        }
    }

    fun run(req: AnalyzeDocumentRequest) = runBlocking {
        val inputDocuments = loadInputDocuments(req)

        val documentDataModels = inputDocuments.map { doc ->
            async(workerPoolDispatcher) {
                processDocument(doc, req.classifiedTypeOverride, req.outputDirectory)
            }
        }.awaitAll()

        processStatementsAndChecks(
            req.outputDirectory, req.outputFile ?: req.inputFile,
            documentDataModels.mapNotNull { it as? StatementDataModel },
            documentDataModels.mapNotNull { it as? CheckDataModel }
        )
    }

    fun loadInputDocuments(req: AnalyzeDocumentRequest): List<PdfDocumentPage> =
        dataManager.findMatchingFiles(req.inputFile).flatMap {
            val desiredPages = if (req.allPages) null else req.getDesiredPages()
            dataManager.loadInputPdfDocumentPages(req.inputFile, desiredPages)
        }

    fun processDocument(inputDocument: PdfDocumentPage, classifiedTypeOverride: String?, outputDirectory: String?): DocumentDataModel {
        val classifiedDocument = if (classifiedTypeOverride != null) {
            logger.info { "Overriding classification as $classifiedTypeOverride as requested..." }
            inputDocument.withType(classifiedTypeOverride)
        } else {
            classifier.classifyDocument(inputDocument)
        }

        return when {
            classifiedDocument.isStatementDocument() -> dataExtractor.extractStatementData(classifiedDocument);
            classifiedDocument.isRelevant() -> dataExtractor.extractCheckData(classifiedDocument)
            else -> ExtraPageDataModel(classifiedDocument.toDocumentMetadata())
        }.also { dataModel ->
            try {
                dataManager.saveModel(inputDocument, dataModel, outputDirectory)
            } catch (ex: Throwable) {
                logger.error(ex) { "Exception writing model for ${inputDocument.nameWithPage}: $ex" }
            }
        }
    }

    fun processStatementsAndChecks(outputDirectory: String, outputFile: String, allStatementModels: List<StatementDataModel>, allCheckModels: List<CheckDataModel>): List<String> {
        val (statementModelsUpdatedAccounts, checksUpdatedAccounts) = accountNormalizer.normalizeAccounts(allStatementModels, allCheckModels)

        val statementsWithoutChecks = statementCreator.createBankStatements(statementModelsUpdatedAccounts)
            .sortedBy { it.statementDate }
            .onEach { if (it.isSuspicious()) logger.error { "Statement ${it.primaryKey} is suspicious" } }
        logger.debug { "=====Statements=====\n${statementsWithoutChecks.toStringDetailed()}" }

        val filePrefix = listOf(outputDirectory, outputFile.getDocumentName(), outputFile.getDocumentName()).joinToString("/")

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(statementsWithoutChecks, checksUpdatedAccounts)
        val summary = accountSummaryCreator.createSummary(finalStatements).also { logger.debug { it } }

        return listOf(
            dataManager.writeCheckSummaryToCsv("${filePrefix}_CheckSummary", checksUpdatedAccounts, finalStatements.getMissingChecks(), finalStatements.getChecksNotUsed(allCheckModels.map { it.checkDataKey }.toSet())),
            dataManager.writeAccountSummaryToCsv("${filePrefix}_AccountSummary", summary),
            dataManager.writeStatementSummaryToCsv("${filePrefix}_StatementSummary", finalStatements.map { it.toStatementSummary() }),
            dataManager.writeRecordsToCsv("${filePrefix}_Records", finalStatements),
        )
    }

    companion object {
        private const val PATH_TO_DOCS = "/com/goldberg/law"
        @JvmStatic
        fun main(args: Array<String>) {
            val request = AnalyzeDocumentRequest().parseArgs(args)
            val azureConfiguration = if (request.isProd) AzureConfiguration.PROD else AzureConfiguration.TEST
            val injector: Injector = Guice.createInjector(
                AppModule(AppEnvironmentSettings(azureConfiguration, ExecutionEnvironment.LOCAL))
            )
            injector.getInstance(PdfExtractorMain::class.java).run(request)
        }
    }
}