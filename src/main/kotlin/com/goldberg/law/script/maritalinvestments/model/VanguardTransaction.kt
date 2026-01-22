package com.goldberg.law.script.maritalinvestments.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeName
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.bd
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.parseBigDecimal
import com.goldberg.law.util.parseCurrency
import org.apache.commons.csv.CSVRecord
import java.math.BigDecimal
import java.util.*

@JsonTypeName("VANGUARD")
data class VanguardTransaction @JsonCreator constructor(
    @JsonProperty("settlementDate") val settlementDate: Date,
    @JsonProperty("tradeDate") val tradeDate: Date,
    @JsonProperty("symbol") override val symbol: InstrumentKey,
    @JsonProperty("name") override val name: String,
    @JsonProperty("transactionType") val transactionType: VanguardTransactionType,
    @JsonProperty("accountType") val accountType: String,
    @JsonProperty("quantity") override val quantity: BigDecimal?,
    @JsonProperty("price") val price: BigDecimal?,
    @JsonProperty("commissionsFees") val commissionsFees: BigDecimal?,
    @JsonProperty("amount") override val amount: BigDecimal,
): InvestmentTransaction {
    override val type = transactionType.toTransactionType()
    override val date = tradeDate

    companion object {
        const val T_BILL = "U S TREASURY BILL"
        const val T_BOND = "U S TREASURY BOND"
        const val T_NOTE = "U S TREASURY NOTE"

        /**
         * Parses a CSV line into a VanguardTransaction.
         * Expected columns in order:
         * 1. Settlement date (Date)
         * 2. Trade date (Date)
         * 3. Symbol (String)
         * 4. Name (String)
         * 5. Transaction type (VanguardTransactionTypes)
         * 6. Account type (String)
         * 7. Quantity (BigDecimal)
         * 8. Price (BigDecimal)
         * 9. Commissions/Fees (BigDecimal) - "FREE" is treated as 0.0
         * 10. Amount (BigDecimal)
         *
         * Note: "—" in any column means null. All values are trimmed.
         */
        fun fromCsvLine(csvLine: CSVRecord): VanguardTransaction {
            if (csvLine.size() != 10) throw InvalidCsvException(csvLine, "Line has ${csvLine.size()} columns")

            val trimmedValues: List<String?> = csvLine.values().map { value -> value.trim().let { if (it == "-" || it == "—" || it.isBlank()) null else it } }

            val name = trimmedValues.getOrNull(3) ?: throw InvalidCsvFieldException(csvLine, "name")

            val symbol = if (name.startsWith(T_BILL) || name.startsWith(T_BOND) || name.startsWith(T_NOTE)) {
                try {
                    TreasuryCoupon.parse(name)
                } catch(ex: IllegalArgumentException) {
                    throw InvalidCsvException(csvLine, ex.message!!)
                }
            }
            else trimmedValues.getOrNull(2)?.filter { it.isLetterOrDigit() }?.ifBlank { null }
                ?.let { HoldingSymbol(it) }
                ?: HoldingSymbol.NO_SYM

            return VanguardTransaction(
                settlementDate = fromWrittenDate(trimmedValues.getOrNull(0)) ?: throw InvalidCsvFieldException(csvLine, "settlementDate"),
                tradeDate = fromWrittenDate(trimmedValues.getOrNull(1)) ?: throw InvalidCsvFieldException(csvLine, "tradeDate"),
                symbol = symbol,
                name = name,
                transactionType = VanguardTransactionType.fromValue(trimmedValues.getOrNull(4)) ?: throw InvalidCsvFieldException(csvLine, "transactionType"),
                accountType = trimmedValues.getOrNull(5) ?: throw InvalidCsvFieldException(csvLine, "accountType"),
                quantity = trimmedValues.getOrNull(6)?.parseBigDecimal()?.abs(),
                price = trimmedValues.getOrNull(7)?.parseCurrency(),
                commissionsFees = trimmedValues.getOrNull(8)?.let { if (it.equals("FREE", ignoreCase = true)) ZERO else it.parseCurrency() },
                amount = trimmedValues.getOrNull(9)?.parseCurrency()?.abs() ?: throw InvalidCsvFieldException(csvLine, "amount"),
            )
        }
    }
}
