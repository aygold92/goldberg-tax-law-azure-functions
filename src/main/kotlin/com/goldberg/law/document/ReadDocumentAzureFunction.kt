package com.goldberg.law.document

import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*
import com.azure.storage.blob.BlobClientBuilder
import com.azure.ai.formrecognizer.FormRecognizerClientBuilder
import com.azure.ai.formrecognizer.models.RecognizedForm
import com.azure.core.credential.AzureKeyCredential
import com.azure.core.util.BinaryData
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.*

/**
 * Azure Functions with HTTP Trigger.
 */
class ReadDocumentAzureFunction {

    @FunctionName("uploadAndProcessDocument")
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.FUNCTION)
        request: HttpRequestMessage<Optional<String>>,
        context: ExecutionContext
    ): HttpResponseMessage {
        context.logger.info("Processing document upload...")

        val storageConnectionString = System.getenv("AZURE_STORAGE_CONNECTION_STRING")
        val blobServiceClient = BlobClientBuilder()
            .connectionString(storageConnectionString)
            .containerName("documents")
            .blobName("uploaded-document.pdf")
            .buildClient()

        // Upload the document
        val requestBody = request.body.orElse("")
        blobServiceClient.upload(BinaryData.fromString(requestBody))

        // Analyze the document
        val documentIntelligenceEndpoint = System.getenv("DOCUMENT_INTELLIGENCE_ENDPOINT")
        val documentIntelligenceKey = System.getenv("DOCUMENT_INTELLIGENCE_KEY")
        val documentClient = FormRecognizerClientBuilder()
            .endpoint(documentIntelligenceEndpoint)
            .credential(AzureKeyCredential(documentIntelligenceKey))
            .buildClient()

        val blobUrl = blobServiceClient.blobUrl
        val poller = documentClient.beginRecognizeCustomFormsFromUrl("prebuilt-document", blobUrl)
        val analyzeResult: List<RecognizedForm> = poller.waitForCompletion().let {
            poller.finalResult
        }

        // Extract key/value pairs
        val keyValuePairs = mutableMapOf<String, String>()
//        analyzeResult.forEach { recognizedForm ->
//            recognizedForm.pages.forEach { formPage ->
//                val key = formPage.key?.content
//                val value = formPage.value?.content
//                if (key != null && value != null) {
//                    keyValuePairs[key] = value
//                }
//            }
//        }

        // Store the extracted data in SQL Database
        val sqlConnectionString = System.getenv("AZURE_SQL_CONNECTION_STRING")
        storeDataInSqlDatabase(sqlConnectionString, keyValuePairs)

        return request.createResponseBuilder(HttpStatus.OK)
            .body("Document processed and data stored successfully.")
            .build()
    }

    private fun storeDataInSqlDatabase(connectionString: String, data: Map<String, String>) {
        val connection: Connection = DriverManager.getConnection(connectionString)
        val sql = "INSERT INTO ExtractedData (Key, Value) VALUES (?, ?)"
        val statement: PreparedStatement = connection.prepareStatement(sql)

        data.forEach { (key, value) ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.addBatch()
        }

        statement.executeBatch()
        statement.close()
        connection.close()
    }

}