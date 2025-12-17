package com.goldberg.law.entity

import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newClassifiedFilePages
import com.goldberg.law.entity.EntityValues.newStatement
import com.goldberg.law.util.GSON
import com.goldberg.law.util.OBJECT_MAPPER
import com.nimbusds.jose.shaded.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class EntitySerializationTest {
    @Test
    fun testClassifiedFileSerialization() {
        val fileGson = GSON.fromJson(GSON.toJson(FILE), ClassifiedFile::class.java)
        val fileJackson = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(FILE), ClassifiedFile::class.java)
        assertThat(FILE).isEqualTo(fileJackson).isEqualTo(fileGson)

        assertThat(listOf(2,3,4))
            .isEqualTo(fileGson.classifications[0].pagesOrdered)
            .isEqualTo(fileJackson.classifications[0].pagesOrdered)
        assertThat(listOf(5,7,9))
            .isEqualTo(fileGson.classifications[1].pagesOrdered)
            .isEqualTo(fileJackson.classifications[1].pagesOrdered)
    }

    @Test
    fun testClassifiedFilePagesSerializable() {
        val classifiedFilePagesGSON = GSON.fromJson(GSON.toJson(newClassifiedFilePages()), ClassifiedFilePages::class.java)
        val classifiedFilePagesJackson = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(newClassifiedFilePages()), ClassifiedFilePages::class.java)
        assertThat(newClassifiedFilePages())
            .isEqualTo(classifiedFilePagesGSON)
            .isEqualTo(classifiedFilePagesJackson)

        assertThat(listOf(1))
            .isEqualTo(classifiedFilePagesGSON.pagesOrdered)
            .isEqualTo(classifiedFilePagesJackson.pagesOrdered)
    }
    @Test
    fun testStatementSerializable() {
        val stmt = newStatement()
        val statementGSON = GSON.fromJson(GSON.toJson(stmt), Statement::class.java)
        val statementJackson = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(stmt), Statement::class.java)
        assertThat(stmt)
            .isEqualTo(statementGSON)
            .isEqualTo(statementJackson)

        assertThat(listOf(1))
            .isEqualTo(statementGSON.pagesOrdered)
            .isEqualTo(statementJackson.pagesOrdered)

        assertThat(DocumentType.BANK)
            .isEqualTo(statementGSON.documentType)
            .isEqualTo(statementJackson.documentType)
    }

    companion object {
        val FILE = ClassifiedFile(UUID.randomUUID(), listOf(
            ClassifiedPages(setOf(4,3,2), DocumentType.BankTypes.WF_BANK),
            ClassifiedPages(setOf(9,7,5), DocumentType.BankTypes.B_OF_A)
        ))
    }
}