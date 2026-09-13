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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.valtimoplugins.claude.client.ClaudeClient
import com.ritense.valtimoplugins.claude.client.ClaudeClientProvider
import com.ritense.valtimoplugins.claude.domain.ClaudeConnection
import com.ritense.valtimoplugins.claude.domain.ClaudeDocument
import com.ritense.valtimoplugins.claude.domain.ClaudeEffort
import com.ritense.valtimoplugins.claude.domain.ClaudeMessage
import com.ritense.valtimoplugins.claude.domain.ClaudeRequest
import com.ritense.valtimoplugins.claude.domain.ClaudeRole
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the client against a stand-in Messages API rather than a mock of the SDK,
 * so the assertions are about the JSON that actually leaves the process — which is the
 * only part of the SDK's builder API this plugin is betting on.
 */
internal class ClaudeClientTest : BaseTest() {
    private lateinit var server: MockWebServer
    private lateinit var client: ClaudeClient
    private lateinit var connection: ClaudeConnection

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ClaudeClient(ClaudeClientProvider())
        connection =
            ClaudeConnection(
                apiKey = "sk-test",
                baseUrl = server.url("/").toString().removeSuffix("/"),
                // No retries: a test that asserts on one recorded request should not have
                // to reason about how many the SDK decided to send.
                maxRetries = 0,
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `should send prompt and return the answer`() {
        server.enqueue(answerResponse("The answer is 42.", inputTokens = 120, outputTokens = 8))

        val answer = client.send(connection, requestOf("What is the answer?"))

        assertEquals("The answer is 42.", answer.text)
        assertEquals("claude-opus-5", answer.model)
        assertEquals("end_turn", answer.stopReason)
        assertEquals(120, answer.inputTokens)
        assertEquals(8, answer.outputTokens)
        assertFalse(answer.isRefusal)
        assertFalse(answer.isTruncated)

        val request = server.takeRequest()
        assertTrue(request.path!!.endsWith("/v1/messages"))
        assertEquals("sk-test", request.getHeader("x-api-key"))

        val body = bodyOf(request)
        assertEquals("claude-opus-5", body["model"].asText())
        assertEquals(16000, body["max_tokens"].asInt())
        assertEquals("user", body["messages"][0]["role"].asText())
        assertEquals("What is the answer?", body["messages"][0]["content"][0]["text"].asText())
    }

    @Test
    fun `should join every text block of a reply`() {
        server.enqueue(
            jsonResponse(
                """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5",
                 "content":[{"type":"thinking","thinking":"","signature":"sig"},
                            {"type":"text","text":"First."},
                            {"type":"text","text":"Second."}],
                 "stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":2}}
                """.trimIndent(),
            ),
        )

        val answer = client.send(connection, requestOf("Two paragraphs, please"))

        assertEquals("First.\nSecond.", answer.text)
    }

    @Test
    fun `should send the system prompt, effort and adaptive thinking`() {
        server.enqueue(answerResponse("ok"))

        client.send(
            connection,
            requestOf("Hello").copy(
                systemPrompt = "You are terse.",
                effort = ClaudeEffort.XHIGH,
            ),
        )

        val body = bodyOf(server.takeRequest())
        assertEquals("You are terse.", body["system"].asText())
        assertEquals("xhigh", body["output_config"]["effort"].asText())
        assertEquals("adaptive", body["thinking"]["type"].asText())
    }

    @Test
    fun `should disable thinking when it is switched off`() {
        server.enqueue(answerResponse("ok"))

        client.send(connection, requestOf("Hello").copy(thinking = false))

        assertEquals("disabled", bodyOf(server.takeRequest())["thinking"]["type"].asText())
    }

    @Test
    fun `should omit the system prompt when there is none`() {
        server.enqueue(answerResponse("ok"))

        client.send(connection, requestOf("Hello"))

        assertNull(bodyOf(server.takeRequest())["system"])
    }

    @Test
    fun `should attach a pdf as a document block before the prompt`() {
        server.enqueue(answerResponse("ok"))

        client.send(connection, requestWithDocument("invoice.pdf", ClaudeDocument.PDF, "%PDF-1.4 fake"))

        val content = bodyOf(server.takeRequest())["messages"][0]["content"]
        assertEquals("document", content[0]["type"].asText())
        assertEquals("invoice.pdf", content[0]["title"].asText())
        assertEquals("base64", content[0]["source"]["type"].asText())
        assertEquals("application/pdf", content[0]["source"]["media_type"].asText())
        assertEquals("text", content[1]["type"].asText())
    }

    @Test
    fun `should attach an image as an image block`() {
        server.enqueue(answerResponse("ok"))

        client.send(connection, requestWithDocument("scan.png", "image/png", "not really a png"))

        val content = bodyOf(server.takeRequest())["messages"][0]["content"]
        assertEquals("image", content[0]["type"].asText())
        assertEquals("image/png", content[0]["source"]["media_type"].asText())
    }

    @Test
    fun `should attach text as a plain text document, decoded`() {
        server.enqueue(answerResponse("ok"))

        client.send(connection, requestWithDocument("notes.txt", "text/plain; charset=UTF-8", "Plain notes"))

        val source = bodyOf(server.takeRequest())["messages"][0]["content"][0]["source"]
        assertEquals("text", source["type"].asText())
        assertEquals("Plain notes", source["data"].asText())
    }

    @Test
    fun `should refuse to attach an unsupported media type`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                client.send(connection, requestWithDocument("offer.docx", "application/vnd.ms-word", "binary"))
            }

