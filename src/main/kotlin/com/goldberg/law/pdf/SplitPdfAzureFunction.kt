package com.goldberg.law.pdf

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class SplitPdfAzureFunction {
    @FunctionName("splitPdf")
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.FUNCTION)
        request: HttpRequestMessage<Optional<String>>,
        context: ExecutionContext
    ): HttpResponseMessage {
        val req = OBJECT_MAPPER.readValue(request.body.orElseThrow(), SplitPdfRequest::class.java)

        val document = PDF_SPLITTER.splitPdf(req)

        // TODO: put it into Storage or something

        return request.createResponseBuilder(HttpStatus.OK)
            .body("Document processed and data stored successfully.")
            .build()
    }
    companion object {
        private val OBJECT_MAPPER = ObjectMapper()
        private val PDF_SPLITTER = PDFSplitter()
    }
}