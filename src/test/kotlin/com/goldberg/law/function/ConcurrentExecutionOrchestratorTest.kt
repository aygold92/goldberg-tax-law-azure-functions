package com.goldberg.law.function

import com.goldberg.law.document.model.ModelValues.CLIENT_NAME
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.REQUEST_ID
import com.goldberg.law.document.model.StatementModelValues.Companion.METADATA_1
import com.goldberg.law.document.model.StatementModelValues.Companion.METADATA_2
import com.goldberg.law.document.model.StatementModelValues.Companion.METADATA_3
import com.goldberg.law.document.model.StatementModelValues.Companion.METADATA_4
import com.goldberg.law.document.model.StatementModelValues.Companion.METADATA_5
import com.goldberg.law.document.model.StatementModelValues.Companion.OTHER_FILENAME
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_1
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_2
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_3
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_4
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_5
import com.goldberg.law.function.activity.ProcessDataModelActivity
import com.goldberg.law.function.activity.UpdateMetadataActivity
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.activity.ProcessDataModelActivityInput
import com.goldberg.law.function.model.activity.UpdateMetadataActivityInput
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.tracking.OrchestrationStatus
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskOrchestrationContext
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class ConcurrentExecutionOrchestratorTest {

    private val numWorkers = 2

    private val executionOrchestrator = ConcurrentExecutionOrchestrator()
    @Mock
    private val orchestrationStatus: OrchestrationStatus = mock()

    @Mock
    private val mockContext: TaskOrchestrationContext = mock()

    @Mock
    private val processDataModelTask: Task<DocumentDataModelContainer> = mock()

    @Mock
    private val updateMetadataTask: Task<Void> = mock()

    @Mock
    private val otherUpdateMetadataTask: Task<Void> = mock()

    @Mock
    private val anyOfTask: Task<Task<*>> = mock()

    @BeforeEach
    fun setup() {
        whenever(mockContext.instanceId).thenReturn(REQUEST_ID)
        whenever(mockContext.callActivity(eq(ProcessDataModelActivity.FUNCTION_NAME), any(), eq(DocumentDataModelContainer::class.java)))
            .thenReturn(processDataModelTask)
        whenever(mockContext.callActivity(eq(UpdateMetadataActivity.FUNCTION_NAME), any<UpdateMetadataActivityInput>()))
            .thenReturn(updateMetadataTask, otherUpdateMetadataTask)
        whenever(mockContext.anyOf(any<List<Task<DocumentDataModelContainer>>>()))
            .thenReturn(anyOfTask)
        whenever(anyOfTask.await()).thenReturn(processDataModelTask)

        whenever(orchestrationStatus.updateDoc(any(), any())).thenReturn(orchestrationStatus)
        whenever(orchestrationStatus.save()).thenReturn(orchestrationStatus)
    }

    @Test
    fun testProcessDataModelOneLoop() {
        val updateMetadataTasks: MutableList<Task<Void>> = mutableListOf()

        whenever(processDataModelTask.await()).thenReturn(
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_1),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_2)
        )

        whenever(orchestrationStatus.getNumStatementsForDoc(any())).thenReturn(2)
        whenever(orchestrationStatus.docIsComplete(any()))
            .thenReturn(false, true)

        val docsToAnalyze = listOf(METADATA_1, METADATA_2)

        val analyzedModels = executionOrchestrator.execProcessDataModels(
            mockContext, docsToAnalyze, orchestrationStatus, 2, CLIENT_NAME, updateMetadataTasks
        )

        assertThat(analyzedModels.toSet()).isEqualTo(setOf(
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_1),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_2)
        ))

        assertThat(updateMetadataTasks).hasSize(1)

        verify(processDataModelTask, times(2)).await()

        verify(orchestrationStatus, times(2)).updateDoc(eq(FILENAME), any())
        verify(orchestrationStatus, times(2)).docIsComplete(FILENAME)
        verify(orchestrationStatus, times(2)).save()
        verify(orchestrationStatus).getNumStatementsForDoc(FILENAME)
        verify(mockContext, times(2)).instanceId
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, METADATA_1), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, METADATA_2), DocumentDataModelContainer::class.java)
        verify(mockContext, times(2)).anyOf(any<List<Task<*>>>())
//        verify(mockContext, times(2)).anyOf(argThat<List<Task<*>>> { l -> l.size == numWorkers })

        verify(mockContext).callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(CLIENT_NAME, FILENAME, InputFileMetadata(2, true, true)))


        verifyNoMoreInteractions(mockContext, orchestrationStatus, processDataModelTask, updateMetadataTask)

    }

    @Test
    fun testProcessDataModelMultipleLoops() {
        val updateMetadataTasks: MutableList<Task<Void>> = mutableListOf()

        whenever(processDataModelTask.await()).thenReturn(
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_1),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_2),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_3),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_4),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_5)
        )

        whenever(orchestrationStatus.getNumStatementsForDoc(any())).thenReturn(2)
        whenever(orchestrationStatus.docIsComplete(any()))
            .thenReturn(false, false, false, true, true)

        val docsToAnalyze = listOf(METADATA_1, METADATA_2, METADATA_3, METADATA_4, METADATA_5)
        val analyzedModels = executionOrchestrator.execProcessDataModels(
            mockContext, docsToAnalyze, orchestrationStatus, 2, CLIENT_NAME, updateMetadataTasks
        )

        assertThat(analyzedModels.toSet()).isEqualTo(setOf(
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_1),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_2),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_3),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_4),
            DocumentDataModelContainer(statementDataModel = STATEMENT_MODEL_5),
        ))

        assertThat(updateMetadataTasks).hasSize(2)

        verify(processDataModelTask, times(5)).await()

        verify(orchestrationStatus, times(3)).updateDoc(eq(FILENAME), any())
        verify(orchestrationStatus, times(2)).updateDoc(eq(OTHER_FILENAME), any())
        verify(orchestrationStatus, times(3)).docIsComplete(FILENAME)
        verify(orchestrationStatus, times(2)).docIsComplete(OTHER_FILENAME)
        verify(orchestrationStatus, times(5)).save()
        verify(orchestrationStatus).getNumStatementsForDoc(FILENAME)
        verify(orchestrationStatus).getNumStatementsForDoc(OTHER_FILENAME)
        verify(mockContext, times(5)).instanceId
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, METADATA_1), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, METADATA_2), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, METADATA_3), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, METADATA_4), DocumentDataModelContainer::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, CLIENT_NAME, METADATA_5), DocumentDataModelContainer::class.java)
        verify(mockContext, times(5)).anyOf((any<List<Task<*>>>()))
//        verify(mockContext)).anyOf(any<List<Task<*>>>( { l -> l.size == 1 }))

        verify(mockContext).callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(CLIENT_NAME, FILENAME, InputFileMetadata(2, true, true)))
        verify(mockContext).callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(CLIENT_NAME, OTHER_FILENAME, InputFileMetadata(2, true, true)))


        verifyNoMoreInteractions(mockContext, orchestrationStatus, processDataModelTask, updateMetadataTask)

    }
}