package com.goldberg.law.datamanager

import com.goldberg.law.util.OBJECT_MAPPER

data class StorageLocation(
    val containerName: String,
    val filePath: String,
    val extension: Extension
) {
    fun filePathWithExt() = "$filePath.$extension"
    fun serialize(): String = OBJECT_MAPPER.writeValueAsString(this)
    override fun toString() = "$containerName/$filePath.$extension"

    companion object {
        fun deserialize(str: String?): StorageLocation? = if (str == null) null else OBJECT_MAPPER.readValue(str, StorageLocation::class.java)
        fun fromUrl(url: String): StorageLocation = url.substringAfter("https://egblobstore.blob.core.windows.net/")
            .split("/", limit = 2).let { (containerName, filePath) ->
                StorageLocation(containerName, filePath, Extension.valueOf((filePath.split(".")[1])))
            }
    }
}

enum class Extension(val extension: String) {
    PDF("pdf"),
    JSON("json");

    override fun toString() = extension
}