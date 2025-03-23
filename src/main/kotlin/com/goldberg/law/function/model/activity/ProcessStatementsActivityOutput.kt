package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.CheckDataKey

data class ProcessStatementsActivityOutput @JsonCreator constructor(
    @JsonProperty("filenameStatementMap") val filenameStatementMap: Map<String, Set<String>>,
//    @JsonProperty("unusedChecks") val unusedChecks: Set<CheckDataKey>
)