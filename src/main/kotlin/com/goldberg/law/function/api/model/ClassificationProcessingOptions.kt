package com.goldberg.law.function.api.model

data class ClassificationProcessingOptions(
    val forceReanalysis: Boolean = false,
    val forceRecreate: Boolean = false,
    val replaceOnRecreate: Boolean = false,
)
