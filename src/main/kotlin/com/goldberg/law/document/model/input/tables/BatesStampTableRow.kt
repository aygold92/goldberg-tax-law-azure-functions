package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.util.*

data class BatesStampTableRow @JsonCreator constructor(
    @JsonProperty("val") val `val`: String,
    @JsonProperty("page") val page: Int,
) {
    object Keys {
        const val VAL = "val"
    }
    companion object {
        fun DocumentField.toBatesStampTableRow() = this.valueMap.let { recordFields ->
            BatesStampTableRow(
                // this should never be null, otherwise it wouldn't even show up in the table
                `val` = recordFields[Keys.VAL]?.valueString ?: "",
                page = recordFields.pageNumber()
            )
        }
    }
}