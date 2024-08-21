package com.goldberg.law

data class AppEnvironmentSettings(
    val intelligenceService: IntelligenceService,
    val executionEnvironment: ExecutionEnvironment
)

data class DocumentIntelligenceConnection(val apiEndpoint: String, val apiKey: String, val classifierModel: String, val dataExtractorModel: String)

// TODO: move this to config file
enum class IntelligenceService constructor(documentIntelligenceConnection: DocumentIntelligenceConnection) {
    TEST(DocumentIntelligenceConnection(
        "https://eastus.api.cognitive.microsoft.com/",
        "[REDACTED]",
        "BankCreditCardClassification_V2",
        "BankCreditCardExtractor_V4"
    )),
    PROD(DocumentIntelligenceConnection(
        "https://goldbergtaxlawdocumentservice.cognitiveservices.azure.com/",
        "[REDACTED]",
        "BankCreditCardClassification_Prod_V1",
        "BankCreditCardExtractor_Prod_V1"
    ));

    val apiEndpoint = documentIntelligenceConnection.apiEndpoint
    val apiKey = documentIntelligenceConnection.apiKey
    val classifierModel = documentIntelligenceConnection.classifierModel
    val dataExtractorModel = documentIntelligenceConnection.dataExtractorModel
}

enum class ExecutionEnvironment {
    LOCAL, AZURE
}
