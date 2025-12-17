package com.goldberg.law.function.activity.model

import com.goldberg.law.function.model.ExtractedDocumentIds
import java.util.*

data class MatchChecksToStatementsActivityInput(
    val requestId: String,
    val clientId: UUID,
    val documents: Map<UUID, ExtractedDocumentIds>,
)