package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

// Request model for loading a specific bank statement

data class LoadBankStatementRequest @JsonCreator constructor(
    @JsonProperty("statementId") val statementId: UUID,
) 