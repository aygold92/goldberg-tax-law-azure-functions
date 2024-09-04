package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.roundToTwoDecimalPlaces

class TransactionTableDepositWithdrawalRecord(
    val date: String?,
    val checkNumber: Int?,
    val description: String?,
    val depositAmount: Double?,
    val withdrawalAmount: Double?
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementYear: String?, metadata: TransactionHistoryPageMetadata): TransactionHistoryRecord = TransactionHistoryRecord(
        date = fromWrittenDateStatementYearOverride(this.date, statementYear),
        checkNumber = this.checkNumber,
        description = this.description,
        // TODO: can we do better for the case it has both?
        amount = this.depositAmount ?: this.withdrawalAmount?.let { 0 - it },
        pageMetadata = metadata
    )

    object Keys {
        const val DATE = "Date"
        const val CHECK_NUMBER = "Check Number"
        const val DESCRIPTION = "Description"
        const val WITHDRAWALS_SUBTRACTIONS = "Withdrawals/ Subtractions"
        const val DEPOSITS_ADDITIONS = "Deposits/ Additions"
    }

    companion object {
        fun DocumentField.toTransactionTableDepositWithdrawalRecord() = this.getFieldMapHack().let { recordFields ->
            val (checkNumber, description) = getCheckNumberAndDescription(recordFields)
            TransactionTableDepositWithdrawalRecord(
                date = recordFields[Keys.DATE]?.valueAsString,
                checkNumber = checkNumber,
                description = description,
                depositAmount = recordFields[Keys.DEPOSITS_ADDITIONS]?.valueAsDouble?.roundToTwoDecimalPlaces(),
                withdrawalAmount = recordFields[Keys.WITHDRAWALS_SUBTRACTIONS]?.valueAsDouble?.roundToTwoDecimalPlaces()
            )
        }

        /**
         * Some bank statements merge the CheckNumber and Description field into one
         */
        private fun getCheckNumberAndDescription(fieldMap: Map<String, DocumentField>): Pair<Int?, String?> {
            val checkContent = fieldMap[Keys.CHECK_NUMBER]?.content?.trim()
            val descriptionValue = fieldMap[Keys.DESCRIPTION]?.valueAsString?.trim()
            return if (checkContent?.matches(NUMBER_CHECK_REGEX) == true) {
                NUMBER_CHECK_REGEX.find(checkContent)!!.destructured.let {
                    Pair(it.component1().toInt(), it.component2())
                }
            } else if (descriptionValue?.matches(NUMBER_CHECK_REGEX) == true) {
                NUMBER_CHECK_REGEX.find(descriptionValue)!!.destructured.let {
                    Pair(it.component1().toInt(), it.component2())
                }
            } else {
                Pair((fieldMap[Keys.CHECK_NUMBER]?.value as Number?)?.toInt(), descriptionValue)
            }
        }

        // don't need ^ and $ in regex and end because matches() assumes that already
        private val NUMBER_CHECK_REGEX = "(\\d+)(check)".toRegex(RegexOption.IGNORE_CASE)
    }
}