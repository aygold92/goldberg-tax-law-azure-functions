package com.goldberg.law.util

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentFieldType
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.goldberg.law.document.model.input.tables.asDocumentField
import java.io.IOException


class DocumentFieldSerializer @JvmOverloads constructor(t: Class<DocumentField?>? = null) :
    StdSerializer<DocumentField?>(t) {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun serialize(
        value: DocumentField?, jgen: JsonGenerator, provider: SerializerProvider?
    ) {
        jgen.writeStartObject()
        provider?.defaultSerializeField("type", value?.type.toString(), jgen)
        provider?.defaultSerializeField("content", value?.content, jgen)
        when(val workingValue = value?.value) {
            is Map<*,*> -> {
                jgen.writeFieldName("value")
                jgen.writeStartObject()
                workingValue.keys.forEach {
                    jgen.writeFieldName(it.toString())
                    serialize(workingValue[it]?.asDocumentField(), jgen, provider)
                }
                jgen.writeEndObject()
            }
            is Collection<*> -> {
                jgen.writeArrayFieldStart("value")
                workingValue.forEach {
                    serialize(it?.asDocumentField(), jgen, provider)
                }
                jgen.writeEndArray()
            }
            else -> provider?.defaultSerializeField("value", value?.value, jgen)
        }
        jgen.writeEndObject()
    }
}

class AnalyzedDocumentSerializer @JvmOverloads constructor(t: Class<AnalyzedDocument?>? = null) :
    StdSerializer<AnalyzedDocument?>(t) {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun serialize(
        value: AnalyzedDocument?, jgen: JsonGenerator, provider: SerializerProvider?
    ) {
        jgen.writeStartObject()
        provider?.defaultSerializeField("fields", value?.fields, jgen)
        provider?.defaultSerializeField("docType", value?.docType, jgen)
        jgen.writeEndObject()
    }
}