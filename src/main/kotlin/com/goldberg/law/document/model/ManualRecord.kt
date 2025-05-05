package com.goldberg.law.document.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.tables.TransactionRecord
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.function.model.PdfPageData
import java.math.BigDecimal
import java.util.*

data class ManualRecord @JsonCreator constructor(
    @JsonIgnore @Transient
    @JsonProperty("id") override val id: String,
    @JsonProperty("date") val date: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("amount") val amount: BigDecimal?,
    @JsonProperty("checkNumber") val checkNumber: Int?,
    @JsonProperty("checkPageData") val checkPageData: PdfPageData?
): TransactionRecord() {

    // TODO: match check number with checks on the bank statement
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: TransactionHistoryPageMetadata, documentType: DocumentType): TransactionHistoryRecord {
        return TransactionHistoryRecord(
            id = this.id,
            date = fromWrittenDateStatementDateOverride(this.date, statementDate),
            description = this.description,
            checkNumber = this.checkNumber,
            amount = amount,
            pageMetadata = metadata
        )
    }
}