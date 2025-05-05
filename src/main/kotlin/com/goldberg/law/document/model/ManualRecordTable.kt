package com.goldberg.law.document.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.tables.TransactionTable

data class ManualRecordTable @JsonCreator constructor(
    @JsonProperty("records") override val records: List<ManualRecord>
) : TransactionTable(records)