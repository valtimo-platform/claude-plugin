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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.valtimoplugins.claude.domain.ClaudeResultMapping
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Picks values out of a JSON answer and keys them by the value-resolver expression they
 * should be written to (`pv:urgency`, `doc:/zaak/urgentie`), which is the shape
 * `ValueResolverService.handleValues` takes.
 *
 * Nothing here throws. A model can always answer in a shape the mapping did not expect,
 * and that must not turn a paid-for, otherwise perfectly good answer into a BPMN
 * incident. Problems are collected in [Result.error] instead, which the caller exposes
 * as the `claudeMappingError` process variable so a process can branch on it.
 */
open class ClaudeResultMapper(
    private val objectMapper: ObjectMapper,
) {
    /** [values] is keyed by the full target expression, e.g. `doc:/zaak/urgentie`. */
    data class Result(
        val values: Map<String, Any> = emptyMap(),
        val error: String? = null,
    ) {
        val isEmpty: Boolean get() = values.isEmpty()
    }

    open fun map(
        mappings: List<ClaudeResultMapping>?,
        answer: String?,
    ): Result {
        val configured = mappings?.filter { !it.source.isNullOrBlank() && !it.target.isNullOrBlank() }
        if (configured.isNullOrEmpty()) return Result()

        val json =
            parseJson(answer)
                ?: return Result(error = "The answer is not JSON, so no values could be mapped")

        val values = mutableMapOf<String, Any>()
        val errors = mutableListOf<String>()

        configured.forEach { mapping ->
            val source = mapping.source!!.trim()
            val target = mapping.target!!.trim()
            val node = json.at(pointerOf(source))
            when {
                node.isMissingNode || node.isNull -> errors += "'$source' is not in the answer"
                !target.contains(':') ->
                    errors +=
                        "target '$target' is not a value resolver expression (use pv: or doc:)"
                else -> values[target] = valueOf(node)
            }
        }

        return Result(values = values, error = errors.joinToString("; ").ifBlank { null })
    }

    /**
     * Parses the answer as JSON. Models like to wrap JSON in a ```json fence even when
     * asked not to, so that is stripped first. Returns `null` for anything that is not a
     * JSON object or array — a plain sentence has nothing to map from.
     */
    private fun parseJson(answer: String?): JsonNode? {
        val trimmed = answer?.trim()
        if (trimmed.isNullOrEmpty()) return null
        val body =
            if (trimmed.startsWith(FENCE)) {
                trimmed
                    .removePrefix("$FENCE$JSON_FENCE_LANGUAGE")
                    .removePrefix(FENCE)
                    .removeSuffix(FENCE)
                    .trim()
            } else {
                trimmed
            }
        return try {
            objectMapper.readTree(body)?.takeIf { it.isContainerNode }
        } catch (e: Exception) {
            logger.debug(e) { "The answer is not JSON" }
            null
        }
    }

    /** Accepts both `/urgency` and `urgency` as a pointer into the answer. */
    private fun pointerOf(source: String): String = if (source.startsWith("/")) source else "/$source"

    /**
     * Scalars keep their JSON type, so a number lands in the case document as a number.
     * Objects and arrays are written as their JSON text — predictable, and enough for
     * the values a process actually branches on.
     */
    private fun valueOf(node: JsonNode): Any =
        when {
            node.isTextual -> node.asText()
            node.isNumber -> node.numberValue()
            node.isBoolean -> node.asBoolean()
            else -> objectMapper.writeValueAsString(node)
        }

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val FENCE = "```"
        private const val JSON_FENCE_LANGUAGE = "json"
    }
}
