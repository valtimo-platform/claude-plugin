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

package com.ritense.valtimoplugins.claude.client

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.ritense.valtimoplugins.claude.domain.ClaudeConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * Hands out one [AnthropicClient] per distinct [ClaudeConnection].
 *
 * The SDK client owns an OkHttp connection pool and its own dispatcher threads, so
 * building one per service task would throw away every keep-alive connection and leak
 * threads under load. Configurations are few and long-lived, which is exactly what a
 * cache keyed on the connection wants — and because [ClaudeConnection] is a data class
 * that includes the API key, rotating the key in the admin UI produces a different key
 * and therefore a fresh client, with no invalidation logic to get wrong.
 *
 * Unbounded on purpose: the bound is the number of plugin configurations (times the
 * number of key rotations since the last restart), not anything user traffic drives.
 */
open class ClaudeClientProvider {
    private val clients = ConcurrentHashMap<ClaudeConnection, AnthropicClient>()

    open fun clientFor(connection: ClaudeConnection): AnthropicClient =
        clients.computeIfAbsent(connection) {
            logger.debug { "Building a new Anthropic client for $it" }
            AnthropicOkHttpClient
                .builder()
                .apiKey(it.apiKey)
                .timeout(it.timeout)
                .maxRetries(it.maxRetries)
                .apply { it.baseUrl?.takeIf(String::isNotBlank)?.let { url -> baseUrl(url) } }
                .build()
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
