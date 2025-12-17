package com.goldberg.law.function.api.model

import com.goldberg.law.entity.IClassification

data class PutDocumentClassificationResponse(
    val classificationData: List<IClassification>
)