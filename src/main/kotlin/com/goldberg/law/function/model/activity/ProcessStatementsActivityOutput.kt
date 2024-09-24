package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class ProcessStatementsActivityOutput @JsonCreator constructor(
    @JsonProperty("fileNames") val fileNames: Collection<String>
)