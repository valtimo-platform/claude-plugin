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
 * One line of the result mapping: [source] is a JSON pointer into Claude's answer,
 * [target] a Valtimo value-resolver expression such as `pv:urgency` or `doc:/zaak/urgentie`.
 *
 * Both are nullable because they arrive straight from the process-link configuration,
 * where a half-filled row is possible; `ClaudeResultMapper` drops those.
 */
data class ClaudeResultMapping(
    val source: String? = null,
    val target: String? = null,
)
