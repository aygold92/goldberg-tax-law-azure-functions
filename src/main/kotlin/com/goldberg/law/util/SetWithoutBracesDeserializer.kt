package com.goldberg.law.util
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

class SetWithoutBracesDeserializer : JsonDeserializer<Set<String>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Set<String> {
        val rawValue = p.text // Get the raw string value from the JSON field
        return rawValue.split(",").map { it.trim() }.toSet() // Split by commas, trim whitespace, and convert to a Set
    }
}