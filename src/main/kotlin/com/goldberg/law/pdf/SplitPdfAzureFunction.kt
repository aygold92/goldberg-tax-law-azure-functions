package com.goldberg.law.pdf

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldberg.law.pdf.model.SplitPdfRequest
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import java.util.*

class SplitPdfAzureFunction {
    @FunctionName("splitPdf")
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.FUNCTION)
        request: HttpRequestMessage<Optional<String>>,
        context: ExecutionContext
    ): HttpResponseMessage {
        val req = OBJECT_MAPPER.readValue(request.body.orElseThrow(), SplitPdfRequest::class.java)

        //val document = PDF_SPLITTER.splitPdf(req)

        return request.createResponseBuilder(HttpStatus.OK)
            .body("Document processed and data stored successfully.")
            .build()
    }
    companion object {
        private val OBJECT_MAPPER = ObjectMapper()
        private val PDF_SPLITTER = PdfSplitter()
    }
}