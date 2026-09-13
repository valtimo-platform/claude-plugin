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

import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.PlainTextSource
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.ritense.valtimoplugins.claude.domain.ClaudeAnswer
import com.ritense.valtimoplugins.claude.domain.ClaudeConnection
import com.ritense.valtimoplugins.claude.domain.ClaudeDocument
import com.ritense.valtimoplugins.claude.domain.ClaudeEffort
import com.ritense.valtimoplugins.claude.domain.ClaudeMessage
import com.ritense.valtimoplugins.claude.domain.ClaudeRequest
import com.ritense.valtimoplugins.claude.domain.ClaudeRole
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/**
 * The single place where this plugin's vocabulary meets the Anthropic SDK.
 *
 * Everything above it speaks [ClaudeRequest] / [ClaudeAnswer]; everything the SDK
 * exposes stops here. That is what lets the plugin later grow a tool-use loop — it
 * becomes another method on this class, over the same `MessageCreateParams` builder,
 * without any BPMN-facing code learning about content blocks.
 */
open class ClaudeClient(
    private val clientProvider: ClaudeClientProvider,
) {
    /**
     * Sends [request] and returns the reply. Exceptions from the SDK (`4xx`/`5xx`,
     * timeouts, connection failures) are deliberately not caught: a failed call should
     * raise a BPMN incident, which is visible and retryable, rather than quietly become
     * an answer that says "error".
     */
    open fun send(
        connection: ClaudeConnection,
        request: ClaudeRequest,
    ): ClaudeAnswer {
        val params = paramsOf(request)
        logger.debug {
            "Asking ${request.model} (maxTokens=${request.maxTokens}, effort=${request.effort?.value ?: "default"}, " +
                "messages=${request.messages.size})"
        }
        return answerOf(clientProvider.clientFor(connection).messages().create(params))
    }

    private fun paramsOf(request: ClaudeRequest): MessageCreateParams {
        val builder =
            MessageCreateParams
                .builder()
                .model(request.model)
                .maxTokens(request.maxTokens)

        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { builder.system(it) }
        request.effort?.let { builder.outputConfig(OutputConfig.builder().effort(effortOf(it)).build()) }

        // Adaptive is the only supported on-mode on the models this plugin targets;
        // the fixed `budget_tokens` form is rejected by Opus 5 and its generation.
        if (request.thinking) {
            builder.thinking(ThinkingConfigAdaptive.builder().build())
        } else {
            builder.thinking(ThinkingConfigDisabled.builder().build())
        }

        request.messages.forEach { message ->
            val blocks = blocksOf(message)
            when (message.role) {
                ClaudeRole.USER -> builder.addUserMessageOfBlockParams(blocks)
                ClaudeRole.ASSISTANT -> builder.addAssistantMessageOfBlockParams(blocks)
            }
        }
        return builder.build()
    }

    /**
     * Documents come first and the text last: Claude attends to a question asked *about*
     * material it has already read, and the API's own guidance for documents is the same.
     */
    private fun blocksOf(message: ClaudeMessage): List<ContentBlockParam> =
        message.documents.map { blockOf(it) } +
            ContentBlockParam.ofText(TextBlockParam.builder().text(message.text).build())

    private fun blockOf(document: ClaudeDocument): ContentBlockParam =
        when (document.kind) {
            ClaudeDocument.Kind.PDF ->
                ContentBlockParam.ofDocument(
                    DocumentBlockParam
                        .builder()
                        .source(Base64PdfSource.builder().data(document.base64Content).build())
                        .title(document.fileName)
                        .build(),
                )

            ClaudeDocument.Kind.IMAGE ->
                ContentBlockParam.ofImage(
                    ImageBlockParam
                        .builder()
                        .source(
                            Base64ImageSource
                                .builder()
                                .mediaType(Base64ImageSource.MediaType.of(document.mediaType))
                                .data(document.base64Content)
                                .build(),
                        ).build(),
                )

            ClaudeDocument.Kind.TEXT ->
                ContentBlockParam.ofDocument(
                    DocumentBlockParam
                        .builder()
                        .source(PlainTextSource.builder().data(decodeText(document)).build())
                        .title(document.fileName)
                        .build(),
                )

            ClaudeDocument.Kind.UNSUPPORTED ->
                throw IllegalArgumentException(
                    "Cannot attach '${document.fileName}': media type '${document.mediaType}' is not supported. " +
                        "Supported: ${ClaudeDocument.SUPPORTED_TYPES.joinToString()}",
                )
        }

    private fun decodeText(document: ClaudeDocument): String =
        String(Base64.getDecoder().decode(document.base64Content), UTF_8)

    private fun effortOf(effort: ClaudeEffort): OutputConfig.Effort = OutputConfig.Effort.of(effort.value)

    private fun answerOf(message: Message): ClaudeAnswer {
        val refusal = message.stopDetails().orElse(null)
        return ClaudeAnswer(
            // A reply can hold several text blocks (citations split them), and on a
            // thinking model the thinking blocks are simply not text — joining the text
            // blocks is the whole answer and nothing else.
            text =
                message
                    .content()
                    .mapNotNull { it.text().orElse(null)?.text() }
                    .joinToString(separator = "\n"),
            model = message.model().asString(),
            stopReason = message.stopReason().orElse(null)?.asString(),
            inputTokens = message.usage().inputTokens(),
            outputTokens = message.usage().outputTokens(),
            refusalCategory = refusal?.category()?.orElse(null)?.asString(),
            refusalExplanation = refusal?.explanation()?.orElse(null),
        )
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
