package com.goldberg.law.function

import com.goldberg.law.document.model.StatementModelValues.REQUEST_ID
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.CLASSFN_ID
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_2
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_3
import com.goldberg.law.entity.EntityValues.CLASSFN_ID_4
import com.goldberg.law.entity.EntityValues.CLIENT_ID
import com.goldberg.law.entity.EntityValues.FILE_ID
import com.goldberg.law.entity.EntityValues.FILE_ID_2
import com.goldberg.law.entity.EntityValues.STMT_ID
import com.goldberg.law.entity.EntityValues.STMT_ID_2
import com.goldberg.law.entity.EntityValues.STMT_ID_3
import com.goldberg.law.entity.EntityValues.STMT_ID_4
import com.goldberg.law.entity.EntityValues.STMT_ID_5
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newInputFile
import com.goldberg.law.function.activity.GetFilesToProcessActivity
import com.goldberg.law.function.activity.MatchChecksToStatementsActivity
import com.goldberg.law.function.activity.model.*
import com.goldberg.law.function.api.model.AnalyzeDocumentResult
import com.goldberg.law.function.api.model.AzureAnalyzeDocumentsRequest
import com.goldberg.law.function.model.ExtractedDocumentIds
import com.goldberg.law.function.model.tracking.OrchestrationStage
import com.goldberg.law.function.model.tracking.OrchestrationStatus
import com.goldberg.law.function.model.tracking.OrchestrationStatusFactory
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskOrchestrationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class PdfDataExtractorOrchestratorFunctionTest {
    @Mock
    private val mockContext: TaskOrchestrationContext = mock()

    @Mock
    private val orchestrationStatusFactory: OrchestrationStatusFactory = mock()

    @Mock
    private val orchestrationStatus: OrchestrationStatus = mock()

    @Mock
    private val concurrentExecutionOrchestrator: ConcurrentExecutionOrchestrator = mock()

    @Mock
    private val getFilesToProcessTask: Task<GetFilesToProcessActivityOutput> = mock()

    @Mock
    private val matchCheckToStatementsTask: Task<MatchChecksToStatementsActivityOutput> = mock()

    private val dataExtractorOrchestratorFunction: PdfDataExtractorOrchestratorFunction = PdfDataExtractorOrchestratorFunction(concurrentExecutionOrchestrator, orchestrationStatusFactory)

    @BeforeEach
    fun setup() {
        whenever(mockContext.instanceId).thenReturn(REQUEST_ID)
        whenever(mockContext.isReplaying).thenReturn(false)

        whenever(mockContext.callActivity(eq(GetFilesToProcessActivity.FUNCTION_NAME), any(), eq(
            GetFilesToProcessActivityOutput::class.java)))
            .thenReturn(getFilesToProcessTask)

        whenever(orchestrationStatusFactory.new(any(), any(), any(), any(), any()))
            .thenReturn(orchestrationStatus)

        whenever(orchestrationStatus.updateStage(any())).thenReturn(orchestrationStatus)
        whenever(orchestrationStatus.updateDoc(any(), any())).thenReturn(orchestrationStatus)
        whenever(orchestrationStatus.save()).thenReturn(orchestrationStatus)

        whenever(mockContext.callActivity(eq(MatchChecksToStatementsActivity.FUNCTION_NAME), any<MatchChecksToStatementsActivityInput>(), eq(MatchChecksToStatementsActivityOutput::class.java)))
            .thenReturn(matchCheckToStatementsTask)
    }

    @Test
    fun testBasicNewDocuments() {
        whenever(mockContext.getInput(AzureAnalyzeDocumentsRequest::class.java)).thenReturn(INPUT)
        whenever(getFilesToProcessTask.await()).thenReturn(
            GetFilesToProcessActivityOutput(
                filesToClassify = setOf(newInputFile(), newInputFile(FILE_ID_2)),
                classificationsToAnalyze = setOf(),
                itemsCompleted = setOf()
            )
        )

        whenever(concurrentExecutionOrchestrator.execClassifyDocuments(any(), any(), any()))
            .thenReturn(mapOf(
                FILE_ID to listOf(
                    newClassification(FILE_ID, CLASSFN_ID, DocumentType.BankTypes.WF_BANK),
                    newClassification(FILE_ID, CLASSFN_ID_2, DocumentType.BankTypes.WF_BANK),
                ),
                FILE_ID_2 to listOf(
                    newClassification(FILE_ID_2, CLASSFN_ID_3, DocumentType.BankTypes.WF_BANK),
                    newClassification(FILE_ID_2, CLASSFN_ID_4, DocumentType.BankTypes.WF_BANK),
                )
            ))


        whenever(concurrentExecutionOrchestrator.execProcessDataModels(any(), any(), any()))
            .thenReturn(listOf(
                ProcessDataModelActivityOutput(fileId = FILE_ID, extractedDocumentIds = ExtractedDocumentIds(setOf(STMT_ID))),
                ProcessDataModelActivityOutput(fileId = FILE_ID, extractedDocumentIds = ExtractedDocumentIds(setOf(STMT_ID_2))),
                ProcessDataModelActivityOutput(fileId = FILE_ID_2, extractedDocumentIds = ExtractedDocumentIds(setOf(STMT_ID_3))),
                ProcessDataModelActivityOutput(fileId = FILE_ID_2, extractedDocumentIds = ExtractedDocumentIds(setOf(STMT_ID_4, STMT_ID_5))),
            ))

        whenever(matchCheckToStatementsTask.await())
            .thenReturn(MatchChecksToStatementsActivityOutput(emptyList()))

        val result = dataExtractorOrchestratorFunction.pdfDataExtractorOrchestrator(mockContext)

        val documentsByFile = mapOf(
            FILE_ID to ExtractedDocumentIds(setOf(STMT_ID, STMT_ID_2)),
            FILE_ID_2 to ExtractedDocumentIds(setOf(STMT_ID_3, STMT_ID_4, STMT_ID_5)),
        )

        assertThat(result).isEqualTo(
            AnalyzeDocumentResult(AnalyzeDocumentResult.Status.SUCCESS, documentsByFile, null)
        )

        verify(mockContext).getInput(AzureAnalyzeDocumentsRequest::class.java)

        verify(mockContext, atLeastOnce()).isReplaying
        verify(mockContext, atLeastOnce()).instanceId

        verify(mockContext).callActivity(
            GetFilesToProcessActivity.FUNCTION_NAME,
            GetFilesToProcessActivityInput(REQUEST_ID, setOf(FILE_ID, FILE_ID_2)),
            GetFilesToProcessActivityOutput::class.java
        )

        verify(concurrentExecutionOrchestrator).execClassifyDocuments(
            eq(mockContext),
            argThat { arg -> arg.toSet() == setOf(newInputFile(), newInputFile(FILE_ID_2)) },
            eq(orchestrationStatus),
        )

        verify(concurrentExecutionOrchestrator).execProcessDataModels(
            eq(mockContext),
            argThat { arg -> arg.toSet() == setOf(
                newClassification(FILE_ID, CLASSFN_ID, DocumentType.BankTypes.WF_BANK),
                newClassification(FILE_ID, CLASSFN_ID_2, DocumentType.BankTypes.WF_BANK),
                newClassification(FILE_ID_2, CLASSFN_ID_3, DocumentType.BankTypes.WF_BANK),
                newClassification(FILE_ID_2, CLASSFN_ID_4, DocumentType.BankTypes.WF_BANK)
            ) },
            eq(orchestrationStatus),
        )

        verify(matchCheckToStatementsTask).await()

        verify(mockContext).callActivity(
            MatchChecksToStatementsActivity.FUNCTION_NAME,
            MatchChecksToStatementsActivityInput(REQUEST_ID, CLIENT_ID, documentsByFile),
            MatchChecksToStatementsActivityOutput::class.java
        )

        verify(orchestrationStatusFactory).new(mockContext, OrchestrationStage.CLASSIFYING_DOCUMENTS, setOf(newInputFile(), newInputFile(FILE_ID_2)), setOf(), setOf())
        verify(orchestrationStatus).updateStage(OrchestrationStage.EXTRACTING_DATA)
        verify(orchestrationStatus).updateStage(OrchestrationStage.MATCHING_CHECKS)
        verify(orchestrationStatus, times(2)).save()

        verifyNoMoreInteractions(mockContext, concurrentExecutionOrchestrator, orchestrationStatusFactory, orchestrationStatus)
    }

    companion object {
        private val INPUT = AzureAnalyzeDocumentsRequest(
            CLIENT_ID,
            setOf(FILE_ID, FILE_ID_2)
        )
    }
}