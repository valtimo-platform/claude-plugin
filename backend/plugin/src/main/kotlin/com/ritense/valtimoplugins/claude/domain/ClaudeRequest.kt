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
 * One call to the Messages API, in the plugin's own vocabulary.
 *
 * Deliberately a *list* of [messages] rather than a single prompt string, even though
 * the `ask-claude` action only ever builds one. The Messages API is multi-turn, and a
 * later conversation or tool-use loop appends assistant and tool-result turns to this
 * same list — keeping the shape here means such a loop is an addition to
 * `ClaudeClient`, not a rewrite of everything that calls it.
 */
data class ClaudeRequest(
    val model: String,
    val maxTokens: Long,
    val messages: List<ClaudeMessage>,
    val systemPrompt: String? = null,
    val effort: ClaudeEffort? = null,
    val thinking: Boolean = true,
) {
    init {
        require(model.isNotBlank()) { "'model' is required" }
        require(maxTokens > 0) { "'maxTokens' must be positive (was $maxTokens)" }
        require(messages.isNotEmpty()) { "a request needs at least one message" }
        require(messages.first().role == ClaudeRole.USER) {
            "the first message must be a user message (was ${messages.first().role})"
        }
        // Claude Opus 5 rejects thinking: disabled above effort `high`; failing here
        // gives the configurator the reason instead of an opaque HTTP 400 at runtime.
        require(thinking || effort == null || effort in ClaudeEffort.THINKING_OPTIONAL) {
            "thinking cannot be switched off at effort '${effort?.value}'; use 'low', 'medium' or 'high'"
        }
    }
}

/** A single turn. [documents] ride along with the text as extra content blocks. */
data class ClaudeMessage(
    val role: ClaudeRole,
    val text: String,
    val documents: List<ClaudeDocument> = emptyList(),
)

enum class ClaudeRole { USER, ASSISTANT }

/**
 * How much Claude may spend on a request. Maps onto `output_config.effort`; leaving it
 * unset is the API's own default (`high`).
 */
enum class ClaudeEffort(
    val value: String,
) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max"),
    ;

    companion object {
        /** The efforts at which thinking may be switched off. */
        val THINKING_OPTIONAL = setOf(LOW, MEDIUM, HIGH)

        /** Parses a configured effort, case-insensitively; blank means "unset". */
        fun ofOrNull(value: String?): ClaudeEffort? {
            val trimmed = value?.trim()?.lowercase()
            if (trimmed.isNullOrEmpty()) return null
            return entries.firstOrNull { it.value == trimmed }
                ?: throw IllegalArgumentException(
                    "Unknown effort '$value'. Allowed: ${entries.joinToString { it.value }}",
                )
        }
    }
}
