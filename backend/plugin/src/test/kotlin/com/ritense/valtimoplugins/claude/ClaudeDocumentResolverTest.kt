/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.valtimoplugins.claude

import com.ritense.resource.domain.MetadataType
import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.valtimoplugins.claude.domain.ClaudeDocument
import com.ritense.valtimoplugins.claude.service.ClaudeDocumentResolver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ClaudeDocumentResolverTest : BaseTest() {
    private val storageService: TemporaryResourceStorageService = mock()
    private val resolver = ClaudeDocumentResolver(storageService, MAX_SIZE)

    @Test
    fun `should read a resource into a document`() {
        stub("resource-id", "%PDF-1.4 content", "invoice.pdf", ClaudeDocument.PDF)

        val documents = resolver.resolve("resource-id")

        assertEquals(1, documents.size)
        assertEquals("invoice.pdf", documents[0].fileName)
        assertEquals(ClaudeDocument.PDF, documents[0].mediaType)
        assertEquals("%PDF-1.4 content", decode(documents[0]))
    }

    @Test
    fun `should return nothing when no resource is configured`() {
        assertTrue(resolver.resolve(null).isEmpty())
        assertTrue(resolver.resolve("  ").isEmpty())
        assertTrue(resolver.resolve(emptyList<Any>()).isEmpty())
    }

    @Test
    fun `should read the id out of what a valtimo file upload leaves behind`() {
        stub("resource-id", "%PDF-1.4 content", "invoice.pdf", ClaudeDocument.PDF)

        // The Documenten API uploader's shape, and the plain Valtimo uploader's.
        val documentenApi = listOf(mapOf("filename" to "invoice.pdf", "sizeInBytes" to 12, "id" to "resource-id"))
        val valtimo = listOf(mapOf("resourceId" to "resource-id"))

        assertEquals("invoice.pdf", resolver.resolve(documentenApi)[0].fileName)
        assertEquals("invoice.pdf", resolver.resolve(valtimo)[0].fileName)
    }

    @Test
    fun `should refuse to silently drop extra uploads`() {
        val two = listOf(mapOf("id" to "first"), mapOf("id" to "second"))

        val exception = assertThrows<IllegalArgumentException> { resolver.resolve(two) }

        assertTrue(exception.message!!.contains("2 were uploaded"))
    }

    @Test
    fun `should report a resource it cannot read an id from`() {
        val exception = assertThrows<IllegalArgumentException> { resolver.resolve(42) }

        assertTrue(exception.message!!.contains("42"))
    }

    @Test
    fun `should fall back to the resource id when metadata has no file name`() {
        stub("resource-id", "text", fileName = null, contentType = "text/plain")

        assertEquals("resource-id", resolver.resolve("resource-id")[0].fileName)
    }

    @Test
    fun `should reject a media type Claude cannot read`() {
        stub("resource-id", "binary", "offer.docx", "application/vnd.ms-word")

        val exception = assertThrows<IllegalArgumentException> { resolver.resolve("resource-id") }

        assertTrue(exception.message!!.contains("offer.docx"))
    }

    @Test
    fun `should reject an empty document`() {
        stub("resource-id", "", "empty.pdf", ClaudeDocument.PDF)

        assertThrows<IllegalArgumentException> { resolver.resolve("resource-id") }
    }

    @Test
    fun `should reject a document over the maximum size`() {
        stub("resource-id", "x".repeat((MAX_SIZE + 1).toInt()), "big.pdf", ClaudeDocument.PDF)

        val exception = assertThrows<IllegalArgumentException> { resolver.resolve("resource-id") }

        assertTrue(exception.message!!.contains("larger than the maximum"))
    }

    @Test
    fun `should report an unreadable resource`() {
        whenever(storageService.getResourceContentAsInputStream(any())).thenThrow(RuntimeException("gone"))

        val exception = assertThrows<IllegalArgumentException> { resolver.resolve("resource-id") }

        assertTrue(exception.message!!.contains("resource-id"))
    }

    private fun stub(
        resourceId: String,
        content: String,
        fileName: String?,
        contentType: String,
    ) {
        // A fresh stream per call: `thenReturn` would hand every call the same instance,
        // and the second read of an already-consumed stream looks like an empty document.
        whenever(storageService.getResourceContentAsInputStream(resourceId))
            .thenAnswer { ByteArrayInputStream(content.toByteArray(UTF_8)) }
        val metadata = mutableMapOf<String, Any>(MetadataType.CONTENT_TYPE.key to contentType)
        fileName?.let { metadata[MetadataType.FILE_NAME.key] = it }
        whenever(storageService.getResourceMetadata(resourceId)).thenReturn(metadata)
    }

    private fun decode(document: ClaudeDocument) = String(Base64.getDecoder().decode(document.base64Content), UTF_8)

    private companion object {
        const val MAX_SIZE = 1024L
    }
}
