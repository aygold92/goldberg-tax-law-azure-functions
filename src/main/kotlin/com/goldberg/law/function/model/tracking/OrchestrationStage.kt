package com.goldberg.law.function.model.tracking

enum class OrchestrationStage(private val action: String) {
    VERIFYING_DOCUMENTS("Verifying Documents"),
    CLASSIFYING_DOCUMENTS("Classifying Documents"),
    EXTRACTING_DATA("Extracting Data"),
    MATCHING_CHECKS("Matching Checks");

    override fun toString(): String {
        return action
    }
}