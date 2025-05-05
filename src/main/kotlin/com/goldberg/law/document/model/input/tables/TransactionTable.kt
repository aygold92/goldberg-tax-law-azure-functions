package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import kotlin.collections.LinkedHashMap

abstract class TransactionTable(@JsonIgnore @Transient open val records: List<TransactionRecord>) {
    fun createHistoryRecords(statementDate: Date?, metadata: TransactionHistoryPageMetadata, documentType: DocumentType): List<TransactionHistoryRecord> = records.map {
        it.toTransactionHistoryRecord(statementDate, metadata, documentType)
    }
}
abstract class TransactionRecord(open val id: String = UUID.randomUUID().toString()) {

    @JsonIgnore @Transient
    val logger = KotlinLogging.logger {}
    abstract fun toTransactionHistoryRecord(statementDate: Date?, metadata: TransactionHistoryPageMetadata, documentType: DocumentType): TransactionHistoryRecord

    fun fromWrittenDateStatementDateOverride(monthDay: String?, statementDate: Date?): String? {
        val date = fromWrittenDate(monthDay, statementDate?.getYearSafe())
        return (if (date != null && date.getMonthInt() == 11 && statementDate?.getMonthInt() == 0 && date.getYearInt() != (statementDate.getYearInt() - 1)) {
            Calendar.getInstance().apply {
                time = date
                add(Calendar.YEAR, -1)
            }.time
        } else date)?.toTransactionDate()
    }

    fun extractCheckNumber(desc: String?): Int? {
        if (desc == null) return null
        val match = CHECK_REGEX.find(desc) ?: return null
        return match.groupValues[1].toInt()
    }

    companion object {
        private val CHECK_REGEX = Regex("^Check (\\d+)$") // Matches "Check xxx" where xxx is a number
    }
}

// hack for unit tests needed
fun DocumentField.getFieldMapHack(): Map<String, DocumentField> = (this.value as Map<*, *>).let { recordMap ->
    if (recordMap.values.first() is LinkedHashMap<*, *>) {
        recordMap.map { entry ->
            entry.key as String to entry.value!!.asDocumentField()
        }.toMap()
    } else {
        recordMap as Map<String, DocumentField>
    }
}

// hack for unit tests
fun Any.asDocumentField(): DocumentField =
    if (this is LinkedHashMap<*,*>)
        OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(this), DocumentField::class.java)
    else
        this as DocumentField
