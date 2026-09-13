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
 * What came back: the answer text plus the bits of the response a process might
 * reasonably branch on or account for.
 */
data class ClaudeAnswer(
    /** All text blocks of the reply, joined. Empty when Claude produced no text. */
    val text: String,
    /** The model that actually answered, as reported by the API. */
    val model: String,
    /** `end_turn`, `max_tokens`, `refusal`, … — null when the API did not report one. */
    val stopReason: String?,
    val inputTokens: Long,
    val outputTokens: Long,
    /** Set only on a refusal: the safety category and the API's explanation. */
    val refusalCategory: String? = null,
    val refusalExplanation: String? = null,
) {
    val isRefusal: Boolean get() = stopReason == REFUSAL

    /** True when the reply was cut off by `maxTokens` and is therefore incomplete. */
    val isTruncated: Boolean get() = stopReason == MAX_TOKENS

    companion object {
        const val REFUSAL = "refusal"
        const val MAX_TOKENS = "max_tokens"
    }
}
