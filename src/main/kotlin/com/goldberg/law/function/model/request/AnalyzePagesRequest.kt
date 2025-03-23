package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.activity.ProcessDataModelActivityInput
import javax.inject.Inject

data class AnalyzePagesRequest @Inject constructor(
    @JsonProperty("pageRequests") val pageRequests: List<ProcessDataModelActivityInput>,
)