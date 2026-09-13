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
 * The model half of a plugin configuration — what to ask and how hard to think about it
 * — as opposed to [ClaudeConnection], which is how to reach the API.
 *
 * Split because they have different lifetimes in practice: a team tunes the model and
 * the effort while building a process, and touches the API key roughly never.
 */
data class ClaudeSettings(
    val model: String = DEFAULT_MODEL,
    val maxTokens: Long = DEFAULT_MAX_TOKENS,
    val effort: ClaudeEffort? = null,
    val thinking: Boolean = true,
    /** Applied to every action on this configuration unless the action overrides it. */
    val systemPrompt: String? = null,
) {
    init {
        require(model.isNotBlank()) { "'model' is required on the Claude plugin configuration" }
        require(maxTokens > 0) { "'maxTokens' must be positive (was $maxTokens)" }
    }

    companion object {
        /** Anthropic's most capable generally available model; the right default. */
        const val DEFAULT_MODEL = "claude-opus-5"

        /**
         * Roomy enough for a real answer, low enough to stay well inside the SDK's HTTP
         * timeout on a non-streaming call. Raising it much beyond this is a reason to
         * stream, which this plugin does not do.
         */
        const val DEFAULT_MAX_TOKENS = 16_000L
    }
}
