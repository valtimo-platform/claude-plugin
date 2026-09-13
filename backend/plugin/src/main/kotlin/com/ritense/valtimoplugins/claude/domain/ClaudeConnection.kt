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

import java.time.Duration

/**
 * Everything needed to reach the API, taken from one plugin configuration.
 *
 * Separate from [ClaudeRequest] because it changes on a different clock: the request
 * is rebuilt per process step, while the connection is stable for the life of a plugin
 * configuration and is what `ClaudeClientProvider` keys its client cache on. Its
 * [toString] never prints the key.
 */
data class ClaudeConnection(
    val apiKey: String,
    /** Overrides the API host — for a gateway or proxy. Null means Anthropic's own. */
    val baseUrl: String? = null,
    val timeout: Duration = DEFAULT_TIMEOUT,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
) {
    init {
        require(apiKey.isNotBlank()) { "'apiKey' is required on the Claude plugin configuration" }
        require(!timeout.isNegative && !timeout.isZero) { "'timeout' must be positive (was $timeout)" }
        require(maxRetries >= 0) { "'maxRetries' cannot be negative (was $maxRetries)" }
    }

    override fun toString(): String =
        "ClaudeConnection(baseUrl=$baseUrl, timeout=$timeout, maxRetries=$maxRetries, apiKey=***)"

    companion object {
        /** The SDK's own default. Generous, because a long answer is a slow answer. */
        val DEFAULT_TIMEOUT: Duration = Duration.ofMinutes(10)
        const val DEFAULT_MAX_RETRIES = 2
    }
}
