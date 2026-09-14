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

package com.ritense.valtimoplugins.claude.plugin

import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginAction
import com.ritense.plugin.annotation.PluginActionProperty
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.processlink.domain.ActivityTypeWithEventName.SERVICE_TASK_START
import com.ritense.valtimoplugins.claude.domain.ClaudeConnection
import com.ritense.valtimoplugins.claude.domain.ClaudeEffort
import com.ritense.valtimoplugins.claude.domain.ClaudeResultMapping
import com.ritense.valtimoplugins.claude.domain.ClaudeSettings
import com.ritense.valtimoplugins.claude.service.ClaudeService
import org.operaton.bpm.engine.delegate.DelegateExecution
import java.time.Duration

/**
 * Asks Claude a question from a BPMN process and puts the answer back into the case.
 *
 * The plugin configuration owns the credentials and the model settings; each `ask-claude`
 * service task owns its prompt. What the action actually *does* lives in [ClaudeService]
 * — this class is the BPMN-facing shell plus the configuration it reads.
 */
@Plugin(
    key = ClaudePlugin.PLUGIN_KEY,
    title = "Claude",
    description = "Ask Claude a question from a process and use the answer in the case.",
)
open class ClaudePlugin(
    private val claudeService: ClaudeService,
) {
    @PluginProperty(key = "apiKey", secret = true, required = true)
    lateinit var apiKey: String

    /** Leave empty for the Anthropic API; set it to route through a gateway or proxy. */
    @PluginProperty(key = "baseUrl", secret = false, required = false)
    var baseUrl: String? = null

    @PluginProperty(key = "model", secret = false, required = false)
    var model: String? = null

    @PluginProperty(key = "maxTokens", secret = false, required = false)
    var maxTokens: Int? = null

    /** `low`, `medium`, `high` (the API's own default), `xhigh` or `max`. */
    @PluginProperty(key = "effort", secret = false, required = false)
    var effort: String? = null

    /**
     * Adaptive thinking, on unless switched off. Switching it off is only accepted up to
     * effort `high`; [ClaudeSettings] and [ClaudeConnection] are rebuilt per action so
     * that combination surfaces as a configuration error, not an opaque API 400.
     */
    @PluginProperty(key = "thinking", secret = false, required = false)
    var thinking: Boolean? = null

    /** Applied to every action on this configuration unless the action overrides it. */
    @PluginProperty(key = "systemPrompt", secret = false, required = false)
    var systemPrompt: String? = null

    /** Seconds to wait for an answer. A hard question at high effort is a slow one. */
    @PluginProperty(key = "timeoutSeconds", secret = false, required = false)
    var timeoutSeconds: Int? = null

    @PluginAction(
        key = ASK_ACTION_KEY,
        title = "Ask Claude",
        description = "Sends a prompt to Claude and stores the answer in the case",
        activityTypes = [SERVICE_TASK_START],
    )
    open fun askClaude(
        execution: DelegateExecution,
        // Lines rather than one string, and not a matter of taste: `PluginService` runs
        // every *textual* action property through `ValueResolverService` before this method
        // is called, and that reads a leading `word:` as a resolver prefix. A prompt opening
        // "Context: ..." therefore dies with "No resolver factory found for value prefix
        // Context" before the action ever runs — and takes the other properties in the same
        // batch down with it. A JSON array is not textual, so it arrives untouched. It also
        // reads better in a hand-written process link, where a prompt is a list of lines.
        @PluginActionProperty prompt: List<String>?,
        @PluginActionProperty systemPrompt: List<String>?,
        // `Any?` rather than `String?`: a Valtimo file upload leaves a list of file
        // references in its process variable, and `ClaudeDocumentResolver` reads the id
        // out of either that or a plain id string.
        @PluginActionProperty documentResourceId: Any?,
        @PluginActionProperty resultVariable: String?,
        @PluginActionProperty resultMappings: List<ClaudeResultMapping>?,
    ) {
        claudeService.ask(
            execution = execution,
            connection = connection(),
            settings = settings(),
            ask =
                ClaudeService.AskRequest(
                    prompt = prompt.asText(),
                    systemPrompt = systemPrompt.asText(),
                    documentResourceId = documentResourceId,
                    resultVariable = resultVariable,
                    resultMappings = resultMappings,
                ),
        )
    }

    /** The lines of a prompt as the single string that is sent. */
    private fun List<String>?.asText(): String? = this?.joinToString(separator = "\n")

    /** How this configuration reaches the API. */
    fun connection(): ClaudeConnection =
        ClaudeConnection(
            apiKey = apiKey,
            baseUrl = baseUrl?.takeIf { it.isNotBlank() },
            timeout =
                timeoutSeconds
                    ?.takeIf { it > 0 }
                    ?.let { Duration.ofSeconds(it.toLong()) }
                    ?: ClaudeConnection.DEFAULT_TIMEOUT,
        )

    /** What this configuration asks, and how hard Claude thinks about it. */
    fun settings(): ClaudeSettings =
        ClaudeSettings(
            model = model?.takeIf { it.isNotBlank() } ?: ClaudeSettings.DEFAULT_MODEL,
            maxTokens = maxTokens?.takeIf { it > 0 }?.toLong() ?: ClaudeSettings.DEFAULT_MAX_TOKENS,
            effort = ClaudeEffort.ofOrNull(effort),
            thinking = thinking ?: true,
            systemPrompt = systemPrompt?.takeIf { it.isNotBlank() },
        )

    companion object {
        /** The plugin definition key; must match `pluginId` in the frontend specification. */
        const val PLUGIN_KEY = "claude"

        const val ASK_ACTION_KEY = "ask-claude"
    }
}
