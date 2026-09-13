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

import com.ritense.valtimoplugins.claude.domain.ClaudeAnswer

/**
 * Maps a [ClaudeAnswer] onto the `claude*` process variables.
 *
 * One place rather than inline in the plugin action, because these names are the
 * plugin's public contract with every BPMN model that uses it: a gateway conditioned on
 * `claudeStopReason` breaks if this set drifts.
 */
object ClaudeAnswerVariables {
    /** Default name for the answer text; overridable per action. */
    const val DEFAULT_RESULT_VARIABLE = "claudeAnswer"

    /** Why the configured result mapping did not (fully) succeed; absent when it did. */
    const val VAR_MAPPING_ERROR = "claudeMappingError"

    const val VAR_MODEL = "claudeModel"
    const val VAR_STOP_REASON = "claudeStopReason"
    const val VAR_INPUT_TOKENS = "claudeInputTokens"
    const val VAR_OUTPUT_TOKENS = "claudeOutputTokens"
    const val VAR_REFUSAL_CATEGORY = "claudeRefusalCategory"
    const val VAR_REFUSAL_EXPLANATION = "claudeRefusalExplanation"

    fun from(
        answer: ClaudeAnswer,
        resultVariable: String = DEFAULT_RESULT_VARIABLE,
    ): Map<String, Any> {
        val variables =
            mutableMapOf<String, Any>(
                resultVariable to answer.text,
                VAR_MODEL to answer.model,
                VAR_INPUT_TOKENS to answer.inputTokens,
                VAR_OUTPUT_TOKENS to answer.outputTokens,
            )
        answer.stopReason?.let { variables[VAR_STOP_REASON] = it }
        answer.refusalCategory?.let { variables[VAR_REFUSAL_CATEGORY] = it }
        answer.refusalExplanation?.let { variables[VAR_REFUSAL_EXPLANATION] = it }
        return variables
    }
}
