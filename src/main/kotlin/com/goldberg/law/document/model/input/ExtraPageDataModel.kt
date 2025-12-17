package com.goldberg.law.document.model.input

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.Classification

data class ExtraPageDataModel @JsonCreator constructor(
    @JsonProperty("classification") override val classification: Classification,
): DocumentDataModel(classification)