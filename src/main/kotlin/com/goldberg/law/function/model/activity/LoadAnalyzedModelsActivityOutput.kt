package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.DocumentDataModelContainer

data class LoadAnalyzedModelsActivityOutput @JsonCreator constructor(
    @JsonProperty("models") val models: List<DocumentDataModelContainer>
)