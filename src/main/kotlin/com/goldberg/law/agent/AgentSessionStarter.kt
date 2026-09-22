package com.goldberg.law.agent

import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.entity.Classification
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * Starts agents against records this app already holds, and records the session on the record it ran for:
 * the splitter's on the file it classifies, an extraction's on the classification it extracts. That link is
 * what lets [AgentResultSaver] find a finished session's target, and what makes a second run a no-op unless
 * the caller overrides it.
 *
 * [AgentSessionLauncher] stays the seam to the agents themselves; this is the database-aware layer above it.
 */
@Singleton
class AgentSessionStarter @Inject constructor(
    private val launcher: AgentSessionLauncher,
    private val fileService: FileService,
    private val classificationService: ClassificationService,
) {
    private val logger = KotlinLogging.logger {}

    /** Uploads the file and splits it, unless it has already been split and [override] wasn't asked for. */
    fun startSplitter(fileId: UUID, override: Boolean = false): AgentStart {
        val file = fileService.loadFile(fileId)
        file.splitterSessionId?.takeUnless { override }?.let {
            logger.info { "File $fileId was already split by session $it; not starting another" }
            return AgentStart(it, started = false, anthropicFileId = file.anthropicFileId)
        }

        val (sessionId, anthropicFileId) = launcher.startSplitter(file)
        fileService.updateSplitterSession(fileId, sessionId, anthropicFileId)
        return AgentStart(sessionId, started = true, anthropicFileId = anthropicFileId)
    }

    /**
     * Extracts the statement(s) in a classification's page range. The page range, bank id and the check
     * pages inside the range all come from the file's classifications, so the caller needs only the id.
     */
    fun startStatementExtraction(classificationId: UUID, override: Boolean = false): AgentStart {
        val classification = classificationService.loadClassification(classificationId)
        alreadyExtracted(classification, override)?.let { return it }

        val pages = classification.pagesOrdered
        require(pages.isNotEmpty()) { "Classification $classificationId has no pages to extract" }

        val sessionId = launcher.startStatementExtraction(
            anthropicFileId = classification.anthropicFileIdOrThrow(),
            fileName = classification.inputFile.fileName,
            startPage = pages.first(),
            endPage = pages.last(),
            bankId = classification.classificationType,
            checkPages = checkPagesWithin(classification, pages.first()..pages.last()),
        )
        classificationService.updateExtractionSession(classificationId, sessionId)
        return AgentStart(sessionId, started = true)
    }

    fun startCheckExtraction(classificationId: UUID, override: Boolean = false): AgentStart {
        val classification = classificationService.loadClassification(classificationId)
        alreadyExtracted(classification, override)?.let { return it }

        val sessionId = launcher.startCheckExtraction(classification.anthropicFileIdOrThrow(), classification.pagesOrdered)
        classificationService.updateExtractionSession(classificationId, sessionId)
        return AgentStart(sessionId, started = true)
    }

    private fun alreadyExtracted(classification: Classification, override: Boolean): AgentStart? =
        classification.extractionSessionId?.takeUnless { override }?.let {
            logger.info { "Classification ${classification.classificationId} was already extracted by session $it; not starting another" }
            AgentStart(it, started = false)
        }

    /** The check pages the statement's own range covers, which the extraction agent skips over. */
    private fun checkPagesWithin(classification: Classification, range: IntRange): List<Int> =
        classificationService.loadClassifications(classification.fileId)
            .filter { it.documentType.isCheck() }
            .flatMap { it.pages }
            .filter { it in range }
            .sorted()

    private fun Classification.anthropicFileIdOrThrow(): String = inputFile.anthropicFileId
        ?: throw IllegalStateException("File $fileId has not been uploaded to Anthropic — run the splitter first")
}

/**
 * [started] is false when the record already had a session and the caller didn't override it: [sessionId] is
 * that earlier session, which is still the one to read results from.
 */
data class AgentStart(
    val sessionId: String,
    val started: Boolean,
    /** Only set by the splitter, which is what uploads the bundle. */
    val anthropicFileId: String? = null,
)
