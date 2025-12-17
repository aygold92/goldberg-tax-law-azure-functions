package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel

data class DocumentDataModelContainer(
    val statementDataModel: StatementDataModel? = null,
    val checkDataModel: CheckDataModel? = null,
    val extraPageDataModel: ExtraPageDataModel? = null,
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