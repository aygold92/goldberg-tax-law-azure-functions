package com.goldberg.law

import com.azure.ai.documentintelligence.DocumentIntelligenceClient
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder
import com.azure.core.credential.AzureKeyCredential
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.goldberg.law.database.DatabaseConfig
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.name.Named
import java.time.Duration
import javax.inject.Singleton

class AppModule: AbstractModule() {
    override fun configure() {
        Runtime.getRuntime().addShutdownHook(Thread {
            DatabaseConfig.close()
        })
        DatabaseConfig.init()
    }

    @Provides
    @Singleton
    fun documentIntelligenceClient(): DocumentIntelligenceClient = DocumentIntelligenceClientBuilder()
        .endpoint(getEnvStrict(EnvVars.DOCUMENT_INTELLIGENCE_API_ENDPOINT))
        .credential(AzureKeyCredential(getEnvStrict(EnvVars.DOCUMENT_INTELLIGENCE_API_KEY)))
        .httpClient(NettyAsyncHttpClientBuilder().responseTimeout(Duration.ofSeconds(180)).build())
        .buildClient()

    @Provides
    @Singleton
    fun storageServiceClient(): BlobServiceClient = BlobServiceClientBuilder()
        .connectionString("DefaultEndpointsProtocol=https;AccountName=${getEnvStrict(EnvVars.AZURE_STORAGE_ACCOUNT_NAME)};AccountKey=${getEnvStrict(EnvVars.AZURE_STORAGE_ACCOUNT_KEY)};EndpointSuffix=core.windows.net")
        .buildClient()

    @Provides
    @Named(CLASSIFIER_MODEL_ID)
    fun provideClassifierModelId(): String = getEnvStrict(CLASSIFIER_MODEL_ID)

    @Provides
    @Named(STATEMENT_EXTRACTOR_MODEL_ID)
    fun provideStatementExtractorModelId(): String = getEnvStrict(STATEMENT_EXTRACTOR_MODEL_ID)

    @Provides
    @Named(CHECK_EXTRACTOR_MODEL_ID)
    fun provideCheckExtractorModelId(): String = getEnvStrict(CHECK_EXTRACTOR_MODEL_ID)

    @Provides
    @Named(NUM_FUNCTION_WORKERS)
    fun provideNumWorkers(): Int = getEnvStrict(NUM_FUNCTION_WORKERS).toInt()

    private fun getEnvStrict(key: String): String = requireNotNull(System.getenv(key)) {
        "Environment variable $key is required"
    }

    object EnvVars {
        const val DOCUMENT_INTELLIGENCE_API_ENDPOINT = "DocumentIntelligence.ApiEndpoint"
        const val DOCUMENT_INTELLIGENCE_API_KEY = "DocumentIntelligence.ApiKey"
        const val AZURE_STORAGE_ACCOUNT_NAME = "AzureStorage.AccountName"
        const val AZURE_STORAGE_ACCOUNT_KEY = "AzureStorage.AccountKey"
    }

