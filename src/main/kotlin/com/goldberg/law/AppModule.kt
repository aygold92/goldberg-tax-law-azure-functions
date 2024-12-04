package com.goldberg.law

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder
import com.azure.core.credential.AzureKeyCredential
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.datamanager.FileDataManager
import com.goldberg.law.document.*
import com.goldberg.law.function.PdfDataExtractorOrchestratorFunction
import com.goldberg.law.function.WriteCsvSummaryFunction
import com.goldberg.law.function.activity.*
import com.goldberg.law.document.PdfSplitter
import com.goldberg.law.function.FetchSASTokenFunction
import com.google.inject.AbstractModule
import com.google.inject.Provides
import javax.inject.Singleton

class AppModule constructor(private val appEnvironmentSettings: AppEnvironmentSettings) : AbstractModule() {
    override fun configure() {

    }

    @Provides
    @Singleton
    fun documentAnalysisClient(): DocumentAnalysisClient = DocumentAnalysisClientBuilder()
        .endpoint(appEnvironmentSettings.azureConfig.apiEndpoint)
        .credential(AzureKeyCredential(appEnvironmentSettings.azureConfig.apiKey))
        .buildClient()

    @Provides
    @Singleton
    fun storageServiceClient(): BlobServiceClient = BlobServiceClientBuilder()
        .connectionString(appEnvironmentSettings.azureConfig.storageBlobConnectionString)
        .buildClient()


    @Provides
    @Singleton
    fun dataManager(pdfSplitter: PdfSplitter, blobServiceClient: BlobServiceClient): DataManager =
        if (appEnvironmentSettings.executionEnvironment == ExecutionEnvironment.LOCAL) FileDataManager(pdfSplitter)
        else AzureStorageDataManager(pdfSplitter, blobServiceClient, appEnvironmentSettings.azureConfig.storageBlobConfig.containerNames)

    /** these should not have to instantiated here, I have no idea why guice can't find the @Inject constructor **/
    @Provides
    @Singleton
    fun documentClassifier(documentAnalysisClient: DocumentAnalysisClient): DocumentClassifier =
        DocumentClassifier(documentAnalysisClient, appEnvironmentSettings.azureConfig.classifierModel)

    @Provides
    @Singleton
    fun documentDataExtractor(documentAnalysisClient: DocumentAnalysisClient): DocumentDataExtractor =
        DocumentDataExtractor(
            documentAnalysisClient,
            appEnvironmentSettings.azureConfig.dataExtractorModel,
            appEnvironmentSettings.azureConfig.checkExtractorModel
        )

    @Provides
    @Singleton
    fun pdfExtractorMain(
        dataManager: DataManager,
        classifier: DocumentClassifier,
        dataExtractor: DocumentDataExtractor,
        statementCreator: DocumentStatementCreator,
        accountNormalizer: AccountNormalizer,
        checkToStatementMatcher: CheckToStatementMatcher,
        accountSummaryCreator: AccountSummaryCreator
    ) = PdfExtractorMain(
        dataManager,
        classifier,
        dataExtractor,
        statementCreator,
        accountNormalizer,
        checkToStatementMatcher,
        accountSummaryCreator,
        appEnvironmentSettings.azureConfig.numWorkers
    )

    @Provides
    @Singleton
    fun pdfDataExtractorOrchestratorFunction() = PdfDataExtractorOrchestratorFunction(appEnvironmentSettings.azureConfig.numWorkers)

    @Provides
    @Singleton
    fun splitPdfFunction(
        dataManager: DataManager,
    ) = SplitPdfActivity(dataManager)

    @Provides
    @Singleton
    fun processDataModelFunction(
        classifier: DocumentClassifier,
        dataExtractor: DocumentDataExtractor,
        dataManager: DataManager
    ) = ProcessDataModelActivity(classifier, dataExtractor, dataManager)

    @Provides
    @Singleton
    fun getRelevantFilesActivity(
        dataManager: DataManager
    ) = GetFilesToProcessActivity(dataManager)

    @Provides
    @Singleton
    fun processStatementActivity(
        statementCreator: DocumentStatementCreator,
        accountNormalizer: AccountNormalizer,
        checkToStatementMatcher: CheckToStatementMatcher,
        dataManager: DataManager
    ) = ProcessStatementsActivity(statementCreator, accountNormalizer, checkToStatementMatcher, dataManager)

    @Provides
    @Singleton
    fun writeCsvSummaryFunction(
        dataManager: DataManager,
        accountSummaryCreator: AccountSummaryCreator,
    ) = WriteCsvSummaryFunction(dataManager, accountSummaryCreator)

    @Provides
    @Singleton
    fun fetchSasTokenFunction(
        blobServiceClient: BlobServiceClient
    ) = FetchSASTokenFunction(blobServiceClient)

    @Provides
    @Singleton
    fun updateMetadataFunction(
        dataManager: DataManager
    ) = UpdateMetadataActivity(dataManager)

    @Provides
    @Singleton
    fun loadAnalyzedModelsFunction(
        dataManager: DataManager
    ) = LoadAnalyzedModelsActivity(dataManager)
}