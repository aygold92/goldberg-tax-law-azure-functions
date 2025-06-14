package com.goldberg.law.categorization.chatgbt

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class ChatGBTClientRequest @JsonCreator constructor(
    @JsonProperty("systemMessage") val systemMessage: String,
    @JsonProperty("userMessage") val userMessage: String,
    @JsonProperty("temperature") val temperature: Double
)