    companion object {
        const val CLASSIFIER_MODEL_ID = "DocumentIntelligence.ClassifierModel"
        const val STATEMENT_EXTRACTOR_MODEL_ID = "DocumentIntelligence.ExtractorModel"
        const val CHECK_EXTRACTOR_MODEL_ID = "DocumentIntelligence.CheckExtractorModel"
        const val NUM_FUNCTION_WORKERS = "NumFunctionWorkers"
    }

//    @Provides
//    @Singleton
//    fun azureStorageDataManager(blobServiceClient: BlobServiceClient): AzureStorageDataManager =
//        AzureStorageDataManager(blobServiceClient)
//
//    /** these should not have to instantiated here, I have no idea why guice can't find the @Inject constructor **/
//    @Provides
//    @Singleton
//    fun documentClassifier(documentIntelligenceClient: DocumentIntelligenceClient): DocumentClassifier =
//        DocumentClassifier(documentIntelligenceClient, appEnvironmentSettings.azureConfig.classifierModel)
//
//    @Provides
//    @Singleton
//    fun documentDataExtractor(documentIntelligenceClient: DocumentIntelligenceClient): DocumentDataExtractor =
//        DocumentDataExtractor(
//            documentIntelligenceClient,
//            appEnvironmentSettings.azureConfig.dataExtractorModel,
//            appEnvironmentSettings.azureConfig.checkExtractorModel
//        )
//
//    @Provides
//    @Singleton
//    fun chatGBTClient() = ChatGBTClient(System.getenv("ChatGBT.ApiKey"))
//
//    @Provides
//    @Singleton
//    fun transactionCategorizer(chatGBTClient: ChatGBTClient) = TransactionCategorizer(chatGBTClient)
//
//    @Provides
//    @Singleton
//    fun pdfDataExtractorOrchestratorFunction() = PdfDataExtractorOrchestratorFunction(
//        appEnvironmentSettings.azureConfig.numWorkers,
//        ConcurrentExecutionOrchestrator(),
//        OrchestrationStatusFactory(),
//    )
//
//    @Provides
//    @Singleton
//    fun processDataModelFunction(
//        dataExtractor: DocumentDataExtractor,
//        azureStorageDataManager: AzureStorageDataManager
//    ) = ProcessDataModelActivity(dataExtractor, azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun getRelevantFilesActivity(
//        mySQLDataManager: MySQLDataManager
//    ) = GetFilesToProcessActivity(mySQLDataManager)
//
//    @Provides
//    @Singleton
//    fun getClassifyDocumentActivity(
//        azureStorageDataManager: AzureStorageDataManager,
//        documentClassifier: DocumentClassifier
//    ) = ClassifyDocumentActivity(azureStorageDataManager, documentClassifier)
//
//    @Provides
//    @Singleton
//    fun writeCsvSummaryFunction(
//        azureStorageDataManager: AzureStorageDataManager,
//        accountSummaryCreator: AccountSummaryCreator,
//        csvCreator: CsvCreator,
//    ) = WriteCsvSummaryFunction(azureStorageDataManager, accountSummaryCreator, csvCreator)
//
//    @Provides
//    @Singleton
//    fun fetchSasTokenFunction(
//        blobServiceClient: BlobServiceClient
//    ) = FetchSASTokenFunction(blobServiceClient)
//
//    @Provides
//    @Singleton
//    fun newClientFunction(
//        blobServiceClient: BlobServiceClient,
//        mySQLDataManager: MySQLDataManager
//    ) = NewClientFunction(blobServiceClient, mySQLDataManager)
//
//    @Provides
//    @Singleton
//    fun listClientsFunction(
//        blobServiceClient: BlobServiceClient
//    ) = ListClientsFunction(blobServiceClient)
//
//    @Provides
//    @Singleton
//    fun testAnalyzePageFunction(
//        azureStorageDataManager: AzureStorageDataManager,
//        processDataModelActivity: ProcessDataModelActivity,
//    ) = AnalyzePageFunction(azureStorageDataManager, processDataModelActivity)
//
//    @Provides
//    @Singleton
//    fun putDocumentDataModelFunction(
//        dataManager: AzureStorageDataManager
//    ) = PutDocumentDataModelFunction(dataManager)
//
//    @Provides
//    @Singleton
//    fun getDocumentDataModelFunction(
//        dataManager: AzureStorageDataManager
//    ) = GetDocumentDataModelFunction(dataManager)
//
//    @Provides
//    @Singleton
//    fun updateStatementModelFunction(
//        azureStorageDataManager: AzureStorageDataManager,
//        putDocumentClassificationFunction: PutDocumentClassificationFunction
//    ) = UpdateStatementModelFunction(azureStorageDataManager, putDocumentClassificationFunction)
//
//    @Provides
//    @Singleton
//    fun deleteDocumentFunction(
//        azureStorageDataManager: AzureStorageDataManager
//    ) = DeleteInputDocumentFunction(azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun loadTransactionsFromModelFunction(
//        azureStorageDataManager: AzureStorageDataManager
//    ) = LoadTransactionsFromModelFunction(azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun categorizeTransactionsFunction(
//        transactionCategorizer: TransactionCategorizer
//    ) = CategorizeTransactionsFunction(transactionCategorizer)
//
//    @Provides
//    @Singleton
//    fun putDocumentClassificationFunction(dataManager: AzureStorageDataManager) = PutDocumentClassificationFunction(dataManager)
//
//    @Provides
//    @Singleton
//    fun getDocumentClassificationFunction(dataManager: AzureStorageDataManager) = GetDocumentClassificationFunction(dataManager)
//
//    @Provides
//    @Singleton
//    fun listStatementsFunction(dataManager: AzureStorageDataManager) = ListStatementsFunction(dataManager)
//
//    @Provides
//    @Singleton
//    fun listInputDocumentsFunction(dataManager: AzureStorageDataManager) = ListInputDocumentsFunction(dataManager)
//
//    @Provides
//    @Singleton
//    fun loadBankStatementFunction(
//        azureStorageDataManager: AzureStorageDataManager
//    ) = LoadBankStatementFunction(azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun deleteStatementFunction(
//        azureStorageDataManager: AzureStorageDataManager
//    ) = DeleteStatementFunction(azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun updateInputFileMetadataFunction(
//        azureStorageDataManager: AzureStorageDataManager
//    ) = UpdateInputFileMetadataFunction(azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun getInputFileMetadataFunction(
//        azureStorageDataManager: AzureStorageDataManager
//    ) = GetInputFileSummaryFunction(azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun retrieveOutputFileFunction(
//        azureStorageDataManager: AzureStorageDataManager
//    ) = RetrieveOutputFileFunction(azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun matchStatementsWithChecksFunction(
//        azureStorageDataManager: AzureStorageDataManager,
//    ) = MatchStatementsWithChecksFunction(azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun putFileInfoFunction(
//        mySQLDataManager: MySQLDataManager,
//        azureStorageDataManager: AzureStorageDataManager
//    ) = PutFileInfoFunction(mySQLDataManager, azureStorageDataManager)
//
//    @Provides
//    @Singleton
//    fun classifyDocumentFunction(
//        classifyDocumentActivity: ClassifyDocumentActivity
//    ) = ClassifyDocumentFunction(classifyDocumentActivity)

}