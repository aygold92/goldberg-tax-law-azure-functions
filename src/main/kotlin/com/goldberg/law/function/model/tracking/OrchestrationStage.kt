package com.goldberg.law.function.model.tracking

enum class OrchestrationStage(private val action: String) {
    VERIFYING_DOCUMENTS("Verifying Documents"),
    CLASSIFYING_DOCUMENTS("Classifying Documents"),
    EXTRACTING_DATA("Extracting Data"),
    CREATING_BANK_STATEMENTS("Creating Bank Statements");

    override fun toString(): String {
        return action
    }
}