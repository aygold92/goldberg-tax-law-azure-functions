package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.DataManager
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
    private val dataManager: DataManager
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
        val statementModels = input.documentDataModels.mapNotNull { it.statementDataModel }
        val checkModels = input.documentDataModels.mapNotNull { it.checkDataModel }
        val (statementModelsUpdatedAccounts, checksUpdatedAccounts) = accountNormalizer.normalizeAccounts(statementModels, checkModels)

        val statementsWithoutChecks = statementCreator.createBankStatements(statementModelsUpdatedAccounts)
            .sortedBy { it.statementDate }
            .onEach { if (it.isSuspicious()) logger.error { "Statement ${it.primaryKey} is suspicious" } }
        logger.debug { "=====Statements=====\n${statementsWithoutChecks.toStringDetailed()}" }

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(statementsWithoutChecks, checksUpdatedAccounts)

        val fileNames = finalStatements.mapAsync { dataManager.saveBankStatement(it) }.toSet()

        val inputFileToStatement: MutableMap<String, MutableSet<String>> = mutableMapOf()
        finalStatements.forEach { statement ->
            statement.pages.associate { it.filename to statement.azureFileName() }.onEach {
                inputFileToStatement[it.key] = inputFileToStatement.getOrDefault(it.key, mutableSetOf()).apply { add(it.value) }
            }
        }
        inputFileToStatement.mapAsync { filename, azureFileNames ->
            dataManager.updateInputPdfMetadata(filename, input.metadataMap[filename]!!.copy(statements = azureFileNames))
        }

        return ProcessStatementsActivityOutput(fileNames)
    }

    companion object {
        const val FUNCTION_NAME = "ProcessStatementsActivity"
    }
}