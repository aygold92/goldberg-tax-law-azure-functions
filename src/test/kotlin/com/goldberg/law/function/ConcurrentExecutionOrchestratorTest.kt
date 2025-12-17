package com.goldberg.law.function

import com.goldberg.law.document.model.StatementModelValues.REQUEST_ID
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.CHECK_ID
import com.goldberg.law.entity.EntityValues.CHECK_ID_2
import com.goldberg.law.entity.EntityValues.CLASSFN_ID
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_2
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_3
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_4
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_5
import com.goldberg.law.entity.EntityValues.FILE_ID
import com.goldberg.law.entity.EntityValues.FILE_ID_2
import com.goldberg.law.entity.EntityValues.FILE_ID_3
import com.goldberg.law.entity.EntityValues.STMT_ID
import com.goldberg.law.entity.EntityValues.STMT_ID_2
import com.goldberg.law.entity.EntityValues.STMT_ID_3
import com.goldberg.law.entity.EntityValues.STMT_ID_4
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.function.activity.ProcessDataModelActivity
import com.goldberg.law.function.activity.model.ProcessDataModelActivityInput
import com.goldberg.law.function.activity.model.ProcessDataModelActivityOutput
import com.goldberg.law.function.model.ExtractedDocumentIds
import com.goldberg.law.function.model.tracking.OrchestrationStatus
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskOrchestrationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class ConcurrentExecutionOrchestratorTest {
    private val executionOrchestrator = ConcurrentExecutionOrchestrator(2)
    @Mock
    private val orchestrationStatus: OrchestrationStatus = mock()

    @Mock
    private val mockContext: TaskOrchestrationContext = mock()

    @Mock
    private val processDataModelTask: Task<ProcessDataModelActivityOutput> = mock()

    @Mock
    private val anyOfTask: Task<Task<*>> = mock()

    @BeforeEach
    fun setup() {
        whenever(mockContext.instanceId).thenReturn(REQUEST_ID)
        whenever(mockContext.callActivity(eq(ProcessDataModelActivity.FUNCTION_NAME), any(), eq(ProcessDataModelActivityOutput::class.java)))
            .thenReturn(processDataModelTask)
        whenever(mockContext.anyOf(any<List<Task<ProcessDataModelActivityOutput>>>()))
            .thenReturn(anyOfTask)
        whenever(anyOfTask.await()).thenReturn(processDataModelTask)

        whenever(orchestrationStatus.updateDoc(any(), any())).thenReturn(orchestrationStatus)
        whenever(orchestrationStatus.save()).thenReturn(orchestrationStatus)
    }

    @Test
    fun testProcessDataModelOneLoop() {
        val ret1 = ProcessDataModelActivityOutput(fileId = FILE_ID, extractedDocumentIds = ExtractedDocumentIds(checkIds = setOf(CHECK_ID)))
        val ret2 = ProcessDataModelActivityOutput(fileId = FILE_ID, extractedDocumentIds = ExtractedDocumentIds(statementIds = setOf(STMT_ID)))

        whenever(processDataModelTask.await()).thenReturn(ret1, ret2)

        val docsToAnalyze = listOf(
            newClassification(classificationId = CLASSFN_ID_2, type = DocumentType.CheckTypes.B_OF_A_CHECK),
            newClassification()
        )

        val processDataModelActivityOutputs = executionOrchestrator.execProcessDataModels(
            mockContext, docsToAnalyze, orchestrationStatus
        )

        assertThat(processDataModelActivityOutputs.toSet()).isEqualTo(setOf(ret1, ret2))

        verify(processDataModelTask, times(2)).await()

        verify(orchestrationStatus, times(2)).updateDoc(eq(FILE_ID), any())
        verify(orchestrationStatus, times(2)).save()
        verify(mockContext, times(2)).instanceId
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, docsToAnalyze[0]), ProcessDataModelActivityOutput::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, docsToAnalyze[1]), ProcessDataModelActivityOutput::class.java)
        verify(mockContext, times(2)).anyOf(any<List<Task<*>>>())
