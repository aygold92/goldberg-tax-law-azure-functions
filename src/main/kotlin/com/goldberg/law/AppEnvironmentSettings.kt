package com.goldberg.law

data class AppEnvironmentSettings(
    val intelligenceService: IntelligenceService,
    val executionEnvironment: ExecutionEnvironment
)

data class DocumentIntelligenceConnection(val apiEndpoint: String, val apiKey: String, val classifierModel: String, val dataExtractorModel: String)

// TODO: move this to config file
enum class IntelligenceService constructor(documentIntelligenceConnection: DocumentIntelligenceConnection) {
    TEST(DocumentIntelligenceConnection("https://eastus.api.cognitive.microsoft.com/", "[REDACTED]", "BankClassification2", "TransactionTable_DepositWithdrawal_V4")),
    PROD(DocumentIntelligenceConnection("test", "test", "test", "test"));

    val apiEndpoint = documentIntelligenceConnection.apiEndpoint
    val apiKey = documentIntelligenceConnection.apiKey
    val classifierModel = documentIntelligenceConnection.classifierModel
    val dataExtractorModel = documentIntelligenceConnection.dataExtractorModel
}

enum class ExecutionEnvironment {
    LOCAL, AZURE
}
