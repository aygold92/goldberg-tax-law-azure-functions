package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.script.maritalinvestments.model.VanguardTransaction
import com.goldberg.law.util.*
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ProcessMaritalTransactionsFunction(private val csvParser: CsvParser) {
    private val logger = KotlinLogging.logger {}

    fun processMaritalTransactions(input: ProcessMaritalTransactionsInput): ProcessMaritalTransactionsOutput {
        val marriageDate = fromWrittenDate(input.marriageDate) ?: throw IllegalArgumentException("Invalid marriage date: ${input.marriageDate}")

        val transactions = csvParser.parse(input.transactionsCsv)
            .map { VanguardTransaction.fromCsvLine(it) }

        val processor: MaritalTransactionProcessor = when (input.processorType) {
            MaritalTransactionProcessorType.SplitCash -> SplitCashTransactionProcessor(transactions, input.startingHoldings, marriageDate)
            MaritalTransactionProcessorType.SharedCash -> SharedCashTransactionProcessor(transactions, input.startingHoldings, marriageDate)
            MaritalTransactionProcessorType.FIFO -> FIFOSplitCashTransactionProcessor(transactions, input.startingHoldings, marriageDate)
        }

        return processor.processMaritalTransactions()
    }

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        val result = processMaritalTransactions(OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), ProcessMaritalTransactionsInput::class.java))

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(result)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error analyzing for input $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    enum class MaritalTransactionProcessorType{
        SplitCash,SharedCash,FIFO
    }

    companion object {
        const val FUNCTION_NAME = "ProcessMartialTransactions"
    }
}