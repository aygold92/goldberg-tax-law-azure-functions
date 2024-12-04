package com.goldberg.law.function.model.activity

import com.goldberg.law.function.model.InputFileMetadata
import javax.inject.Inject

// note: this only updates the metadata for input files
data class UpdateMetadataActivityInput @Inject constructor(
    val filename: String,
    val metadata: InputFileMetadata
)