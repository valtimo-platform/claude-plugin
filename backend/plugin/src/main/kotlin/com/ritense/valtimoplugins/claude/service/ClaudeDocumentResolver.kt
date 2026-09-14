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

package com.ritense.valtimoplugins.claude.service

import com.ritense.resource.domain.MetadataType
import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.valtimoplugins.claude.domain.ClaudeDocument
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Base64

/**
 * Turns a Valtimo temporary-resource id into the [ClaudeDocument] attached to a question.
 *
 * A resource id is how files travel between plugin actions in Valtimo: a form upload or
 * the Documenten API plugin's download action puts one in a process variable, which is
 * then passed to this plugin as `pv:resourceId`. The bytes are base64-encoded into the
 * request body, so the whole file goes out as one HTTP request — hence
 * [maxDocumentSizeBytes], which is about the request, not about disk.
 */
open class ClaudeDocumentResolver(
    private val temporaryResourceStorageService: TemporaryResourceStorageService,
    private val maxDocumentSizeBytes: Long,
) {
    /**
     * Reads the resource behind [resource] as a single-element document list, or an empty
     * list when no resource was configured — the common case, since attaching a document
     * is optional.
     */
    open fun resolve(resource: Any?): List<ClaudeDocument> {
        val resourceId = resourceIdOf(resource) ?: return emptyList()

        val content = readContent(resourceId)
        require(content.isNotEmpty()) { "Document '$resourceId' is empty" }
        // readContent reads one byte past the limit, so an oversized file is rejected
        // here instead of being pulled into memory in full and then refused by the API.
        require(content.size <= maxDocumentSizeBytes) {
            "Document '$resourceId' is larger than the maximum of $maxDocumentSizeBytes bytes " +
                "(valtimo.claude.max-document-size)"
        }

        val metadata = readMetadata(resourceId)
        val document =
            ClaudeDocument(
                fileName = metadata[MetadataType.FILE_NAME.key]?.toString()?.takeIf { it.isNotBlank() } ?: resourceId,
                contentType =
                    metadata[MetadataType.CONTENT_TYPE.key]?.toString()?.takeIf { it.isNotBlank() }
                        ?: DEFAULT_CONTENT_TYPE,
                base64Content = Base64.getEncoder().encodeToString(content),
            )

        // Caught here rather than in ClaudeClient so the file name is in the message:
        // "cannot attach offerte.docx" is actionable, "media type not supported" is not.
        require(document.kind != ClaudeDocument.Kind.UNSUPPORTED) {
            "Cannot attach '${document.fileName}': media type '${document.mediaType}' is not supported by Claude. " +
                "Supported: ${ClaudeDocument.SUPPORTED_TYPES.joinToString()}"
        }

        logger.debug {
            "Attaching document '${document.fileName}' (${document.mediaType}, ${content.size} bytes) " +
                "from resource '$resourceId'"
        }
        return listOf(document)
    }

    /**
     * The resource id inside whatever the action property resolved to.
     *
     * Not just a `String?`, because what a Valtimo file upload leaves in a process
     * variable is a *list of file references* — `[{filename, sizeInBytes, id}]` — not a
     * bare id. Requiring the id on its own would mean every process using the stock
     * uploader needs a script task in front of this action to dig it out. A plain id
     * string is still the normal case and still works unchanged.
     */
    private fun resourceIdOf(resource: Any?): String? =
        when (resource) {
            null -> null
            is CharSequence -> resource.toString().trim().takeIf { it.isNotEmpty() }
            // Only one document can be attached; a form that allows several would
            // silently drop the rest, so say so rather than quietly pick the first.
            is Collection<*> -> {
                require(resource.size <= 1) {
                    "Only one document can be attached to a question, but ${resource.size} were uploaded"
                }
                resource.firstOrNull()?.let { resourceIdOf(it) }
            }
            is Map<*, *> -> idIn(resource)
            else ->
                throw IllegalArgumentException(
                    "Cannot read a resource id from a ${resource::class.simpleName}: '$resource'",
                )
        }

    /**
     * The id inside one file reference.
     *
     * Both stock uploaders nest theirs one level deep — `{data: {resourceId: ...}}` — while
     * the Documenten API uploader puts `id` at the top, so both levels are searched.
     *
     * A reference with no id in either place throws rather than resolving to "no document":
     * the process asked for a file to be attached, and answering a question about a document
     * that was never sent is worse than failing the step.
     */
    private fun idIn(reference: Map<*, *>): String {
        val nested = reference[NESTED_KEY] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        return ID_KEYS.firstNotNullOfOrNull { key -> (reference[key] ?: nested[key])?.let { resourceIdOf(it) } }
            ?: throw IllegalArgumentException(
                "Cannot read a resource id from the file reference ${reference.keys}: expected one of " +
                    "${ID_KEYS.joinToString()}, at the top level or under '$NESTED_KEY'",
            )
    }

    private fun readContent(resourceId: String): ByteArray =
        try {
            temporaryResourceStorageService.getResourceContentAsInputStream(resourceId).use {
                it.readNBytes(readLimit())
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not read document for resource id '$resourceId'", e)
        }

    private fun readMetadata(resourceId: String): Map<String, Any> =
        try {
            temporaryResourceStorageService.getResourceMetadata(resourceId)
        } catch (e: Exception) {
            // Metadata only supplies the label and the media type; without it the
            // document still has bytes, and the media-type check below reports the miss.
            logger.warn(e) { "Could not read metadata for resource id '$resourceId'" }
            emptyMap()
        }

    /** One byte past the maximum, so an oversized document is detectable. */
    private fun readLimit(): Int = (maxDocumentSizeBytes + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

        /**
         * The keys a file reference carries its id under. `id` is what the Documenten API
         * uploader emits; `resourceId` is what the plain Valtimo uploader emits.
         */
        private val ID_KEYS = listOf("id", "resourceId")

        /**
         * Where the stock form.io upload services put their id: not at the top of the file
         * reference but one level down, next to the file name and size.
         */
        private const val NESTED_KEY = "data"
    }
}
