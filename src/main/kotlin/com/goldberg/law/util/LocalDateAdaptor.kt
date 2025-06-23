package com.goldberg.law.util

import com.azure.ai.documentintelligence.models.DocumentFieldType
import com.nimbusds.jose.shaded.gson.TypeAdapter
import com.nimbusds.jose.shaded.gson.stream.JsonToken
import com.nimbusds.jose.shaded.gson.stream.JsonWriter
import java.io.IOException
import java.time.LocalDate

class LocalDateAdapter : TypeAdapter<LocalDate?>() {
    @Throws(IOException::class)
    override fun write(jsonWriter: JsonWriter, localDate: LocalDate?) {
        if (localDate == null) {
            jsonWriter.nullValue()
        } else {
            jsonWriter.value(localDate.toString())
        }
    }

    @Throws(IOException::class)
    override fun read(jsonReader: com.nimbusds.jose.shaded.gson.stream.JsonReader): LocalDate? {
        if (jsonReader.peek() === JsonToken.NULL) {
            jsonReader.nextNull()
            return null
        } else {
            return LocalDate.parse(jsonReader.nextString())
        }
    }
}

class DocumentFieldTypeAdaptor : TypeAdapter<DocumentFieldType?>() {
    @Throws(IOException::class)
    override fun write(jsonWriter: JsonWriter, documentFieldType: DocumentFieldType?) {
        if (documentFieldType == null) {
            jsonWriter.nullValue()
        } else {
            jsonWriter.value(documentFieldType.value)
        }
    }

    @Throws(IOException::class)
    override fun read(jsonReader: com.nimbusds.jose.shaded.gson.stream.JsonReader): DocumentFieldType? {
        if (jsonReader.peek() === JsonToken.NULL) {
            jsonReader.nextNull()
            return null
        } else {
            return DocumentFieldType.fromString(jsonReader.nextString())
        }
    }
}