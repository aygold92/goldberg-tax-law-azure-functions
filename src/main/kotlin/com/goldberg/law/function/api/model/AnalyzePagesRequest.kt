package com.goldberg.law.function.api.model

import java.util.*

data class AnalyzePagesRequest(
    val pageRequests: Set<UUID>,
)