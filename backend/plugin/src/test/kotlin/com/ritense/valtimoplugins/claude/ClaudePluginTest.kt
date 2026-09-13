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

import com.ritense.valtimoplugins.claude.domain.ClaudeConnection
import com.ritense.valtimoplugins.claude.domain.ClaudeEffort
import com.ritense.valtimoplugins.claude.domain.ClaudeMessage
import com.ritense.valtimoplugins.claude.domain.ClaudeRequest
import com.ritense.valtimoplugins.claude.domain.ClaudeRole
import com.ritense.valtimoplugins.claude.domain.ClaudeSettings
import com.ritense.valtimoplugins.claude.plugin.ClaudePlugin
import com.ritense.valtimoplugins.claude.service.ClaudeService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ClaudePluginTest : BaseTest() {
    private val claudeService: ClaudeService = mock()

    @Test
    fun `should fall back to the defaults when nothing is configured`() {
        val plugin = plugin()

        assertEquals(ClaudeSettings.DEFAULT_MODEL, plugin.settings().model)
        assertEquals(ClaudeSettings.DEFAULT_MAX_TOKENS, plugin.settings().maxTokens)
        assertNull(plugin.settings().effort)
        assertTrue(plugin.settings().thinking)
        assertNull(plugin.settings().systemPrompt)
        assertNull(plugin.connection().baseUrl)
        assertEquals(ClaudeConnection.DEFAULT_TIMEOUT, plugin.connection().timeout)
    }

    @Test
    fun `should read the configured settings`() {
        val plugin =
            plugin().apply {
                model = "claude-sonnet-5"
                maxTokens = 4096
                effort = "XHigh"
                thinking = false
                systemPrompt = "You are terse."
                baseUrl = "https://gateway.example.nl"
                timeoutSeconds = 120
            }

        assertEquals("claude-sonnet-5", plugin.settings().model)
        assertEquals(4096L, plugin.settings().maxTokens)
        assertEquals(ClaudeEffort.XHIGH, plugin.settings().effort)
        assertEquals("You are terse.", plugin.settings().systemPrompt)
        assertEquals("https://gateway.example.nl", plugin.connection().baseUrl)
        assertEquals(Duration.ofSeconds(120), plugin.connection().timeout)
    }

    @Test
    fun `should treat blank and non positive settings as unset`() {
        val plugin =
            plugin().apply {
                model = "  "
                maxTokens = 0
                effort = " "
                systemPrompt = ""
                baseUrl = ""
                timeoutSeconds = -1
            }

        assertEquals(ClaudeSettings.DEFAULT_MODEL, plugin.settings().model)
        assertEquals(ClaudeSettings.DEFAULT_MAX_TOKENS, plugin.settings().maxTokens)
        assertNull(plugin.settings().effort)
        assertNull(plugin.settings().systemPrompt)
        assertNull(plugin.connection().baseUrl)
        assertEquals(ClaudeConnection.DEFAULT_TIMEOUT, plugin.connection().timeout)
    }

    @Test
    fun `should reject an unknown effort`() {
        val exception = assertThrows<IllegalArgumentException> { plugin().apply { effort = "extreme" }.settings() }

        assertTrue(exception.message!!.contains("extreme"))
    }

    @Test
    fun `should reject thinking switched off above effort high`() {
        // The API answers this combination with a 400; catching it here names the two
        // fields that are in conflict instead.
        val exception =
            assertThrows<IllegalArgumentException> {
                requestOf(effort = ClaudeEffort.MAX, thinking = false)
            }

        assertTrue(exception.message!!.contains("max"))
        requestOf(effort = ClaudeEffort.HIGH, thinking = false)
        requestOf(effort = ClaudeEffort.MAX, thinking = true)
    }

    @Test
    fun `should never print the api key`() {
        assertTrue(plugin().connection().toString().contains("apiKey=***"))
        assertTrue(!plugin().connection().toString().contains("sk-test"))
    }

    private fun requestOf(
        effort: ClaudeEffort,
        thinking: Boolean,
    ) = ClaudeRequest(
        model = ClaudeSettings.DEFAULT_MODEL,
        maxTokens = 1000,
        messages = listOf(ClaudeMessage(ClaudeRole.USER, "Hello")),
        effort = effort,
        thinking = thinking,
    )

    private fun plugin() = ClaudePlugin(claudeService).apply { apiKey = "sk-test" }
}