//        verify(mockContext, times(2)).anyOf(argThat<List<Task<*>>> { l -> l.size == numWorkers })

        verifyNoMoreInteractions(mockContext, orchestrationStatus, processDataModelTask)
    }

    @Test
    fun testProcessDataModelOneLoopMultipleStatementsPerClassification() {
        val ret1 = ProcessDataModelActivityOutput(fileId = FILE_ID, extractedDocumentIds = ExtractedDocumentIds(checkIds = setOf(CHECK_ID, CHECK_ID_2)))
        val ret2 = ProcessDataModelActivityOutput(fileId = FILE_ID, extractedDocumentIds = ExtractedDocumentIds(statementIds = setOf(STMT_ID, STMT_ID_2)))

        whenever(processDataModelTask.await()).thenReturn(ret1, ret2)

        val docsToAnalyze = listOf(
            newClassification(classificationId = FILE_ID_2, type = DocumentType.CheckTypes.B_OF_A_CHECK),
            newClassification()
        )

        val processDataModelActivityOutputs = executionOrchestrator.execProcessDataModels(
            mockContext, docsToAnalyze, orchestrationStatus
        )

        assertThat(processDataModelActivityOutputs.toSet()).isEqualTo(setOf(ret1, ret2))

        verify(processDataModelTask, times(2)).await()

        verify(orchestrationStatus, times(2)).updateDoc(eq(FILE_ID), any())
        verify(orchestrationStatus, times(2)).save()
        verify(mockContext, times(2)).instanceId
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, docsToAnalyze[0]),
            ProcessDataModelActivityOutput::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, docsToAnalyze[1]),
            ProcessDataModelActivityOutput::class.java)
        verify(mockContext, times(2)).anyOf(any<List<Task<*>>>())
//        verify(mockContext, times(2)).anyOf(argThat<List<Task<*>>> { l -> l.size == numWorkers })

        verifyNoMoreInteractions(mockContext, orchestrationStatus, processDataModelTask)
    }

    @Test
    fun testProcessDataModelMultipleLoops() {
        val ret1 = ProcessDataModelActivityOutput(fileId = FILE_ID, extractedDocumentIds = ExtractedDocumentIds(checkIds = setOf(CHECK_ID)))
        val ret2 = ProcessDataModelActivityOutput(fileId = FILE_ID, extractedDocumentIds = ExtractedDocumentIds(statementIds = setOf(STMT_ID)))
        val ret3 = ProcessDataModelActivityOutput(fileId = FILE_ID_3, extractedDocumentIds = ExtractedDocumentIds(statementIds = setOf(STMT_ID_2)))
        val ret4 = ProcessDataModelActivityOutput(fileId = FILE_ID_3, extractedDocumentIds = ExtractedDocumentIds(statementIds = setOf(STMT_ID_3)))
        val ret5 = ProcessDataModelActivityOutput(fileId = FILE_ID_3, extractedDocumentIds = ExtractedDocumentIds(statementIds = setOf(STMT_ID_4)))

        whenever(processDataModelTask.await()).thenReturn(ret1, ret2, ret3, ret4, ret5)

        val docsToAnalyze = listOf(
            newClassification(FILE_ID, CLASSFN_ID_5, DocumentType.CheckTypes.B_OF_A_CHECK),
            newClassification(FILE_ID, CLASSFN_ID, DocumentType.BankTypes.B_OF_A),
            newClassification(FILE_ID_3, CLASSFN_ID_2, DocumentType.BankTypes.B_OF_A),
            newClassification(FILE_ID_3, CLASSFN_ID_3, DocumentType.BankTypes.B_OF_A),
            newClassification(FILE_ID_3, CLASSFN_ID_4, DocumentType.BankTypes.B_OF_A),
        )
        val analyzedModels = executionOrchestrator.execProcessDataModels(mockContext, docsToAnalyze, orchestrationStatus)

        assertThat(analyzedModels.toSet()).isEqualTo(setOf(ret1, ret2, ret3, ret4, ret5))

        verify(processDataModelTask, times(5)).await()

        verify(orchestrationStatus, times(2)).updateDoc(eq(FILE_ID), any())
        verify(orchestrationStatus, times(3)).updateDoc(eq(FILE_ID_3), any())
        verify(orchestrationStatus, times(5)).save()
        verify(mockContext, times(5)).instanceId
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, docsToAnalyze[0]), ProcessDataModelActivityOutput::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, docsToAnalyze[1]), ProcessDataModelActivityOutput::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, docsToAnalyze[2]), ProcessDataModelActivityOutput::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, docsToAnalyze[3]), ProcessDataModelActivityOutput::class.java)
        verify(mockContext).callActivity(ProcessDataModelActivity.FUNCTION_NAME, ProcessDataModelActivityInput(REQUEST_ID, docsToAnalyze[4]), ProcessDataModelActivityOutput::class.java)
        verify(mockContext, times(5)).anyOf((any<List<Task<*>>>()))

        verifyNoMoreInteractions(mockContext, orchestrationStatus, processDataModelTask)
    }
}