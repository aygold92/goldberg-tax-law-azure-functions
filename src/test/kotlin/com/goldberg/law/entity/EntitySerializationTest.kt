package com.goldberg.law.entity

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newClassifiedFilePages
import com.goldberg.law.entity.EntityValues.newStatement
import com.goldberg.law.function.activity.model.ProcessDataModelActivityInput
import com.goldberg.law.function.api.model.ClassificationProcessingOptions
import com.goldberg.law.util.GSON
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.gson.GsonBuilder
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

    @Test
    fun testClassificationRoundTripWithKotlinModule() {
        val mapper = ObjectMapper().apply {
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            registerModule(KotlinModule.Builder().build())
        }
        val input = ProcessDataModelActivityInput(requestId = "test-request", classification = newClassification(), processingOptions = ClassificationProcessingOptions())
        val json = mapper.writeValueAsString(input)
        val deserialized = mapper.readValue(json, ProcessDataModelActivityInput::class.java)
        assertThat(deserialized.classification.clientId).isEqualTo(input.classification.clientId)
        assertThat(deserialized.classification.fileId).isEqualTo(input.classification.fileId)
        assertThat(deserialized.classification.classificationId).isEqualTo(input.classification.classificationId)
    }

    @Test
    fun testClassificationRoundTripWithoutKotlinModule() {
        // Simulates the Azure Durable Functions SDK's Jackson mapper, which uses a plain
        // ObjectMapper without KotlinModule. @JsonCreator + @JsonProperty must be present
        // on Classification and InputFile so the constructor is called and $$delegate_* fields
        // are initialized.
        val sdkMapper = ObjectMapper().apply {
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
        val input = ProcessDataModelActivityInput(requestId = "test-request", classification = newClassification(), processingOptions = ClassificationProcessingOptions())
        val json = sdkMapper.writeValueAsString(input)
        val deserialized = sdkMapper.readValue(json, ProcessDataModelActivityInput::class.java)
        assertThat(deserialized.classification.clientId).isEqualTo(input.classification.clientId)
        assertThat(deserialized.classification.fileId).isEqualTo(input.classification.fileId)
        assertThat(deserialized.classification.classificationId).isEqualTo(input.classification.classificationId)
    }

    @Test
    fun testClassificationRoundTripWithGson() {
        // Simulates the Azure Durable Functions SDK's GSON deserializer, which uses a plain
        // GsonBuilder without any custom type adapters. The @JsonAdapter annotation on
        // Classification and InputFile must cause the constructor to be called so that
        // $$delegate_* fields are initialized and delegated properties don't NPE.
        val sdkGson = GsonBuilder().create()
        val input = ProcessDataModelActivityInput(requestId = "test-request", classification = newClassification(), processingOptions = ClassificationProcessingOptions())
        val json = sdkGson.toJson(input)
        val deserialized = sdkGson.fromJson(json, ProcessDataModelActivityInput::class.java)
        assertThat(deserialized.classification.clientId).isEqualTo(input.classification.clientId)
        assertThat(deserialized.classification.fileId).isEqualTo(input.classification.fileId)
        assertThat(deserialized.classification.classificationId).isEqualTo(input.classification.classificationId)
    }

    companion object {
        val FILE = ClassifiedFile(UUID.randomUUID(), listOf(
            ClassifiedPages(setOf(4,3,2), DocumentType.BankTypes.WF_BANK),
            ClassifiedPages(setOf(9,7,5), DocumentType.BankTypes.B_OF_A)
        ))
    }
}