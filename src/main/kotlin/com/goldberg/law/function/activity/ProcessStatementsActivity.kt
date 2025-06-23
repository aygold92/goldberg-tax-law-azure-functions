package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.AccountNormalizer
import com.goldberg.law.document.CheckToStatementMatcher
import com.goldberg.law.document.DocumentStatementCreator
import com.goldberg.law.function.model.activity.ProcessStatementsActivityInput
import com.goldberg.law.function.model.activity.ProcessStatementsActivityOutput
import com.goldberg.law.util.mapAsync
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject

class ProcessStatementsActivity @Inject constructor(
    private val statementCreator: DocumentStatementCreator,
    private val accountNormalizer: AccountNormalizer,
    private val checkToStatementMatcher: CheckToStatementMatcher,
    private val dataManager: AzureStorageDataManager
) {
    private val logger = KotlinLogging.logger {}

    /**
     * This is the activity function that is invoked by the orchestrator function.
     */
    @FunctionName(FUNCTION_NAME)
    fun processStatementsAndChecks(
        @DurableActivityTrigger(name = "input") input: ProcessStatementsActivityInput,
        context: ExecutionContext
    ): ProcessStatementsActivityOutput {
        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.documentDataModels.size} models" }
        val checkModels = input.documentDataModels.mapNotNull { it.checkDataModel }.flatMap { it.extractNestedChecks() }

        val statementsWithoutChecks = input.documentDataModels.mapNotNull { it.statementDataModel?.toBankStatement() }.flatten()
            .sortedBy { it.statementDate }
            .onEach { if (it.isSuspicious()) logger.error { "Statement ${it.primaryKey} is suspicious" } }

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(statementsWithoutChecks, checkModels)

        logger.debug { "=====Statements=====\n${finalStatements.toStringDetailed()}" }

        finalStatements.mapAsync {
            try {
                dataManager.saveBankStatement(input.clientName, it)
            } catch (e: Exception) {
                logger.error { "Unable to save statement $it: $e" }
            }
        }

        // match filenames to statements
        val inputFileToStatement: MutableMap<String, MutableSet<String>> = mutableMapOf()
        finalStatements.forEach { statement ->
            statement.pageMetadata.pages.associate { statement.pageMetadata.filename to statement.azureFileName() }.onEach {
                inputFileToStatement[it.key] = inputFileToStatement.getOrDefault(it.key, mutableSetOf()).apply { add(it.value) }
            }
        }
        inputFileToStatement.mapAsync { filename, azureFileNames ->
            val statements = (if (input.keepOriginalMetadata) input.metadataMap[filename]?.statements ?: setOf() else setOf())
                .union(azureFileNames)
            dataManager.updateInputPdfMetadata(input.clientName, filename, input.metadataMap[filename]!!.copy(statements = statements))
        }

        return ProcessStatementsActivityOutput(inputFileToStatement)
    }

    companion object {
        const val FUNCTION_NAME = "ProcessStatementsActivity"
    }
}