package com.goldberg.law

data class AppEnvironmentSettings(
    val azureConfig: AzureConfiguration,
    val executionEnvironment: ExecutionEnvironment
)

enum class ExecutionEnvironment {
    LOCAL, AZURE
}

data class DocumentIntelligenceConnection(val apiEndpoint: String, val apiKey: String, val classifierModel: String, val dataExtractorModel: String, val checkExtractorModel: String)

enum class AzureConfiguration constructor(
    documentIntelligenceConnection: DocumentIntelligenceConnection,
    val storageBlobConfig: StorageBlobConfiguration,
    val numWorkers: Int
) {
    TEST(
        DocumentIntelligenceConnection(
            System.getenv("DocumentIntelligence.Test.ApiEndpoint"),
            System.getenv("DocumentIntelligence.Test.ApiKey"),
            System.getenv("DocumentIntelligence.Test.ClassifierModel"),
            System.getenv("DocumentIntelligence.Test.ExtractorModel"),
            System.getenv("DocumentIntelligence.Test.CheckExtractorModel"),
        ),
        StorageBlobConfiguration(
            System.getenv("AzureStorage.Test.AccountKey"),
            System.getenv("AzureStorage.Test.AccountName"),
            StorageBlobContainersNames(
                System.getenv("AzureStorage.Test.Container.Input"),
                System.getenv("AzureStorage.Test.Container.SplitInput"),
                System.getenv("AzureStorage.Test.Container.Models"),
                System.getenv("AzureStorage.Test.Container.Output"),
            )
        ),
        System.getenv("Test.NumWorkers").toInt()
    ),
    PROD(
        DocumentIntelligenceConnection(
            System.getenv("DocumentIntelligence.Prod.ApiEndpoint"),
            System.getenv("DocumentIntelligence.Prod.ApiKey"),
            System.getenv("DocumentIntelligence.Prod.ClassifierModel"),
            System.getenv("DocumentIntelligence.Prod.ExtractorModel"),
            System.getenv("DocumentIntelligence.Prod.CheckExtractorModel"),
        ),
        StorageBlobConfiguration(
            System.getenv("AzureStorage.Prod.AccountKey"),
            System.getenv("AzureStorage.Prod.AccountName"),
            StorageBlobContainersNames(
                System.getenv("AzureStorage.Prod.Container.Input"),
                System.getenv("AzureStorage.Prod.Container.SplitInput"),
                System.getenv("AzureStorage.Prod.Container.Models"),
                System.getenv("AzureStorage.Prod.Container.Output"),
            )

        ),
        System.getenv("Prod.NumWorkers").toInt()
    );

    val storageBlobConnectionString = "DefaultEndpointsProtocol=https;AccountName=${storageBlobConfig.storageAccountName};AccountKey=${storageBlobConfig.accountKey};EndpointSuffix=core.windows.net"
    val apiEndpoint = documentIntelligenceConnection.apiEndpoint
    val apiKey = documentIntelligenceConnection.apiKey
    val classifierModel = documentIntelligenceConnection.classifierModel
    val dataExtractorModel = documentIntelligenceConnection.dataExtractorModel
    val checkExtractorModel = documentIntelligenceConnection.checkExtractorModel
}

data class StorageBlobConfiguration(
    val accountKey: String,
    val storageAccountName: String,
    val containerNames: StorageBlobContainersNames
)

data class StorageBlobContainersNames(
    val input: String,
    val splitInput: String,
    val models: String,
    val output: String,
)
