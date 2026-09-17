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

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.valtimoplugins.claude.client.ClaudeClient
import com.ritense.valtimoplugins.claude.domain.ClaudeAnswer
import com.ritense.valtimoplugins.claude.domain.ClaudeConnection
import com.ritense.valtimoplugins.claude.domain.ClaudeDocument
import com.ritense.valtimoplugins.claude.domain.ClaudeRequest
import com.ritense.valtimoplugins.claude.domain.ClaudeResultMapping
import com.ritense.valtimoplugins.claude.domain.ClaudeRole
import com.ritense.valtimoplugins.claude.domain.ClaudeSettings
import com.ritense.valtimoplugins.claude.service.ClaudeAnswerVariables
import com.ritense.valtimoplugins.claude.service.ClaudeDocumentResolver
import com.ritense.valtimoplugins.claude.service.ClaudePromptTemplateResolver
import com.ritense.valtimoplugins.claude.service.ClaudeResultMapper
import com.ritense.valtimoplugins.claude.service.ClaudeService
import com.ritense.valueresolver.ValueResolverService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.operaton.bpm.engine.delegate.DelegateExecution
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ClaudeServiceTest : BaseTest() {
    private val claudeClient: ClaudeClient = mock()
    private val promptTemplateResolver: ClaudePromptTemplateResolver = mock()
    private val documentResolver: ClaudeDocumentResolver = mock()
    private val valueResolverService: ValueResolverService = mock()
    private val execution: DelegateExecution = mock()

    private val service =
        ClaudeService(
            claudeClient,
            promptTemplateResolver,
            documentResolver,
            ClaudeResultMapper(ObjectMapper()),
            valueResolverService,
        )

    private val connection = ClaudeConnection(apiKey = "sk-test")

    @BeforeEach
    fun setUp() {
        whenever(execution.processInstanceId).thenReturn("process-instance-id")
        // By default the resolver hands back whatever it was given.
        whenever(promptTemplateResolver.resolve(any(), eq(execution))).thenAnswer { it.arguments[0] }
        whenever(documentResolver.resolve(any())).thenReturn(emptyList())
        whenever(claudeClient.send(any(), any())).thenReturn(answer("42"))
    }

    @Test
    fun `should ask the resolved prompt and store the answer`() {
        service.ask(execution, connection, ClaudeSettings(), ask(prompt = "What is the answer?"))

        val request = capturedRequest()
        assertEquals(ClaudeSettings.DEFAULT_MODEL, request.model)
        assertEquals(ClaudeSettings.DEFAULT_MAX_TOKENS, request.maxTokens)
        assertEquals(1, request.messages.size)
        assertEquals(ClaudeRole.USER, request.messages[0].role)
        assertEquals("What is the answer?", request.messages[0].text)

        val variables = capturedVariables()
        assertEquals("42", variables[ClaudeAnswerVariables.DEFAULT_RESULT_VARIABLE])
        assertEquals(ClaudeSettings.DEFAULT_MODEL, variables[ClaudeAnswerVariables.VAR_MODEL])
        assertEquals("end_turn", variables[ClaudeAnswerVariables.VAR_STOP_REASON])
        assertEquals(10L, variables[ClaudeAnswerVariables.VAR_INPUT_TOKENS])
        assertEquals(2L, variables[ClaudeAnswerVariables.VAR_OUTPUT_TOKENS])
    }

    @Test
    fun `should store the answer under the configured result variable`() {
        service.ask(execution, connection, ClaudeSettings(), ask(prompt = "Hi", resultVariable = " advice "))

        assertEquals("42", capturedVariables()["advice"])
        assertNull(capturedVariables()[ClaudeAnswerVariables.DEFAULT_RESULT_VARIABLE])
    }

    @Test
    fun `should require a prompt`() {
        assertThrows<IllegalArgumentException> {
            service.ask(execution, connection, ClaudeSettings(), ask(prompt = "  "))
        }
        verify(claudeClient, never()).send(any(), any())
    }

    @Test
    fun `should let the action override the configuration system prompt`() {
        service.ask(
            execution,
            connection,
            ClaudeSettings(systemPrompt = "From the configuration"),
            ask(prompt = "Hi", systemPrompt = "From the action"),
        )

        assertEquals("From the action", capturedRequest().systemPrompt)
    }

    @Test
    fun `should fall back to the configuration system prompt`() {
        service.ask(
            execution,
            connection,
            ClaudeSettings(systemPrompt = "From the configuration"),
            ask(prompt = "Hi", systemPrompt = " "),
        )

        assertEquals("From the configuration", capturedRequest().systemPrompt)
    }

    @Test
    fun `should attach the resolved document`() {
        val document = ClaudeDocument("invoice.pdf", ClaudeDocument.PDF, "ZmFrZQ==")
        whenever(documentResolver.resolve("pv:resourceId")).thenReturn(listOf(document))

        service.ask(execution, connection, ClaudeSettings(), ask(prompt = "Hi", documentResourceId = "pv:resourceId"))

        assertEquals(listOf(document), capturedRequest().messages[0].documents)
    }

    @Test
    fun `should write mapped values through the value resolver`() {
        whenever(claudeClient.send(any(), any())).thenReturn(answer("""{"urgency":"high"}"""))

        service.ask(
            execution,
            connection,
            ClaudeSettings(),
            ask(prompt = "Hi", resultMappings = listOf(ClaudeResultMapping("/urgency", "doc:/zaak/urgentie"))),
        )

        verify(valueResolverService).handleValues(
            eq("process-instance-id"),
            eq(execution),
            eq(mapOf("doc:/zaak/urgentie" to "high")),
        )
        assertNull(capturedVariables()[ClaudeAnswerVariables.VAR_MAPPING_ERROR])
    }

    @Test
    fun `should record a mapping error without failing the step`() {
        whenever(claudeClient.send(any(), any())).thenReturn(answer("Rather urgent, I would say."))

        service.ask(
            execution,
            connection,
            ClaudeSettings(),
            ask(prompt = "Hi", resultMappings = listOf(ClaudeResultMapping("/urgency", "pv:urgency"))),
        )

        verify(valueResolverService, never()).handleValues(any(), any(), any())
        verify(execution).setVariable(eq(ClaudeAnswerVariables.VAR_MAPPING_ERROR), any())
    }

    @Test
    fun `should record a failed write without failing the step`() {
        whenever(claudeClient.send(any(), any())).thenReturn(answer("""{"urgency":"high"}"""))
        whenever(valueResolverService.handleValues(any(), any(), any())).thenThrow(RuntimeException("no such path"))

        service.ask(
            execution,
            connection,
            ClaudeSettings(),
            ask(prompt = "Hi", resultMappings = listOf(ClaudeResultMapping("/urgency", "doc:/nope"))),
        )

        val captor = argumentCaptor<Any>()
        verify(execution).setVariable(eq(ClaudeAnswerVariables.VAR_MAPPING_ERROR), captor.capture())
        assertTrue(captor.firstValue.toString().contains("no such path"))
    }

    @Test
    fun `should surface a refusal as variables rather than an exception`() {
        whenever(claudeClient.send(any(), any())).thenReturn(
            answer("", stopReason = ClaudeAnswer.REFUSAL, category = "cyber", explanation = "Not this one."),
        )

        service.ask(execution, connection, ClaudeSettings(), ask(prompt = "Hi"))

        val variables = capturedVariables()
        assertEquals(ClaudeAnswer.REFUSAL, variables[ClaudeAnswerVariables.VAR_STOP_REASON])
        assertEquals("cyber", variables[ClaudeAnswerVariables.VAR_REFUSAL_CATEGORY])
        assertEquals("Not this one.", variables[ClaudeAnswerVariables.VAR_REFUSAL_EXPLANATION])
    }

    private fun ask(
        prompt: String?,
        systemPrompt: String? = null,
        documentResourceId: String? = null,
        resultVariable: String? = null,
        resultMappings: List<ClaudeResultMapping>? = null,
    ) = ClaudeService.AskRequest(prompt, systemPrompt, documentResourceId, resultVariable, resultMappings)

    private fun answer(
        text: String,
        stopReason: String = "end_turn",
        category: String? = null,
        explanation: String? = null,
    ) = ClaudeAnswer(
        text = text,
        model = ClaudeSettings.DEFAULT_MODEL,
        stopReason = stopReason,
        inputTokens = 10,
        outputTokens = 2,
        refusalCategory = category,
        refusalExplanation = explanation,
    )

    private fun capturedRequest(): ClaudeRequest {
        val captor = argumentCaptor<ClaudeRequest>()
        verify(claudeClient).send(eq(connection), captor.capture())
        return captor.firstValue
    }

    @Suppress("UNCHECKED_CAST")
    private fun capturedVariables(): Map<String, Any> {
        val captor = argumentCaptor<Map<String, Any>>()
        verify(execution).setVariables(captor.capture())
        return captor.firstValue
    }
}
