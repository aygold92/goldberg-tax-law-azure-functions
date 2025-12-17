package com.goldberg.law.entity

data class InputFileSummary(
    val inputFile: InputFile,
    val numChecks: Int? = null,
    val numStatements: Int? = null,
    val numTransactions: Int? = null,
    val numAnalyzed: Int? = null,
    val statementMetadata: Map<String, StatementSummary>? = null
): IInputFile by inputFile