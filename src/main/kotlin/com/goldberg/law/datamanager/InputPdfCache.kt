package com.goldberg.law.datamanager

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalNotification
import com.google.inject.Singleton
import org.apache.pdfbox.pdmodel.PDDocument
import java.time.Duration
import java.util.UUID

@Singleton
class InputPdfCache {
    private val cache: Cache<UUID, PDDocument> = CacheBuilder.newBuilder()
        .maximumSize(10)
        .expireAfterAccess(Duration.ofMinutes(15))
        .removalListener { n: RemovalNotification<UUID, PDDocument> -> n.value?.close() }
        .build()

    fun getOrLoad(fileId: UUID, loader: () -> PDDocument): PDDocument = cache.get(fileId) { loader() }
    fun invalidate(fileId: UUID) = cache.invalidate(fileId)
}
