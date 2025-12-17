package com.goldberg.law.function.api.model

import com.goldberg.law.function.model.DocumentDataModelContainer
import java.util.*

class PutDocumentDataModelRequest(
    val classificationId: UUID,
    val model: DocumentDataModelContainer
)