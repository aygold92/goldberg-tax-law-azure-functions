package com.goldberg.law

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder
import com.azure.core.credential.AzureKeyCredential
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.*
import com.goldberg.law.function.PdfDataExtractorOrchestratorFunction
import com.goldberg.law.function.WriteCsvSummaryFunction
import com.goldberg.law.function.activity.*
import com.goldberg.law.splitpdftool.PdfSplitter
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
    fun azureStorageDataManager(blobServiceClient: BlobServiceClient): AzureStorageDataManager =
        AzureStorageDataManager(blobServiceClient)

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
    fun pdfDataExtractorOrchestratorFunction() = PdfDataExtractorOrchestratorFunction(appEnvironmentSettings.azureConfig.numWorkers)

    @Provides
    @Singleton
    fun splitPdfFunction(
        azureStorageDataManager: AzureStorageDataManager,
    ) = SplitPdfActivity(azureStorageDataManager)

    @Provides
    @Singleton
    fun processDataModelFunction(
        classifier: DocumentClassifier,
        dataExtractor: DocumentDataExtractor,
        azureStorageDataManager: AzureStorageDataManager
    ) = ProcessDataModelActivity(classifier, dataExtractor, azureStorageDataManager)

    @Provides
    @Singleton
    fun getRelevantFilesActivity(
        azureStorageDataManager: AzureStorageDataManager
    ) = GetFilesToProcessActivity(azureStorageDataManager)

    @Provides
    @Singleton
    fun processStatementActivity(
        statementCreator: DocumentStatementCreator,
        accountNormalizer: AccountNormalizer,
        checkToStatementMatcher: CheckToStatementMatcher,
        azureStorageDataManager: AzureStorageDataManager
    ) = ProcessStatementsActivity(statementCreator, accountNormalizer, checkToStatementMatcher, azureStorageDataManager)

    @Provides
    @Singleton
    fun writeCsvSummaryFunction(
        azureStorageDataManager: AzureStorageDataManager,
        accountSummaryCreator: AccountSummaryCreator,
        csvCreator: CsvCreator,
    ) = WriteCsvSummaryFunction(azureStorageDataManager, accountSummaryCreator, csvCreator)

    @Provides
    @Singleton
    fun fetchSasTokenFunction(
        blobServiceClient: BlobServiceClient
    ) = FetchSASTokenFunction(blobServiceClient)

    @Provides
    @Singleton
    fun updateMetadataFunction(
        azureStorageDataManager: AzureStorageDataManager
    ) = UpdateMetadataActivity(azureStorageDataManager)

    @Provides
    @Singleton
    fun loadAnalyzedModelsFunction(
        azureStorageDataManager: AzureStorageDataManager
    ) = LoadAnalyzedModelsActivity(azureStorageDataManager)
}