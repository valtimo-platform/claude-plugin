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

import com.ritense.valtimoplugins.claude.client.ClaudeClient
import com.ritense.valtimoplugins.claude.domain.ClaudeAnswer
import com.ritense.valtimoplugins.claude.domain.ClaudeConnection
import com.ritense.valtimoplugins.claude.domain.ClaudeMessage
import com.ritense.valtimoplugins.claude.domain.ClaudeRequest
import com.ritense.valtimoplugins.claude.domain.ClaudeResultMapping
import com.ritense.valtimoplugins.claude.domain.ClaudeRole
import com.ritense.valtimoplugins.claude.domain.ClaudeSettings
import com.ritense.valueresolver.ValueResolverService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.operaton.bpm.engine.delegate.DelegateExecution

/**
 * Everything one `ask-claude` step does, between the plugin action that supplies the
 * configuration and the client that speaks HTTP: resolve the prompt against case data,
 * attach the document, ask, then route the answer into process variables and the case
 * document.
 *
 * Kept out of `ClaudePlugin` so this sequence is testable without a plugin instance —
 * and because the multi-turn and tool-using variants this plugin is headed for are
 * variations on *this* method, not on the BPMN-facing shell.
 */
open class ClaudeService(
    private val claudeClient: ClaudeClient,
    private val promptTemplateResolver: PromptTemplateResolver,
    private val documentResolver: ClaudeDocumentResolver,
    private val resultMapper: ClaudeResultMapper,
    private val valueResolverService: ValueResolverService,
) {
    /** The prompt, the document and the answer handling for one service task. */
    data class AskRequest(
        val prompt: String?,
        val systemPrompt: String?,
        /** A resource id, or the list of file references a Valtimo upload leaves behind. */
        val documentResourceId: Any?,
        val resultVariable: String?,
        val resultMappings: List<ClaudeResultMapping>?,
    )

    /**
     * Asks [settings]'s model the resolved [ask] prompt and writes the answer back into
     * the process. Returns the answer so callers (and tests) can assert on it without
     * reading process variables.
     */
    open fun ask(
        execution: DelegateExecution,
        connection: ClaudeConnection,
        settings: ClaudeSettings,
        ask: AskRequest,
    ): ClaudeAnswer {
        val prompt = promptTemplateResolver.resolve(ask.prompt, execution)
        require(!prompt.isNullOrBlank()) { "'prompt' is required on the ask-claude action" }

        // The system prompt is a template too: a process that says "you are answering
        // about case {{doc:/zaaknummer}}" wants the same substitution the prompt gets.
        val systemPrompt =
            promptTemplateResolver.resolve(
                ask.systemPrompt?.takeIf { it.isNotBlank() } ?: settings.systemPrompt,
                execution,
            )

        val request =
            ClaudeRequest(
                model = settings.model,
                maxTokens = settings.maxTokens,
                messages =
                    listOf(
                        ClaudeMessage(
                            role = ClaudeRole.USER,
                            text = prompt,
                            documents = documentResolver.resolve(ask.documentResourceId),
                        ),
                    ),
                systemPrompt = systemPrompt,
                effort = settings.effort,
                thinking = settings.thinking,
            )

        val answer = claudeClient.send(connection, request)
        logger.info {
            "Claude answered in process instance '${execution.processInstanceId}' " +
                "(model=${answer.model}, stopReason=${answer.stopReason}, " +
                "tokens in/out=${answer.inputTokens}/${answer.outputTokens})"
        }
        warnOnIncompleteAnswer(answer, execution)

        writeBack(execution, ask, answer)
        return answer
    }

    /**
     * Neither a refusal nor a truncated answer is an error the API reports as one — both
     * arrive as a perfectly normal `200`. Logging them is what makes an empty
     * `claudeAnswer` explicable afterwards; the process still continues, and can branch
     * on `claudeStopReason` if it cares.
     */
    private fun warnOnIncompleteAnswer(
        answer: ClaudeAnswer,
        execution: DelegateExecution,
    ) {
        if (answer.isRefusal) {
            logger.warn {
                "Claude declined to answer in process instance '${execution.processInstanceId}' " +
                    "(category=${answer.refusalCategory}): ${answer.refusalExplanation}"
            }
        }
        if (answer.isTruncated) {
            logger.warn {
                "Claude's answer in process instance '${execution.processInstanceId}' was cut off at the " +
                    "configured maximum number of tokens and is incomplete"
            }
        }
    }

    /**
     * Writes the answer and its metadata as process variables, then applies the result
     * mapping. Both `pv:` and `doc:` targets go through `handleValues`, which is the one
     * call that knows how to write either.
     */
    private fun writeBack(
        execution: DelegateExecution,
        ask: AskRequest,
        answer: ClaudeAnswer,
    ) {
        val resultVariable =
            ask.resultVariable?.trim()?.takeIf { it.isNotEmpty() }
                ?: ClaudeAnswerVariables.DEFAULT_RESULT_VARIABLE
        execution.setVariables(ClaudeAnswerVariables.from(answer, resultVariable))

        val mapped = resultMapper.map(ask.resultMappings, answer.text)
        if (!mapped.isEmpty) {
            try {
                valueResolverService.handleValues(execution.processInstanceId, execution, mapped.values)
                logger.debug { "Wrote ${mapped.values.keys} from Claude's answer" }
            } catch (e: Exception) {
                // A mapping problem must not strand a process that already has its answer.
                logger.error(e) { "Failed to write ${mapped.values.keys} from Claude's answer" }
                execution.setVariable(
                    ClaudeAnswerVariables.VAR_MAPPING_ERROR,
                    "Could not write the mapped values: ${e.message}",
                )
                return
            }
        }
        mapped.error?.let { execution.setVariable(ClaudeAnswerVariables.VAR_MAPPING_ERROR, it) }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
