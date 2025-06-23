package com.goldberg.law.util

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.azure.ai.documentintelligence.models.BoundingRegion
import com.azure.ai.documentintelligence.models.DocumentField
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.io.IOException

/**
 * removes extra unneeded output from Azure DocumentField
 */
class DocumentFieldSerializer @JvmOverloads constructor(t: Class<DocumentField?>? = null) :
    StdSerializer<DocumentField?>(t) {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun serialize(
        value: DocumentField?, jgen: JsonGenerator, provider: SerializerProvider?
    ) {
        jgen.writeStartObject()
        provider?.defaultSerializeField("type", value?.type.toString(), jgen)
        provider?.defaultSerializeField("content", value?.content, jgen)
        provider?.defaultSerializeField("boundingRegions", value?.boundingRegions, jgen)
//        if (value?.valueNumber)
//        when(val workingValue = value?.value) {
//            is Map<*,*> -> {
//                jgen.writeFieldName("value")
//                jgen.writeStartObject()
//                workingValue.keys.forEach {
//                    jgen.writeFieldName(it.toString())
//                    serialize(workingValue[it]?.asDocumentField(), jgen, provider)
//                }
//                jgen.writeEndObject()
//            }
//            is Collection<*> -> {
//                jgen.writeArrayFieldStart("value")
//                workingValue.forEach {
//                    serialize(it?.asDocumentField(), jgen, provider)
//                }
//                jgen.writeEndArray()
//            }
//            is Double -> jgen.writeNumberField("value", value.valueAsDouble)
//            else -> provider?.defaultSerializeField("value", value?.value, jgen)
//        }
        jgen.writeEndObject()
    }
}

/**
 * removes extra unneeded output from Azure AnalyzedDocument
 */
class AnalyzedDocumentSerializer @JvmOverloads constructor(t: Class<AnalyzedDocument?>? = null) :
    StdSerializer<AnalyzedDocument?>(t) {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun serialize(
        value: AnalyzedDocument?, jgen: JsonGenerator, provider: SerializerProvider?
    ) {
        jgen.writeStartObject()
        provider?.defaultSerializeField("fields", value?.fields, jgen)
        provider?.defaultSerializeField("docType", value?.documentType, jgen)
        jgen.writeEndObject()
    }
}

class BoundingRegionSerializer @JvmOverloads constructor(t: Class<BoundingRegion?>? = null) :
    StdSerializer<BoundingRegion?>(t) {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun serialize(
        value: BoundingRegion?, jgen: JsonGenerator, provider: SerializerProvider?
    ) {
        jgen.writeStartObject()
        provider?.defaultSerializeField("pageNumber", value?.pageNumber, jgen)
        jgen.writeEndObject()
    }
}