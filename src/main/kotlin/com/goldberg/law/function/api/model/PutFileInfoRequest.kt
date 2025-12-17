package com.goldberg.law.function.api.model

import com.goldberg.law.datamanager.StorageLocation
import java.util.*

data class PutFileInfoRequest(
    val filename: String,
    val storageLocation: StorageLocation,
    val clientId: UUID,
    val requestToken: UUID
)