        assertTrue(exception.message!!.contains("offer.docx"))
        assertTrue(exception.message!!.contains("application/vnd.ms-word"))
    }

    @Test
    fun `should report a refusal with its category and explanation`() {
        server.enqueue(
            jsonResponse(
                """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5","content":[],
                 "stop_reason":"refusal",
                 "stop_details":{"type":"refusal","category":"cyber","explanation":"Not this one."},
                 "usage":{"input_tokens":5,"output_tokens":0}}
                """.trimIndent(),
            ),
        )

        val answer = client.send(connection, requestOf("Something disallowed"))

        assertTrue(answer.isRefusal)
        assertEquals("cyber", answer.refusalCategory)
        assertEquals("Not this one.", answer.refusalExplanation)
        assertEquals("", answer.text)
    }

    @Test
    fun `should report a truncated answer`() {
        server.enqueue(answerResponse("The beginning of a long", stopReason = "max_tokens"))

        assertTrue(client.send(connection, requestOf("Write an essay")).isTruncated)
    }

    @Test
    fun `should not swallow an api error`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""),
        )

        assertThrows<RuntimeException> { client.send(connection, requestOf("Hello")) }
    }

    private fun requestOf(prompt: String) =
        ClaudeRequest(
            model = "claude-opus-5",
            maxTokens = 16_000,
            messages = listOf(ClaudeMessage(ClaudeRole.USER, prompt)),
        )

    private fun requestWithDocument(
        fileName: String,
        contentType: String,
        content: String,
    ) = ClaudeRequest(
        model = "claude-opus-5",
        maxTokens = 16_000,
        messages =
            listOf(
                ClaudeMessage(
                    role = ClaudeRole.USER,
                    text = "What is in this file?",
                    documents =
                        listOf(
                            ClaudeDocument(
                                fileName = fileName,
                                contentType = contentType,
                                base64Content = Base64.getEncoder().encodeToString(content.toByteArray(UTF_8)),
                            ),
                        ),
                ),
            ),
    )

    private fun answerResponse(
        text: String,
        stopReason: String = "end_turn",
        inputTokens: Int = 1,
        outputTokens: Int = 1,
    ) = jsonResponse(
        """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5",
         "content":[{"type":"text","text":${objectMapper.writeValueAsString(text)}}],
         "stop_reason":"$stopReason",
         "usage":{"input_tokens":$inputTokens,"output_tokens":$outputTokens}}
        """.trimIndent(),
    )

    private fun jsonResponse(body: String) =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun bodyOf(request: RecordedRequest): JsonNode = objectMapper.readTree(request.body.readString(UTF_8))
}
