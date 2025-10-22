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
        if (value?.boundingRegions != null) {
            provider?.defaultSerializeField("pageNumber", value.boundingRegions[0].pageNumber, jgen)
        }
        
        // Serialize all possible value types
        value?.valueString?.let { provider?.defaultSerializeField("valueString", it, jgen) }
        value?.valueNumber?.let { provider?.defaultSerializeField("valueNumber", it, jgen) }
        value?.valueInteger?.let { provider?.defaultSerializeField("valueInteger", it, jgen) }
        value?.isValueBoolean?.let { provider?.defaultSerializeField("valueBoolean", it, jgen) }
        value?.valueDate?.let { provider?.defaultSerializeField("valueDate", it, jgen) }
        value?.valueTime?.let { provider?.defaultSerializeField("valueTime", it, jgen) }
        value?.valuePhoneNumber?.let { provider?.defaultSerializeField("valuePhoneNumber", it, jgen) }
        value?.valueSelectionMark?.let { provider?.defaultSerializeField("valueSelectionMark", it, jgen) }
        value?.valueSignature?.let { provider?.defaultSerializeField("valueSignature", it, jgen) }
        value?.valueCountryRegion?.let { provider?.defaultSerializeField("valueCountryRegion", it, jgen) }
        value?.valueCurrency?.let { provider?.defaultSerializeField("valueCurrency", it, jgen) }
        value?.valueAddress?.let { provider?.defaultSerializeField("valueAddress", it, jgen) }
        value?.valueList?.let { provider?.defaultSerializeField("valueList", it, jgen) }
        value?.valueMap?.let { provider?.defaultSerializeField("valueMap", it, jgen) }

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