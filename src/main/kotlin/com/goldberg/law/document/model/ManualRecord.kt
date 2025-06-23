package com.goldberg.law.document.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.tables.TransactionRecord
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import java.math.BigDecimal
import java.util.*

data class ManualRecord @JsonCreator constructor(
    @Transient
    @JsonProperty("id") val otherId: String?,
    @JsonProperty("date") val date: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("amount") val amount: BigDecimal?,
    @JsonProperty("checkNumber") val checkNumber: Int?,
    @JsonProperty("checkPdfMetadata") val checkPdfMetadata: ClassifiedPdfMetadata?,
    @JsonProperty("page") override val page: Int,
): TransactionRecord(otherId ?: UUID.randomUUID().toString()) {

    // TODO: match check number with checks on the bank statement
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: ClassifiedPdfMetadata): TransactionHistoryRecord {
        return TransactionHistoryRecord(
            id = this.id,
            date = fromWrittenDateStatementDateOverride(this.date, statementDate),
            description = this.description,
            checkNumber = this.checkNumber,
            amount = amount,
            filePageNumber = page  // TODO: should it be this?: metadata.pagesOrdered[page - 1]
        )
    }
}