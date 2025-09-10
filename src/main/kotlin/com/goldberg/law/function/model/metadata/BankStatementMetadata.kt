package com.goldberg.law.function.model.metadata

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.BankStatementKey
import javax.inject.Inject

/**
 * Metadata for a bank statement, including the BankStatementKey and the StatementMetadata.
 */
data class BankStatementMetadata @Inject constructor(
    @JsonProperty("key") val key: BankStatementKey?,
    @JsonProperty("metadata") val metadata: StatementMetadata
) 