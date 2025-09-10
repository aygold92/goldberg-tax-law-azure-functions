package com.goldberg.law.function.model.metadata

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.toCurrency
import com.goldberg.law.util.asCurrency
import java.math.BigDecimal
import javax.inject.Inject

data class StatementMetadata @Inject constructor(
    @JsonProperty(MD5_KEY) val md5: String,
    @JsonProperty(SUSPICIOUS_KEY) val suspicious: Boolean,
    @JsonProperty(MISSING_CHECKS_KEY) val missingChecks: Boolean,
    @JsonProperty(MANUALLY_VERIFIED_KEY) val manuallyVerified: Boolean = false,
    @JsonProperty(BANK_TYPE_KEY) val bankType: DocumentType?,
    @JsonProperty(TOTAL_SPENDING) val totalSpending: BigDecimal,
    @JsonProperty(TOTAL_INCOME_CREDITS) val totalIncomeCredits: BigDecimal,
    @JsonProperty(NUM_TRANSACTIONS_KEY) val numTransactions: Int,
    @JsonProperty(FILENAME_KEY) val filename: String,
    @JsonProperty(PAGE_RANGE_KEY) val pageRange: Pair<Int, Int>,
    ) {
    // azure blob tags cannot include brackets
    // https://learn.microsoft.com/en-us/dotnet/api/azure.storage.blobs.specialized.blobbaseclient.settags
    fun toMetadataMap(): Map<String, String?> = mapOf(
        MD5_KEY to md5,
        SUSPICIOUS_KEY to suspicious.toString(),
        MISSING_CHECKS_KEY to missingChecks.toString(),
        MANUALLY_VERIFIED_KEY to manuallyVerified.toString(),
        BANK_TYPE_KEY to bankType?.toString(),
        TOTAL_SPENDING to totalSpending.toCurrency(),
        TOTAL_INCOME_CREDITS to totalIncomeCredits.toCurrency(),
        NUM_TRANSACTIONS_KEY to numTransactions.toString(),
        FILENAME_KEY to filename,
        PAGE_RANGE_KEY to "${pageRange.first}-${pageRange.second}",
    ).mapKeys { it.key.lowercase() }

    fun toTagsMap() = mapOf(
        MD5_KEY to md5,
        SUSPICIOUS_KEY to suspicious.toString(),
        MISSING_CHECKS_KEY to missingChecks.toString(),
        MANUALLY_VERIFIED_KEY to manuallyVerified.toString(),
    ).mapKeys { it.key.lowercase() }

    companion object {
        private const val MD5_KEY = "md5"
        private const val SUSPICIOUS_KEY = "suspicious"
        private const val MISSING_CHECKS_KEY = "missingChecks"
        private const val MANUALLY_VERIFIED_KEY = "manuallyVerified"
        private const val BANK_TYPE_KEY = "bankType"
        private const val TOTAL_SPENDING = "totalSpending"
        private const val TOTAL_INCOME_CREDITS = "totalIncomeCredits"
        private const val NUM_TRANSACTIONS_KEY = "numTransactions"
        private const val FILENAME_KEY = "filename"
        private const val PAGE_RANGE_KEY = "pageRange"

        fun fromMetadataMap(metadata: Map<String, String?>, filename: String): StatementMetadata {
            fun getOrThrow(key: String) = metadata[key.lowercase()] ?: throw IllegalArgumentException("Missing key: $key")
            return StatementMetadata(
                md5 = getOrThrow(MD5_KEY),
                suspicious = getOrThrow(SUSPICIOUS_KEY).toBoolean(),
                missingChecks = getOrThrow(MISSING_CHECKS_KEY).toBoolean(),
                manuallyVerified = metadata[MANUALLY_VERIFIED_KEY.lowercase()]?.toBoolean() ?: false,
                bankType = metadata[BANK_TYPE_KEY.lowercase()]?.let { DocumentType.valueOf(it) },
                totalSpending = getOrThrow(TOTAL_SPENDING).asCurrency()!!,
                totalIncomeCredits = getOrThrow(TOTAL_INCOME_CREDITS).asCurrency()!!,
                numTransactions = getOrThrow(NUM_TRANSACTIONS_KEY).toInt(),
                filename = metadata[FILENAME_KEY.lowercase()] ?: filename,
                pageRange = metadata[PAGE_RANGE_KEY.lowercase()]?.let {
                    val parts = it.split("-")
                    if (parts.size == 2) parts[0].toInt() to parts[1].toInt() else 1 to 1
                } ?: (1 to 1)
            )
        }
    }
}