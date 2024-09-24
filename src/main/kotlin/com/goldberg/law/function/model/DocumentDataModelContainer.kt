package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel

data class DocumentDataModelContainer @JsonCreator constructor(
    @JsonProperty("statementDataModel") val statementDataModel: StatementDataModel? = null,
    @JsonProperty("checkDataModel") val checkDataModel: CheckDataModel? = null,
    @JsonProperty("extraPageDataModel") val extraPageDataModel: ExtraPageDataModel? = null,
) {

    constructor(documentDataModel: DocumentDataModel): this(
        documentDataModel as? StatementDataModel,
        documentDataModel as? CheckDataModel,
        documentDataModel as? ExtraPageDataModel
    )

    @JsonIgnore
    fun isStatementModel() = statementDataModel != null

    @JsonIgnore
    fun isCheckModel() = checkDataModel != null

    @JsonIgnore
    fun isExtraPageDataModel() = extraPageDataModel != null

    @JsonIgnore
    fun getDocumentDataModel(): DocumentDataModel {
        if (listOfNotNull(statementDataModel, checkDataModel, extraPageDataModel).size != 1) {
            throw IllegalArgumentException("Must specific exactly one of statementDataModel, checkDataModel, and extraPageDataModel: $this")
        }
        return statementDataModel ?: checkDataModel ?: extraPageDataModel!!
    }
}