package com.goldberg.law.function.activity.model

import com.goldberg.law.function.model.ExtractedDocumentIds
import java.util.*

data class ProcessDataModelActivityOutput(
    val fileId: UUID,
    val extractedDocumentIds: ExtractedDocumentIds,
)