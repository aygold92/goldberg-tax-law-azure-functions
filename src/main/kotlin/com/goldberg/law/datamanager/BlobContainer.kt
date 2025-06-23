package com.goldberg.law.datamanager

import com.fasterxml.jackson.annotation.JsonProperty

enum class BlobContainer(private val containerName: String) {
    @JsonProperty("input") INPUT("input"),
    @JsonProperty("splitinput") SPLIT_INPUT("splitinput"),
    @JsonProperty("classifications") CLASSIFICATIONS("classifications"),
    @JsonProperty("models") MODELS("models"),
    @JsonProperty("statements") STATEMENTS("statements"),
    @JsonProperty("output") OUTPUT("output");

    override fun toString() = containerName

    fun forClient(clientName: String): String {
        return arrayOf(clientName, containerName).joinToString("-")
    }
}