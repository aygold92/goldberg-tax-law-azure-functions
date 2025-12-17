package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class ListInputDocumentsRequest @JsonCreator constructor(
    @JsonProperty("clientId") val clientId: UUID
) 