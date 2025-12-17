package com.goldberg.law.document.model.input

import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.CLASSFN_ID
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_2
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_3
import com.goldberg.law.entity.EntityValues.FILE_ID
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newClassificationInfo
import com.goldberg.law.function.model.DocumentDataModelContainerTest.Companion.CHECK_DATA_MODEL
import com.goldberg.law.function.model.DocumentDataModelContainerTest.Companion.EXTRA_PAGE_DATA_MODEL
import com.goldberg.law.function.model.DocumentDataModelContainerTest.Companion.STATEMENT_DATA_MODEL
import com.goldberg.law.util.GSON
import com.goldberg.law.util.OBJECT_MAPPER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DocumentDataModelTest {
    @Test
    fun testDocumentDataModelInheritance() {
        val documentModels: List<DocumentDataModel> = listOf(
            StatementDataModel.blankModel(newClassification()),
            CheckDataModel.blankModel(newClassification(FILE_ID, CLASSFN_ID_2, DocumentType.CheckTypes.EAGLE_BANK_CHECK)),
            ExtraPageDataModel(newClassification(FILE_ID,CLASSFN_ID_3, DocumentType.IrrelevantTypes.EXTRA_PAGES)),
        )

        assertThat(documentModels[0].classification).isEqualTo(newClassification())
        assertThat(documentModels[1].classification).isEqualTo(newClassification(FILE_ID, CLASSFN_ID_2, DocumentType.CheckTypes.EAGLE_BANK_CHECK))
        assertThat(documentModels[2].classification).isEqualTo(newClassification(FILE_ID,CLASSFN_ID_3, DocumentType.IrrelevantTypes.EXTRA_PAGES))
    }

    @Test
    fun testStatementDataModelSerializableJackson() {
        val model = STATEMENT_DATA_MODEL
        val otherModel = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(model), StatementDataModel::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testStatementDataModelSerializableGson() {
        val model = STATEMENT_DATA_MODEL
        val otherModel = GSON.fromJson(GSON.toJson(model), StatementDataModel::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testCheckDataModelSerializableJackson() {
        val model = CHECK_DATA_MODEL
        val otherModel = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(model), CheckDataModel::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testCheckDataModelSerializableGson() {
        val model = CHECK_DATA_MODEL
        val otherModel = GSON.fromJson(GSON.toJson(model), CheckDataModel::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testExtraPageDataModelSerializableJackson() {
        val model = EXTRA_PAGE_DATA_MODEL
        val otherModel = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(model), ExtraPageDataModel::class.java)
        assertThat(otherModel).isEqualTo(model)
    }

    @Test
    fun testExtraPageDataModelSerializableGson() {
        val model = EXTRA_PAGE_DATA_MODEL
        val otherModel = GSON.fromJson(GSON.toJson(model), ExtraPageDataModel::class.java)
        assertThat(otherModel).isEqualTo(model)
    }
}