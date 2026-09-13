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

package com.ritense.valtimoplugins.claude.domain

/**
 * A file sent along with a question.
 *
 * [base64Content] rather than the raw bytes: base64 is the form both the PDF and the
 * image content block take on the wire, so the encoding happens once — in
 * `ClaudeDocumentResolver`, where the bytes are read — instead of on every retry of
 * the same request. Plain-text documents are decoded again in `ClaudeClient`, which is
 * cheap and keeps this a value type.
 */
data class ClaudeDocument(
    val fileName: String,
    val contentType: String,
    val base64Content: String,
) {
    /** The media type without any `; charset=...` parameter, lowercased. */
    val mediaType: String get() = contentType.substringBefore(';').trim().lowercase()

    val kind: Kind
        get() =
            when {
                mediaType == PDF -> Kind.PDF
                mediaType in IMAGE_TYPES -> Kind.IMAGE
                mediaType.startsWith("text/") || mediaType in TEXT_TYPES -> Kind.TEXT
                else -> Kind.UNSUPPORTED
            }

    /** Which kind of content block this document becomes. */
    enum class Kind { PDF, IMAGE, TEXT, UNSUPPORTED }

    companion object {
        const val PDF = "application/pdf"

        /** The base64 image media types the Messages API accepts. */
        val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

        // Text-ish types outside the `text/` family that are still sent as a plain-text
        // document. (A line comment, not KDoc: Kotlin nests block comments, so a `/*`
        // inside one — which any `text/` wildcard writes — swallows the closing `*/`.)
        val TEXT_TYPES = setOf("application/json", "application/xml", "application/x-yaml")

        /** Every media type that can be attached, for error messages. */
        val SUPPORTED_TYPES = (setOf(PDF) + IMAGE_TYPES + TEXT_TYPES + "text/*").sorted()
    }
}
