package com.goldberg.law.document.model.input

import com.goldberg.law.document.model.ModelValues.CHECK_FILENAME
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.newClassifiedPdfDocument
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.function.model.DocumentDataModelContainerTest.Companion.CHECK_DATA_MODEL
import com.goldberg.law.function.model.DocumentDataModelContainerTest.Companion.EXTRA_PAGE_DATA_MODEL
import com.goldberg.law.function.model.DocumentDataModelContainerTest.Companion.STATEMENT_DATA_MODEL
import com.goldberg.law.util.GSON
import com.goldberg.law.util.OBJECT_MAPPER
import com.nimbusds.jose.shaded.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DocumentDataModelTest {
    @Test
    fun testDocumentDataModelInheritance() {
        val documentModels: List<DocumentDataModel> = listOf(
            StatementDataModel.blankModel(newClassifiedPdfDocument()),
            CheckDataModel.blankModel(newClassifiedPdfDocument(filename = CHECK_FILENAME, classification = DocumentType.CheckTypes.EAGLE_BANK_CHECK)),
            ExtraPageDataModel(newClassifiedPdfDocument(classification = DocumentType.IrrelevantTypes.EXTRA_PAGES).toDocumentMetadata())
        )

        assertThat(documentModels[0].pageMetadata).isEqualTo(ClassifiedPdfMetadata(FILENAME, 1, DocumentType.BankTypes.WF_BANK))
        assertThat(documentModels[1].pageMetadata).isEqualTo(ClassifiedPdfMetadata(CHECK_FILENAME, 1, DocumentType.CheckTypes.EAGLE_BANK_CHECK))
        assertThat(documentModels[2].pageMetadata).isEqualTo(ClassifiedPdfMetadata(FILENAME, 1, DocumentType.IrrelevantTypes.EXTRA_PAGES))
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