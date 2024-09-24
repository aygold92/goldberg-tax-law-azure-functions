package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.request.AzureAnalyzeDocumentsRequest

data class ProcessStatementsActivityInput @JsonCreator constructor(
    @JsonProperty val requestId: String,
    @JsonProperty val request: AzureAnalyzeDocumentsRequest,
    @JsonProperty val documentDataModels: Collection<DocumentDataModelContainer>,
)