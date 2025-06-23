package com.goldberg.law.document.model.input

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata

data class ExtraPageDataModel @JsonCreator constructor(
    @JsonProperty("pageMetadata") override val pageMetadata: ClassifiedPdfMetadata,
): DocumentDataModel(pageMetadata)