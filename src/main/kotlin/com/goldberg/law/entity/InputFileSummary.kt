package com.goldberg.law.entity

data class InputFileSummary(
    val inputFile: InputFile,
    val numChecks: Int?,
    val numStatements: Int?,
    val numTransactions: Int?,
    val numAnalyzed: Int?,
    val numDocuments: Int?,
): IInputFile by inputFile, IClient by inputFile.client