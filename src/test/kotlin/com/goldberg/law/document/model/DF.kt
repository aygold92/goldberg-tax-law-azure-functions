package com.goldberg.law.document.model

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.azure.ai.documentintelligence.models.BoundingRegion
import com.azure.ai.documentintelligence.models.DocumentField
import com.azure.ai.documentintelligence.models.DocumentFieldType

data class BR(val pageNumber: Int) {
    fun toBoundingRegion(): BoundingRegion {
        val ctor = BoundingRegion::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, List::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(pageNumber, emptyList<Double>()) as BoundingRegion
    }
}

data class DF(
    val type: DocumentFieldType,
    val valueString: String? = null,
    val valueInteger: Long? = null,
    val valueNumber: Number? = null,
    val valueBoolean: Boolean? = null,
    val content: String? = null,
    val valueList: List<DocumentField>? = null,
    val valueMap: Map<String, DocumentField>? = null,
    val boundingRegions: List<BR>? = null,
) {
    fun create(): DocumentField {
        val ctor = DocumentField::class.java.getDeclaredConstructor(DocumentFieldType::class.java)
        ctor.isAccessible = true
        val df = ctor.newInstance(type) as DocumentField

        fun setField(name: String, value: Any?) {
            if (value == null) return
            val f = DocumentField::class.java.getDeclaredField(name)
            f.isAccessible = true
            f.set(df, value)
        }

        setField("content", content)
        setField("valueString", valueString)
        setField("valueInteger", valueInteger)
        setField("valueNumber", valueNumber?.toDouble())
        setField("valueBoolean", valueBoolean)
        setField("valueList", valueList)
        setField("valueMap", valueMap)
        setField("boundingRegions", boundingRegions?.map { it.toBoundingRegion() })

        return df
    }

    companion object {
        fun of(value: String, content: String? = value, page: Int? = null): DocumentField = DF(
            type = DocumentFieldType.STRING,
            valueString = value,
            content = content,
            boundingRegions = page?.let { listOf(BR(it)) }
        ).create()

        fun of(value: Long, content: String? = value.toString(), page: Int? = null): DocumentField = DF(
            type = DocumentFieldType.INTEGER,
            valueInteger = value,
            content = content,
            boundingRegions = page?.let { listOf(BR(it)) }
        ).create()

        fun of(value: Double, content: String? = value.toString(), page: Int? = null): DocumentField = DF(
            type = DocumentFieldType.NUMBER,
            valueNumber = value,
            content = content,
            boundingRegions = page?.let { listOf(BR(it)) }
        ).create()

        fun of(value: Boolean, content: String? = value.toString(), page: Int? = null): DocumentField = DF(
            type = DocumentFieldType.BOOLEAN,
            valueBoolean = value,
            content = content,
            boundingRegions = page?.let { listOf(BR(it)) }
        ).create()

        fun of(vararg value: DocumentField): DocumentField = DF(
            type = DocumentFieldType.ARRAY,
            valueList = value.toList()
        ).create()

        fun of(vararg value: Pair<String, DocumentField>): DocumentField = DF(
            type = DocumentFieldType.OBJECT,
            valueMap = value.toMap()
        ).create()
    }
}

data class AD(
    val fields: Map<String, DocumentField>,
    val documentType: String? = "test",
) {
    fun create(): AnalyzedDocument {
        val ctor = AnalyzedDocument::class.java.getDeclaredConstructor(
            String::class.java, List::class.java, Double::class.javaPrimitiveType
        )
        ctor.isAccessible = true
        val doc = ctor.newInstance(documentType, emptyList<Any>(), 1.0) as AnalyzedDocument

        AnalyzedDocument::class.java.getDeclaredField("fields").apply {
            isAccessible = true
            set(doc, fields.toMutableMap())
        }

        return doc
    }
}