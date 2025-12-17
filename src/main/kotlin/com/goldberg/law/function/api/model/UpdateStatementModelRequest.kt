package com.goldberg.law.function.api.model

import com.goldberg.law.entity.ClassifiedPages
import com.goldberg.law.entity.StatementDetails
import com.goldberg.law.entity.TransactionDetails
import java.util.*

data class UpdateStatementModelRequest(
    val classificationId: UUID,
    val classification: ClassifiedPages,
    val statementDetails: StatementDetails,
    val upserts: List<TransactionDetails>,
    val deletes: List<UUID>
)
