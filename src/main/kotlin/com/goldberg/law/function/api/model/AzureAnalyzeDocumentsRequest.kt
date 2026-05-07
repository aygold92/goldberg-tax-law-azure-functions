package com.goldberg.law.function.api.model

import java.util.*

data class AzureAnalyzeDocumentsRequest(
    val clientId: UUID,
    val fileIds: Set<UUID> = emptySet(),
    val classificationIds: Set<UUID> = emptySet(),
    val processingOptions: ClassificationProcessingOptions = ClassificationProcessingOptions(),
)