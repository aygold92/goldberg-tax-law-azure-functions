package com.goldberg.law.util
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

/**
 * The statements field of InputFileMetadata is a Set of statement keys, which normally requires the value
 * to be stored like ["stmt1", "stmt2"]... but in the Azure Storage Tags field you can't store braces "[]" so we
 * need to deserialize it from the metadata and tags fields without the braces
 */
class SetWithoutBracesDeserializer : JsonDeserializer<Set<String>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Set<String> {
        val rawValue = p.text // Get the raw string value from the JSON field
        return rawValue.split(",").map { it.trim() }.toSet() // Split by commas, trim whitespace, and convert to a Set
    }
}