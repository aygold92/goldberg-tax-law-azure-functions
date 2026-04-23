package com.goldberg.law.function.api.model

import java.util.UUID

data class NewClientResponse(
    val clientId: UUID,
    val clientName: String,
)
