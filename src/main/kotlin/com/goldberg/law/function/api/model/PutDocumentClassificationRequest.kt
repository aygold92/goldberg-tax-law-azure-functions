package com.goldberg.law.function.api.model

import com.goldberg.law.entity.ClassifiedFile
import java.util.*

data class PutDocumentClassificationRequest(
    val file: ClassifiedFile,
    val classificationsToRemove: List<UUID>,
)