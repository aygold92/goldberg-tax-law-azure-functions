package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.util.pageNumber
import com.goldberg.law.util.positiveCurrencyValue
import com.goldberg.law.util.valueAsInt
import java.math.BigDecimal
import java.util.*

data class TransactionTableDepositWithdrawalRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("checkNumber") val checkNumber: Int?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("depositAmount") val depositAmount: BigDecimal?,
    @JsonProperty("withdrawalAmount") val withdrawalAmount: BigDecimal?,
    @JsonProperty("page") override val page: Int,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: ClassifiedPdfMetadata): TransactionHistoryRecord = TransactionHistoryRecord(
        id = this.id,
        date = fromWrittenDateStatementDateOverride(this.date, statementDate),
        checkNumber = this.checkNumber,
        description = this.description,
        // TODO: can we do better for the case it has both?
        amount = this.depositAmount ?: this.withdrawalAmount?.negate(),
        filePageNumber = metadata.pagesOrdered[page - 1]
    )

    object Keys {
        const val DATE = "Date"
        const val CHECK_NUMBER = "Check Number"
        const val DESCRIPTION = "Description"
        const val WITHDRAWALS_SUBTRACTIONS = "Withdrawals/ Subtractions"
        const val DEPOSITS_ADDITIONS = "Deposits/ Additions"
    }

    companion object {
        fun DocumentField.toTransactionTableDepositWithdrawalRecord() = this.valueMap.let { recordFields ->
            val (checkNumber, description) = getCheckNumberAndDescription(recordFields)
            TransactionTableDepositWithdrawalRecord(
                date = recordFields[Keys.DATE]?.valueString,
                checkNumber = checkNumber,
                description = description,
                depositAmount = recordFields[Keys.DEPOSITS_ADDITIONS]?.positiveCurrencyValue(),
                withdrawalAmount = recordFields[Keys.WITHDRAWALS_SUBTRACTIONS]?.positiveCurrencyValue(),
                page = recordFields.pageNumber()
            )
        }

        /**
         * Some bank statements merge the CheckNumber and Description field into one
         */
        private fun getCheckNumberAndDescription(fieldMap: Map<String, DocumentField>): Pair<Int?, String?> {
            val checkContent = fieldMap[Keys.CHECK_NUMBER]?.content?.trim()
            val descriptionValue = fieldMap[Keys.DESCRIPTION]?.valueString?.trim()
            return if (checkContent?.matches(NUMBER_CHECK_REGEX) == true) {
                NUMBER_CHECK_REGEX.find(checkContent)!!.destructured.let {
                    Pair(it.component1().toInt(), it.component2())
                }
            } else if (descriptionValue?.matches(NUMBER_CHECK_REGEX) == true) {
                NUMBER_CHECK_REGEX.find(descriptionValue)!!.destructured.let {
                    Pair(it.component1().toInt(), it.component2())
                }
            } else {
                Pair(fieldMap[Keys.CHECK_NUMBER]?.valueAsInt(), descriptionValue)
            }
        }

        // don't need ^ and $ in regex and end because matches() assumes that already
        private val NUMBER_CHECK_REGEX = "(\\d+)(check)".toRegex(RegexOption.IGNORE_CASE)
    }
}