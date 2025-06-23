package com.goldberg.law.function.model.tracking

import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.StatementModelValues.Companion.OTHER_FILENAME
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.util.KotlinExtensionsTest.Companion.OBJECT_MAPPER
import com.microsoft.durabletask.TaskOrchestrationContext
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import com.nimbusds.jose.shaded.gson.Gson
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock

class OrchestrationStatusTest {
    @Mock
    private val mockContext: TaskOrchestrationContext = mock()

    @Test
    fun testSerializationGSONEmpty() {
        val status = OrchestrationStatusFactory().new(mockContext, OrchestrationStage.EXTRACTING_DATA, setOf("a", "b")).getExternalStatus()

        val result = Gson().toJson(status)
        assertThat(result).doesNotContain("ctx").doesNotContain("context")
    }

    @Test
    fun testSerializationGSONFull() {
        val status = OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, mutableMapOf(
                "a" to DocumentOrchestrationStatus("a", 5, 1, true),
                "b" to DocumentOrchestrationStatus("b", 5, 0, true),
                "c" to DocumentOrchestrationStatus("c", 5, 5, true),
            )
        )

        val result = Gson().toJson(status.getExternalStatus())

        println(result)

        assertThat(result).contains("\"totalStatements\":15").contains("\"statementsCompleted\":6")

        assertThat(result).doesNotContain("ctx").doesNotContain("context")
    }

    @Test
    fun testSerializationJackson() {
        val status = OrchestrationStatusFactory().new(mockContext, OrchestrationStage.EXTRACTING_DATA, setOf("a", "b")).getExternalStatus()

        val result = OBJECT_MAPPER.writeValueAsString(status)
        println(result)

        assertThat(result).contains("totalStatements").contains("statementsCompleted")
        assertThat(result).doesNotContain("ctx").doesNotContain("context")
    }

    @Test
    fun testSave() {
        val documentStatusMap = mutableMapOf(
            FILENAME to DocumentOrchestrationStatus(FILENAME, 3, 2, true),
            OTHER_FILENAME to DocumentOrchestrationStatus(OTHER_FILENAME, 4, 4, true)
        )
        val status = OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, documentStatusMap)

        status.save()

        verify(mockContext).setCustomStatus(OrchestrationStatus.ExternalOrchestrationStatus(
            OrchestrationStage.EXTRACTING_DATA, documentStatusMap.toMap(), 6, 7
        ))
        verifyNoMoreInteractions(mockContext)
    }

    @Test
    fun testNew() {
        val status = OrchestrationStatusFactory().new(mockContext, OrchestrationStage.EXTRACTING_DATA, setOf(FILENAME, OTHER_FILENAME))

        assertThat(status).isEqualTo(OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, mutableMapOf(
            FILENAME to DocumentOrchestrationStatus(FILENAME, null, null, null),
            OTHER_FILENAME to DocumentOrchestrationStatus(OTHER_FILENAME, null, null, null),
        )))
    }

    @Test
    fun testUpdateStage() {
        val status = OrchestrationStatusFactory().new(mockContext, OrchestrationStage.EXTRACTING_DATA, setOf(FILENAME, OTHER_FILENAME))
            .updateStage(OrchestrationStage.CREATING_BANK_STATEMENTS)

        assertThat(status).isEqualTo(OrchestrationStatus(mockContext, OrchestrationStage.CREATING_BANK_STATEMENTS, mutableMapOf(
            FILENAME to DocumentOrchestrationStatus(FILENAME, null, null, null),
            OTHER_FILENAME to DocumentOrchestrationStatus(OTHER_FILENAME, null, null, null),
        )))
    }

    @Test
    fun testUpdateDoc() {
        val status = OrchestrationStatusFactory().new(mockContext, OrchestrationStage.EXTRACTING_DATA, setOf(FILENAME, OTHER_FILENAME))

        status.updateDoc(FILENAME) {
            numStatements = 5
            classified = false
        }.updateDoc(OTHER_FILENAME) {
            statementsCompleted = 3
            numStatements = 10
            classified = true
        }

        assertThat(status).isEqualTo(OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, mutableMapOf(
            FILENAME to DocumentOrchestrationStatus(FILENAME, 5, null, false),
            OTHER_FILENAME to DocumentOrchestrationStatus(OTHER_FILENAME, 10, 3, true),
        )))
    }

    @Test
    fun testToMetadataMap() {
        val status = OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, mutableMapOf(
            FILENAME to DocumentOrchestrationStatus(FILENAME, 3, 2, true),
            OTHER_FILENAME to DocumentOrchestrationStatus(OTHER_FILENAME, 4, 4, true)
        ))

        assertThat(status.documentMetadataMap()).isEqualTo(mapOf(
                FILENAME to InputFileMetadata(3, true, false),
                OTHER_FILENAME to InputFileMetadata(4, true, true),
            ))
    }

    @Test
    fun testGetNumStatementsForDocAndDocComplete() {
        val status = OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, mutableMapOf(
            FILENAME to DocumentOrchestrationStatus(FILENAME, 3, 2, true),
            OTHER_FILENAME to DocumentOrchestrationStatus(OTHER_FILENAME, 4, 4, true)
        ))

        assertThat(status.getNumStatementsForDoc(FILENAME)).isEqualTo(3)
        assertThat(status.getNumStatementsForDoc(OTHER_FILENAME)).isEqualTo(4)
        assertThat(status.getNumStatementsForDoc("random")).isEqualTo(null)
        assertThat(status.docIsComplete(FILENAME)).isFalse()
        assertThat(status.docIsComplete(OTHER_FILENAME)).isTrue()
        assertThat(status.docIsComplete("random")).isFalse()
    }
}