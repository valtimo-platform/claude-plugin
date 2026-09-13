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

import com.ritense.plugin.PluginFactory
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.annotation.SkipComponentScan
import com.ritense.valtimoplugins.claude.service.ClaudeService
import org.springframework.stereotype.Component

/**
 * Builds a [ClaudePlugin] for each saved configuration; the plugin framework then
 * injects that configuration's properties into the instance.
 */
@SkipComponentScan
@Component
class ClaudePluginFactory(
    pluginService: PluginService,
    private val claudeService: ClaudeService,
) : PluginFactory<ClaudePlugin>(pluginService) {
    override fun create(): ClaudePlugin = ClaudePlugin(claudeService)
}
