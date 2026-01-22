package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.script.maritalinvestments.model.InstrumentKey
import com.goldberg.law.script.maritalinvestments.model.InvestmentTransaction
import com.goldberg.law.script.maritalinvestments.model.TransactionLog
import com.goldberg.law.script.maritalinvestments.model.TransactionType
import com.goldberg.law.script.maritalinvestments.model.VanguardTransaction
import com.goldberg.law.util.*
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

class ProcessMaritalTransactionsFunction(private val csvParser: CsvParser) {
    private val logger = KotlinLogging.logger {}

    // in this method, we assume all cash is shared
    fun processMaritalTransactionsSharedCash(transactions: List<InvestmentTransaction>,
                                   startingHoldings: HoldingsReport,
                                   marriageDate: Date): ProcessMaritalTransactionsOutput {
        val holdingsReport: MutableMap<String, HoldingsReport> = mutableMapOf()
        val transactionLog: MutableList<TransactionLog> = mutableListOf()

        val cashSym = transactions.filter { it.type == TransactionType.SWEEP_IN || it.type == TransactionType.SWEEP_OUT }
            .map { it.symbol }.distinct()
            .let { cashAdjacentSymbols -> cashAdjacentSymbols.takeIf { it.size == 1 } ?: throw IllegalArgumentException("Exactly 1 cash adjacent symbol is required. Found: ${cashAdjacentSymbols.joinToString(", ")}") }
            .first()

        val preMaritalHoldings: MutableMap<InstrumentKey, BigDecimal> = startingHoldings.preMaritalHoldings.minus(cashSym).toMutableMap()
        val sharedHoldings: MutableMap<InstrumentKey, BigDecimal> = startingHoldings.sharedHoldings.minus(cashSym).toMutableMap()
        // if we sell an asset to 0, remember the last known ratio in case there is a DISTRIBUTION that comes after
        val lastKnownRatios: MutableMap<InstrumentKey, BigDecimal> = mutableMapOf()

        // keep track of the shared cash
        var cashHoldings = ((startingHoldings.preMaritalHoldings[cashSym] ?: BigDecimal.ZERO) + (startingHoldings.sharedHoldings[cashSym] ?: BigDecimal.ZERO)).clean()

        val sortedTransactions = transactions.sortedBy { it.date }

        var currentStatementDate = sortedTransactions[0].getStatementDate()

        logger.info { "initial holdings: $preMaritalHoldings, $sharedHoldings" }
        sortedTransactions.forEachIndexed { idx, transaction ->
            try {
                val isPostMarriage = transaction.date.after(marriageDate)
                if (transaction.getStatementDate() != currentStatementDate) {
                    val preMaritalCashHoldings = if (currentStatementDate.after(marriageDate)) BigDecimal.ZERO else cashHoldings
                    val sharedCashHoldings = if (currentStatementDate.after(marriageDate)) cashHoldings else BigDecimal.ZERO
                    holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(
                        preMaritalHoldings.plus(cashSym to preMaritalCashHoldings).toMap(),
                        sharedHoldings.plus(cashSym to sharedCashHoldings).toMap()
                    )
                    logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}"  }
                    currentStatementDate = transaction.getStatementDate()
                }
                logger.info { "[$idx] [${transaction.date.toTransactionDate()}] processing a ${transaction.type} for ${transaction.symbol}: qty=${transaction.quantity}, amt=${transaction.amount}" }

                val percent: BigDecimal? = when(transaction.type) {
                    TransactionType.BUY -> {
                        // we buy with the cash in the account
                        if (isPostMarriage) sharedHoldings.add(transaction.symbol, transaction.quantity!!)
                        else preMaritalHoldings.add(transaction.symbol, transaction.quantity!!)

                        cashHoldings = (cashHoldings - transaction.amount).clean()
                        if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
                    }
                    TransactionType.SELL -> {
                        // sells are made in proportion of current ownership
                        val preMaritalInstrumentPercent = calculateRatio(preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO), sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO))
                        val preMaritalInstrumentAmount = (transaction.quantity!!.multiplyScale(preMaritalInstrumentPercent))
                        preMaritalHoldings.subtract(transaction.symbol, preMaritalInstrumentAmount)
                        sharedHoldings.subtract(transaction.symbol, transaction.quantity!! - preMaritalInstrumentAmount)

                        lastKnownRatios[transaction.symbol] = preMaritalInstrumentPercent

                        cashHoldings = (cashHoldings + transaction.amount).clean()

                        preMaritalInstrumentPercent
                    }
                    TransactionType.RE_INVESTMENT -> {
                        // reInvestments are like buys but are made the proportion of current ownership
                        if (transaction.symbol == cashSym) {
                            // no need to add or subtract from cash holdings because it cancels out, and the addition is already handled by the dividend
                            if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
                        } else {
                            val preMaritalInstrumentPercent = calculateRatio(
                                preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                                sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                                lastKnownRatios[transaction.symbol]
                            )

                            // reinvestments could be for the money market fund (which uses amount only) or for a symbol (which uses quantity)
                            val totalQuantityOrAmount = (transaction.quantity ?: transaction.amount)
                            val preMaritalQuantityOrAmount = totalQuantityOrAmount.multiplyScale(preMaritalInstrumentPercent)
                            preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
                            sharedHoldings.add(transaction.symbol, totalQuantityOrAmount - preMaritalQuantityOrAmount)

                            cashHoldings = (cashHoldings - transaction.amount).clean()
                            preMaritalInstrumentPercent
                        }
                    }
                    TransactionType.DISTRIBUTION -> {
                        // distributions are made in proportion of current ownership
                        cashHoldings = (cashHoldings + transaction.amount).clean()
                        if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
                    }
                    TransactionType.DEPOSIT -> {
                        // deposits before marriage are 100% preMarital, and after is 100% shared
                        cashHoldings = (cashHoldings + transaction.amount).clean()
                        if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
                    }
                    TransactionType.WITHDRAWAL -> {
                        // withdrawals just subtract from cash
                        cashHoldings = (cashHoldings - transaction.amount).clean()
                        if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
                    }
                    else -> {
                        null
                    }
                }
                logger.info { "[$idx] [${transaction.date.toTransactionDate()}] preMaritalPercent=$percent" }
                transactionLog.add(transaction.log(percent))
                currentStatementDate = transaction.getStatementDate()
            } catch(ex: Exception) {
                logger.error(ex) { "Error processing transaction $idx: $transaction" }
                throw ex
            }
        }
        preMaritalHoldings[cashSym] = BigDecimal.ZERO
        sharedHoldings[cashSym] = cashHoldings.clean()
        holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(preMaritalHoldings.toMap(), sharedHoldings.toMap())
        logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}"  }

        return ProcessMaritalTransactionsOutput(holdingsReport, transactionLog)
    }

    fun processMaritalTransactions(transactions: List<InvestmentTransaction>,
                                   startingHoldings: HoldingsReport,
                                   marriageDate: Date): ProcessMaritalTransactionsOutput {
        val holdingsReport: MutableMap<String, HoldingsReport> = mutableMapOf()
        val transactionLog: MutableList<TransactionLog> = mutableListOf()

        val cashSym = transactions.filter { it.type == TransactionType.SWEEP_IN || it.type == TransactionType.SWEEP_OUT }
            .map { it.symbol }.distinct()
            .let { cashAdjacentSymbols -> cashAdjacentSymbols.takeIf { it.size == 1 } ?: throw IllegalArgumentException("Exactly 1 cash adjacent symbol is required. Found: ${cashAdjacentSymbols.joinToString(", ")}") }
            .first()

        val preMaritalHoldings: MutableMap<InstrumentKey, BigDecimal> = if (!startingHoldings.preMaritalHoldings.containsKey(cashSym))
            startingHoldings.preMaritalHoldings.plus(mutableMapOf(cashSym to BigDecimal.ZERO)).toMutableMap()
        else startingHoldings.preMaritalHoldings.toMutableMap()

        val sharedHoldings: MutableMap<InstrumentKey, BigDecimal> = if (!startingHoldings.sharedHoldings.containsKey(cashSym))
            startingHoldings.sharedHoldings.plus(mutableMapOf(cashSym to BigDecimal.ZERO)).toMutableMap()
        else startingHoldings.sharedHoldings.toMutableMap()

        // if we sell an asset to 0, remember the last known ratio in case there is a DISTRIBUTION that comes after
        val lastKnownRatios: MutableMap<InstrumentKey, BigDecimal> = mutableMapOf()

        val sortedTransactions = transactions.sortedBy { it.date }

        var currentStatementDate = sortedTransactions[0].getStatementDate()

        logger.info { "initial holdings: $preMaritalHoldings, $sharedHoldings" }
        sortedTransactions.forEachIndexed { idx, transaction ->
            try {
                if (transaction.getStatementDate() != currentStatementDate) {
                    holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(preMaritalHoldings.toMap(), sharedHoldings.toMap())
                    logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}"  }
                    currentStatementDate = transaction.getStatementDate()
                }
                logger.info { "[$idx] [${transaction.date.toTransactionDate()}] processing a ${transaction.type} for ${transaction.symbol}: qty=${transaction.quantity}, amt=${transaction.amount}" }

                val percent: BigDecimal? = when(transaction.type) {
                    TransactionType.BUY -> {
                        // we buy with the cash in the account
                        val preMaritalCashPercent = calculateRatio(preMaritalHoldings[cashSym]!!, sharedHoldings[cashSym]!!)

                        val preMaritalQuantityOrAmount = transaction.quantity!!.multiplyScale(preMaritalCashPercent)
                        preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
                        sharedHoldings.add(transaction.symbol, transaction.quantity!! - preMaritalQuantityOrAmount)

                        val preMaritalAmount = transaction.amount.multiplyScale(preMaritalCashPercent)
                        preMaritalHoldings.subtract(cashSym, preMaritalAmount)
                        sharedHoldings.subtract(cashSym, transaction.amount - preMaritalAmount)

                        preMaritalCashPercent
                    }
                    TransactionType.SELL -> {
                        // sells are made in proportion of current ownership
                        val preMaritalInstrumentPercent = calculateRatio(preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO), sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO))
                        val preMaritalInstrumentAmount = (transaction.quantity!!.multiplyScale(preMaritalInstrumentPercent))
                        preMaritalHoldings.subtract(transaction.symbol, preMaritalInstrumentAmount)
                        sharedHoldings.subtract(transaction.symbol, transaction.quantity!! - preMaritalInstrumentAmount)

                        lastKnownRatios[transaction.symbol] = preMaritalInstrumentPercent

                        val preMaritalAmount = (transaction.amount).multiplyScale(preMaritalInstrumentPercent)
                        preMaritalHoldings.add(cashSym, preMaritalAmount)
                        sharedHoldings.add(cashSym, transaction.amount - preMaritalAmount)

                        preMaritalInstrumentPercent
                    }
                    TransactionType.RE_INVESTMENT -> {
                        // reInvestments are like buys but are made the proportion of current ownership
                        val preMaritalInstrumentPercent = calculateRatio(
                            preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            lastKnownRatios[transaction.symbol]
                        )

                        val totalQuantityOrAmount = (transaction.quantity ?: transaction.amount)
                        val preMaritalQuantityOrAmount = totalQuantityOrAmount.multiplyScale(preMaritalInstrumentPercent)
                        preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
                        sharedHoldings.add(transaction.symbol, totalQuantityOrAmount - preMaritalQuantityOrAmount)

                        val preMaritalAmount = transaction.amount.multiplyScale(preMaritalInstrumentPercent)
                        preMaritalHoldings.subtract(cashSym, preMaritalAmount)
                        sharedHoldings.subtract(cashSym, transaction.amount - preMaritalAmount)
                        preMaritalInstrumentPercent
                    }
                    TransactionType.DISTRIBUTION -> {
                        // distributions are made in proportion of current ownership
                        val preMaritalInstrumentPercent = calculateRatio(
                            preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            lastKnownRatios[transaction.symbol]
                        )
                        val preMaritalAmount = transaction.amount.multiplyScale(preMaritalInstrumentPercent)
                        preMaritalHoldings.add(cashSym, preMaritalAmount)
                        sharedHoldings.add(cashSym, transaction.amount - preMaritalAmount)
                        preMaritalInstrumentPercent
                    }
                    TransactionType.DEPOSIT -> {
                        // deposits before marriage are 100% preMarital, and after is 100% shared
                        if (transaction.date.after(marriageDate)) {
                            sharedHoldings.add(cashSym, transaction.amount)
                            BigDecimal.ZERO
                        } else {
                            preMaritalHoldings.add(cashSym, transaction.amount)
                            BigDecimal.ONE
                        }
                    }
                    TransactionType.WITHDRAWAL -> {
                        // withdrawals are made based on proportion of cash in the account
                        val preMaritalCashPercent = calculateRatio(preMaritalHoldings[cashSym]!!, sharedHoldings[cashSym]!!)
                        val preMaritalAmount = transaction.amount.multiplyScale(preMaritalCashPercent)
                        preMaritalHoldings.subtract(cashSym, preMaritalAmount)
                        sharedHoldings.subtract(cashSym, transaction.amount - preMaritalAmount)
                        preMaritalCashPercent
                    }
                    else -> {
                        null
                    }
                }
                logger.info { "[$idx] [${transaction.date.toTransactionDate()}] preMaritalPercent=$percent" }
                transactionLog.add(transaction.log(percent))
                currentStatementDate = transaction.getStatementDate()
            } catch(ex: Exception) {
                logger.error(ex) { "Error processing transaction $idx: $transaction" }
                throw ex
            }
        }
        holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(preMaritalHoldings.toMap(), sharedHoldings.toMap())
        logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}"  }

        return ProcessMaritalTransactionsOutput(holdingsReport, transactionLog)
    }

    fun processMaritalTransactions(input: ProcessMaritalTransactionsInput): ProcessMaritalTransactionsOutput {
        val marriageDate = fromWrittenDate(input.marriageDate) ?: throw IllegalArgumentException("Invalid marriage date: ${input.marriageDate}")

        val transactions = csvParser.parse(input.transactionsCsv)
            .map { VanguardTransaction.fromCsvLine(it) }
        return if (input.useSplitCashAccounts)
            processMaritalTransactions(transactions, input.startingHoldings, marriageDate)
        else
            processMaritalTransactionsSharedCash(transactions, input.startingHoldings, marriageDate)
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

    private fun calculateRatio(val1: BigDecimal, val2: BigDecimal, fallbackRatio: BigDecimal? = null): BigDecimal {
        if (val1 < 0.bd() || val1 + val2 <= 0.bd()) {
            logger.error { "Invalid ratio calculation: $val1, $val2" }
        }
        val ratio = try {
            val1.divide((val1 + val2), MathContext.DECIMAL128).clean()
        } catch (ex: Exception) {
            logger.error(ex) { "Error dividing $val1 by $val2" }
            fallbackRatio ?: throw ex
        }
        return ratio
    }

    companion object {
        const val FUNCTION_NAME = "ProcessMartialTransactions"
        private val ONE = 1.bd()

        fun MutableMap<InstrumentKey, BigDecimal>.subtract(symbol: InstrumentKey, amount: BigDecimal) = add(symbol, amount.negate())
        fun MutableMap<InstrumentKey, BigDecimal>.add(symbol: InstrumentKey, amount: BigDecimal) {
            try {

                this[symbol] = (this.getOrDefault(symbol, 0.bd()) + amount).clean()
            } catch (ex: Exception) {
                println(ex)
            }
        }

        fun BigDecimal.multiplyScale(other: BigDecimal) = (this * other).clean()

        // statement dates are considered to be the first of the next month, including any transactions on the first of the month
        // for ex, a transaction on 10/2/2020 and 11/1/2020 will be both have the statement date 11/1/2020
        fun InvestmentTransaction.getStatementDate(): Date = Calendar.getInstance().apply { time = this@getStatementDate.date }.let { cal ->
            if (cal.get(Calendar.DAY_OF_MONTH) == 1) {
                cal.time
            } else {
                cal.add(Calendar.MONTH, 1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.time
            }
        }
    }
}