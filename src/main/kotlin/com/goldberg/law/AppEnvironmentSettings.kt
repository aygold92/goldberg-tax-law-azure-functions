package com.goldberg.law

data class AppEnvironmentSettings(
    val intelligenceService: IntelligenceService,
    val executionEnvironment: ExecutionEnvironment
)

data class DocumentIntelligenceConnection(val apiEndpoint: String, val apiKey: String, val classifierModel: String, val dataExtractorModel: String, val checkExtractorModel: String)

// TODO: move this to config file
enum class IntelligenceService constructor(documentIntelligenceConnection: DocumentIntelligenceConnection) {
    TEST(DocumentIntelligenceConnection(
        "https://eastus.api.cognitive.microsoft.com/",
        "[REDACTED]",
        "BankCreditCardCheckClassification_V1",
        "BankCreditCardExtractor_V5",
        "CheckDataExtractor_V2"
    )),
    PROD(DocumentIntelligenceConnection(
        "https://goldbergtaxlawdocumentservice.cognitiveservices.azure.com/",
        "[REDACTED]",
        "BankCreditCardCheckClassification_V1", // TODO: add _Prod next time you train it
        "BankCreditCardExtractor_Prod_V1",
        "CheckDataExtractor_Prod_V1"
    ));

    val apiEndpoint = documentIntelligenceConnection.apiEndpoint
    val apiKey = documentIntelligenceConnection.apiKey
    val classifierModel = documentIntelligenceConnection.classifierModel
    val dataExtractorModel = documentIntelligenceConnection.dataExtractorModel
    val checkExtractorModel = documentIntelligenceConnection.checkExtractorModel
}

enum class ExecutionEnvironment {
    LOCAL, AZURE
}
