package com.goldberg.law.function.model

enum class OrchestrationStage(private val action: String) {
    VERIFYING_DOCUMENTS("Verifying Documents"),
    EXTRACTING_DATA("Extracting Data");

    override fun toString(): String {
        return action
    }
}