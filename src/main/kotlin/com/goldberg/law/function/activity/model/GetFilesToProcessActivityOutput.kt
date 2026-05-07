package com.goldberg.law.function.activity.model

import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.ClassifiedItem
import com.goldberg.law.entity.InputFile

data class GetFilesToProcessActivityOutput(
    val filesToClassify: Set<InputFile>,
    val classificationsToProcess: Set<Classification>,
    val itemsCompleted: Set<ClassifiedItem>,
)