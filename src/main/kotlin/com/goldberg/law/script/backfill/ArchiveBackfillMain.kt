package com.goldberg.law.script.backfill

import com.goldberg.law.AppModule
import com.goldberg.law.agent.AgentSessionLauncher
import com.goldberg.law.agent.AgentSessionResult
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.database.tables.FilesTable
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.entity.Classification
import com.google.inject.Guice
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Archives `result.json` for classifications that were extracted before the archive existed, or whose
 * archive write failed after their records had already been committed.
 *
 * This is not only a one-off. `AgentResultSaver` writes the blob *after* the statements and checks commit, so
 * that a failed archive leaves `model_location` null rather than pointing at an analysis the database doesn't
 * have. That choice makes "records present, archive missing" a state the pipeline can legitimately reach, and
 * this is how it's repaired.
 *
 * `AgentResultSaver.save` can't do the job itself: those classifications already hold records, so it returns
 * early as `alreadySaved` without archiving, and `override = true` would archive only by replacing the
 * records first — discarding any manual corrections made since.
 *
 * Dry run unless `--apply` is passed. Re-running is safe: only rows still missing a location are considered.
 */
fun main(args: Array<String>) {
    val apply = args.contains("--apply")
    val clientId = args.indexOf("--client-id").takeIf { it >= 0 }?.let { UUID.fromString(args[it + 1]) }

    val injector = Guice.createInjector(AppModule())
    val db = injector.getInstance(Database::class.java)
    val launcher = injector.getInstance(AgentSessionLauncher::class.java)
    val dataManager = injector.getInstance(AzureStorageDataManager::class.java)
    val classificationService = injector.getInstance(ClassificationService::class.java)

    val pending = loadUnarchived(db, clientId)
    if (pending.isEmpty()) {
        logger.info { "Nothing to back-fill${clientId?.let { " for client $it" }.orEmpty()}" }
        return
    }
    logger.info {
        "${pending.size} classification(s) have an extraction session but no archived result" +
            "${clientId?.let { " (client $it)" }.orEmpty()}${if (apply) "" else " — dry run, pass -Papply=true to write"}"
    }

    val archived = mutableListOf<UUID>()
    val skipped = mutableListOf<String>()

    pending.forEach { classification ->
        val sessionId = classification.extractionSessionId!!
        val label = "${classification.classificationId} (${classification.classificationType}, session $sessionId)"

        val result = runCatching { launcher.fetchResult(sessionId) }.getOrElse { ex ->
            // An unreadable session is reported, not fatal: one expired or deleted session shouldn't
            // stop the rest of the batch from being recovered.
            skipped += "$label — could not read session: ${ex.message}"
            return@forEach
        }

        when {
            result.status != AgentSessionResult.Status.COMPLETED ->
                skipped += "$label — session is ${result.status}${result.error?.let { ", $it" }.orEmpty()}"
            result.outputJson == null ->
                skipped += "$label — session completed but has no ${com.goldberg.law.agent.ManagedAgent.OUTPUT_FILE}"
            !apply -> {
                logger.info { "would archive $label (${result.outputJson!!.length} chars)" }
                archived += classification.classificationId
            }
            else -> {
                val location = dataManager.saveAgentOutput(classification, result.outputJson!!)
                classificationService.updateModelLocation(classification.classificationId, location)
                logger.info { "archived $label to $location" }
                archived += classification.classificationId
            }
        }
    }

    logger.info { "${if (apply) "Archived" else "Would archive"} ${archived.size} of ${pending.size}" }
    if (skipped.isNotEmpty()) {
        logger.warn { "Left un-archived (${skipped.size}):\n  ${skipped.joinToString("\n  ")}" }
    }
}

/**
 * The query lives here rather than in [ClassificationService] deliberately: it exists to repair a state the
 * production paths never read, and keeping it out of the service keeps the pipeline's surface unchanged.
 */
private fun loadUnarchived(db: Database, clientId: UUID?): List<Classification> = db.txnSafe {
    with(ClassificationService.Companion) {
        ClassificationsTable.filesJoin()
            .selectAll()
            .where {
                val unarchived = ClassificationsTable.extractionSessionId.isNotNull() and
                    ClassificationsTable.modelLocation.isNull()
                if (clientId == null) unarchived else unarchived and (FilesTable.clientId eq clientId)
            }
            .map { Classification.fromRow(it) }
            .sortedBy { it.classificationId.toString() }
    }
}
