package com.goldberg.law

import com.azure.ai.documentintelligence.DocumentIntelligenceClient
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder
import com.azure.core.credential.AzureKeyCredential
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.goldberg.law.database.DatabaseConfig
import com.goldberg.law.database.DbExec
import com.goldberg.law.document.AzureDocumentClassifier
import com.goldberg.law.document.AzureDocumentDataExtractor
import com.goldberg.law.document.DocumentClassifier
import com.goldberg.law.document.DocumentDataExtractor
import com.goldberg.law.document.ProxyDocumentClassifier
import com.goldberg.law.document.ProxyDocumentDataExtractor
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.name.Named
import org.jetbrains.exposed.sql.Database
import java.time.Duration

class AppModule: AbstractModule() {
    override fun configure() {
        Runtime.getRuntime().addShutdownHook(Thread {
            DatabaseConfig.close()
        })
        DatabaseConfig.init()
    }

    @Provides
    @Singleton
    fun documentClassifier(): DocumentClassifier =
        if (isProxyMode()) ProxyDocumentClassifier()
        else AzureDocumentClassifier(buildAzureClient(), getEnvStrict(CLASSIFIER_MODEL_ID))

    @Provides
    @Singleton
    fun documentDataExtractor(): DocumentDataExtractor =
        if (isProxyMode()) ProxyDocumentDataExtractor()
        else AzureDocumentDataExtractor(
            buildAzureClient(),
            getEnvStrict(STATEMENT_EXTRACTOR_MODEL_ID),
            getEnvStrict(CHECK_EXTRACTOR_MODEL_ID),
        )

    @Provides
    @Singleton
    fun storageServiceClient(): BlobServiceClient = BlobServiceClientBuilder()
        .connectionString("DefaultEndpointsProtocol=https;AccountName=${getEnvStrict(EnvVars.AZURE_STORAGE_ACCOUNT_NAME)};AccountKey=${getEnvStrict(EnvVars.AZURE_STORAGE_ACCOUNT_KEY)};EndpointSuffix=core.windows.net")
        .buildClient()

    @Provides
    @Named(NUM_FUNCTION_WORKERS)
    fun provideNumWorkers(): Int = getEnvStrict(NUM_FUNCTION_WORKERS).toInt()

    // Lazily constructed so Azure env vars are never touched in proxy mode
    private val azureClient: DocumentIntelligenceClient by lazy {
        DocumentIntelligenceClientBuilder()
            .endpoint(getEnvStrict(EnvVars.DOCUMENT_INTELLIGENCE_API_ENDPOINT))
            .credential(AzureKeyCredential(getEnvStrict(EnvVars.DOCUMENT_INTELLIGENCE_API_KEY)))
            .httpClient(NettyAsyncHttpClientBuilder().responseTimeout(Duration.ofSeconds(180)).build())
            .buildClient()
    }

    private fun buildAzureClient(): DocumentIntelligenceClient = azureClient

    private fun isProxyMode(): Boolean =
        System.getenv(EnvVars.USE_PROXY_DOCUMENT_INTELLIGENCE)?.lowercase() == "true"

    private fun getEnvStrict(key: String): String = requireNotNull(System.getenv(key)) {
        "Environment variable $key is required"
    }

    @Provides
    @Singleton
    fun database(): Database = DatabaseConfig.database

    object EnvVars {
        const val DOCUMENT_INTELLIGENCE_API_ENDPOINT = "DocumentIntelligence.ApiEndpoint"
        const val DOCUMENT_INTELLIGENCE_API_KEY = "DocumentIntelligence.ApiKey"
        const val AZURE_STORAGE_ACCOUNT_NAME = "AzureStorage.AccountName"
        const val AZURE_STORAGE_ACCOUNT_KEY = "AzureStorage.AccountKey"
        const val USE_PROXY_DOCUMENT_INTELLIGENCE = "UseProxyDocumentIntelligence"
    }

    companion object {
        const val CLASSIFIER_MODEL_ID = "DocumentIntelligence.ClassifierModel"
        const val STATEMENT_EXTRACTOR_MODEL_ID = "DocumentIntelligence.ExtractorModel"
        const val CHECK_EXTRACTOR_MODEL_ID = "DocumentIntelligence.CheckExtractorModel"
        const val NUM_FUNCTION_WORKERS = "NumFunctionWorkers"
    }
}