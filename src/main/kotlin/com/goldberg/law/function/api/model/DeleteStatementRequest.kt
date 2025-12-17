package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

// Request model for deleting a specific bank statement

data class DeleteStatementRequest @JsonCreator constructor(
    @JsonProperty("statementId") val statementId: UUID,
)
