package com.goldberg.law.function.api.model

import java.util.*

data class PutFileInfoRequest(
    val filename: String,
    val clientId: UUID,
    val requestToken: UUID
)

