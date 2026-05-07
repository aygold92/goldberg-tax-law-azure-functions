package com.goldberg.law.function.model.tracking

import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.CLASSFN_ID
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_2
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_3
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_4
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_5
import com.goldberg.law.entity.EntityValues.FILE_ID
import com.goldberg.law.entity.EntityValues.FILE_ID_2
import com.goldberg.law.entity.EntityValues.FILE_ID_3
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newClassifiedCheck
import com.goldberg.law.entity.EntityValues.newClassifiedStatement
import com.goldberg.law.entity.EntityValues.newInputFile
import com.goldberg.law.util.KotlinExtensionsTest.Companion.OBJECT_MAPPER
import com.microsoft.durabletask.TaskOrchestrationContext
import com.nimbusds.jose.shaded.gson.Gson
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.*

class OrchestrationStatusTest {
    @Mock
    private val mockContext: TaskOrchestrationContext = mock()

    @Test
    fun testInitialValueBasic() {
        val status = OrchestrationStatusFactory().new(
            mockContext,
            OrchestrationStage.EXTRACTING_DATA,
            setOf(newInputFile(), newInputFile(FILE_ID_2)),
            setOf(),
            setOf()
        )

        val documentStatusMap = mutableMapOf(
            FILE_ID to DocumentOrchestrationStatus(FILE_ID, null, null, null, false),
            FILE_ID_2 to DocumentOrchestrationStatus(FILE_ID_2, null, null, null, false),
        )

        assertThat(status).isEqualTo(OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, documentStatusMap))

        assertThat(status.getExternalStatus()).isEqualTo(OrchestrationStatus.ExternalOrchestrationStatus(
            stage = OrchestrationStage.EXTRACTING_DATA,
            docs = documentStatusMap,
            docsCompleted = null,
            totalDocs = null
        ))
    }

    @Test
    fun testInitialValue() {
        val status = OrchestrationStatusFactory().new(
            mockContext,
            OrchestrationStage.EXTRACTING_DATA,
            setOf(newInputFile(), newInputFile(FILE_ID_2)),
            setOf(
                newClassification(FILE_ID_3, CLASSFN_ID, DocumentType.BankTypes.WF_BANK),
                newClassification(FILE_ID_3, CLASSFN_ID_2, DocumentType.CheckTypes.CHECKS),
                newClassification(FILE_ID_3, CLASSFN_ID_5, DocumentType.BankTypes.WF_BANK, true),

            ),
            setOf(
                newClassifiedStatement(newClassification(FILE_ID_3, CLASSFN_ID_3, DocumentType.BankTypes.WF_BANK, true)),
                newClassifiedCheck(newClassification(FILE_ID_3, CLASSFN_ID_4, DocumentType.CheckTypes.CHECKS, true)),
            )
        )

        val documentStatusMap = mutableMapOf(
            FILE_ID to DocumentOrchestrationStatus(FILE_ID, null, null, null, false),
            FILE_ID_2 to DocumentOrchestrationStatus(FILE_ID_2, null, null, null, false),
            FILE_ID_3 to DocumentOrchestrationStatus(FILE_ID_3, 3, 2, 3, true),
        )

        assertThat(status).isEqualTo(OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, documentStatusMap))

        assertThat(status.getExternalStatus()).isEqualTo(OrchestrationStatus.ExternalOrchestrationStatus(
            stage = OrchestrationStage.EXTRACTING_DATA,
            docs = documentStatusMap,
            docsCompleted = 3,
            totalDocs = 5
        ))
    }

    @Test
    fun testSerializationGSONEmpty() {
        val status = OrchestrationStatusFactory()
            .new(mockContext, OrchestrationStage.EXTRACTING_DATA, setOf(newInputFile(), newInputFile(FILE_ID_2)), setOf(), setOf())
            .getExternalStatus()

        val result = Gson().toJson(status)
        assertThat(result).doesNotContain("ctx").doesNotContain("context")
    }

    @Test
    fun testSerializationGSONFull() {
        val status = OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, mutableMapOf(
                FILE_ID to DocumentOrchestrationStatus(FILE_ID, 5, 2, 1, true),
                FILE_ID_2 to DocumentOrchestrationStatus(FILE_ID_2, 5, 2, 0, true),
                FILE_ID_3 to DocumentOrchestrationStatus(FILE_ID_3, 5, 2, 7, true),
            )
        )

        val result = Gson().toJson(status.getExternalStatus())

        println(result)

        assertThat(result).contains("\"totalDocs\":21").contains("\"docsCompleted\":8")

        assertThat(result).doesNotContain("ctx").doesNotContain("context")
    }

    @Test
    fun testSerializationJackson() {
        val status = OrchestrationStatusFactory()
            .new(mockContext, OrchestrationStage.EXTRACTING_DATA, setOf(newInputFile(), newInputFile(FILE_ID_2)), setOf(), setOf())
            .getExternalStatus()

        assertThat(status.docsCompleted)

        val result = OBJECT_MAPPER.writeValueAsString(status)
        println(result)

        assertThat(result).contains("totalDocs").contains("docsCompleted")
        assertThat(result).doesNotContain("ctx").doesNotContain("context")
    }

    @Test
    fun testSave() {
        val documentStatusMap = mutableMapOf(
            FILE_ID to DocumentOrchestrationStatus(FILE_ID, 3, 1, 2, true),
            FILE_ID_2 to DocumentOrchestrationStatus(FILE_ID_2, 4, 1, 4, true)
        )
        val status = OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, documentStatusMap)

        status.save()

        verify(mockContext).setCustomStatus(OrchestrationStatus.ExternalOrchestrationStatus(
            OrchestrationStage.EXTRACTING_DATA, documentStatusMap.toMap(), 6, 9
        ))
        verifyNoMoreInteractions(mockContext)
    }

    @Test
    fun testUpdateStage() {
        val status = OrchestrationStatusFactory()
            .new(mockContext, OrchestrationStage.EXTRACTING_DATA, setOf(newInputFile(), newInputFile(FILE_ID_2)), setOf(), setOf())
            .updateStage(OrchestrationStage.MATCHING_CHECKS)

        val documentStatusMap = mutableMapOf(
            FILE_ID to DocumentOrchestrationStatus(FILE_ID, null, null, null, false),
            FILE_ID_2 to DocumentOrchestrationStatus(FILE_ID_2, null, null, null, false),
        )

        assertThat(status).isEqualTo(OrchestrationStatus(mockContext, OrchestrationStage.MATCHING_CHECKS, documentStatusMap))
    }

    @Test
    fun testUpdateDoc() {
        val status = OrchestrationStatusFactory()
            .new(mockContext, OrchestrationStage.EXTRACTING_DATA, setOf(newInputFile(), newInputFile(FILE_ID_2)), setOf(), setOf())

        status.updateDoc(FILE_ID) {
            numStatementPages = 5
            classified = false
        }.updateDoc(FILE_ID_2) {
            docsAnalyzed = 3
            numStatementPages = 10
            classified = true
        }

        val documentStatusMap = mutableMapOf(
            FILE_ID to DocumentOrchestrationStatus(FILE_ID, 5, null, null, false),
            FILE_ID_2 to DocumentOrchestrationStatus(FILE_ID_2, 10, null, 3, true),
        )

        assertThat(status).isEqualTo(OrchestrationStatus(mockContext, OrchestrationStage.EXTRACTING_DATA, documentStatusMap))
    }
}