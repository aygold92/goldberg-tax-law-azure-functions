package com.goldberg.law.function.model.metadata

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.toCurrency
import java.math.BigDecimal
import javax.inject.Inject

data class StatementMetadata @Inject constructor(
    @JsonProperty(MD5_KEY) val md5: String,
    @JsonProperty(SUSPICIOUS_KEY) val suspicious: Boolean,
    @JsonProperty(MISSING_CHECKS_KEY) val missingchecks: Boolean,
    @JsonProperty(MANUALLY_VERIFIED_KEY) val manuallyverified: Boolean = false,
    @JsonProperty(BANK_TYPE_KEY) val banktype: DocumentType?,
    @JsonProperty(TOTAL_SPENDING) val totalspending: BigDecimal,
    @JsonProperty(TOTAL_INCOME_CREDITS) val totalincomecredits: BigDecimal,
    @JsonProperty(NUM_TRANSACTIONS_KEY) val numtransactions: Int,
    @JsonProperty(FILENAME_KEY) val filename: String,
    @JsonProperty(PAGE_RANGE_KEY) val pagerange: Pair<Int, Int>,
    ) {
    // azure blob tags cannot include brackets
    // https://learn.microsoft.com/en-us/dotnet/api/azure.storage.blobs.specialized.blobbaseclient.settags
    fun toMetadataMap(): Map<String, String?> = mapOf(
        MD5_KEY to md5,
        SUSPICIOUS_KEY to suspicious.toString(),
        MISSING_CHECKS_KEY to missingchecks.toString(),
        MANUALLY_VERIFIED_KEY to manuallyverified.toString(),
        BANK_TYPE_KEY to banktype?.toString(),
        TOTAL_SPENDING to totalspending.toCurrency(),
        TOTAL_INCOME_CREDITS to totalincomecredits.toCurrency(),
        NUM_TRANSACTIONS_KEY to numtransactions.toString(),
        FILENAME_KEY to filename,
        PAGE_RANGE_KEY to "${pagerange.first}-${pagerange.second}",
    )

    fun toTagsMap() = mapOf(
        MD5_KEY to md5,
        SUSPICIOUS_KEY to suspicious.toString(),
        MISSING_CHECKS_KEY to missingchecks.toString(),
        MANUALLY_VERIFIED_KEY to manuallyverified.toString(),
    )

    companion object {
        private const val MD5_KEY = "md5"
        private const val SUSPICIOUS_KEY = "suspicious"
        private const val MISSING_CHECKS_KEY = "missingchecks"
        private const val MANUALLY_VERIFIED_KEY = "manuallyverified"
        private const val BANK_TYPE_KEY = "banktype"
        private const val TOTAL_SPENDING = "totalspending"
        private const val TOTAL_INCOME_CREDITS = "totalincomecredits"
        private const val NUM_TRANSACTIONS_KEY = "numtransactions"
        private const val FILENAME_KEY = "filename"
        private const val PAGE_RANGE_KEY = "pagerange"
    }
}