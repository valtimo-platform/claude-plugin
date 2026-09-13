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

package com.ritense.valtimoplugins.claude.autoconfiguration

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.plugin.service.PluginService
import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.valtimoplugins.claude.client.ClaudeClient
import com.ritense.valtimoplugins.claude.client.ClaudeClientProvider
import com.ritense.valtimoplugins.claude.plugin.ClaudePluginFactory
import com.ritense.valtimoplugins.claude.service.ClaudeDocumentResolver
import com.ritense.valtimoplugins.claude.service.ClaudeResultMapper
import com.ritense.valtimoplugins.claude.service.ClaudeService
import com.ritense.valtimoplugins.claude.service.PromptTemplateResolver
import com.ritense.valueresolver.ValueResolverService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.util.unit.DataSize

@AutoConfiguration
class ClaudeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ClaudeClientProvider::class)
    fun claudeClientProvider(): ClaudeClientProvider = ClaudeClientProvider()

    @Bean
    @ConditionalOnMissingBean(ClaudeClient::class)
    fun claudeClient(claudeClientProvider: ClaudeClientProvider): ClaudeClient = ClaudeClient(claudeClientProvider)

    @Bean
    @ConditionalOnMissingBean(PromptTemplateResolver::class)
    fun promptTemplateResolver(
        valueResolverService: ValueResolverService,
        objectMapper: ObjectMapper,
    ): PromptTemplateResolver = PromptTemplateResolver(valueResolverService, objectMapper)

    @Bean
    @ConditionalOnMissingBean(ClaudeDocumentResolver::class)
    fun claudeDocumentResolver(
        temporaryResourceStorageService: TemporaryResourceStorageService,
        // The ceiling is about the HTTP request the document ends up in, not about disk;
        // base64 inflates the bytes by a third on the way out.
        @Value("\${valtimo.claude.max-document-size:10MB}") maxDocumentSize: DataSize,
    ): ClaudeDocumentResolver = ClaudeDocumentResolver(temporaryResourceStorageService, maxDocumentSize.toBytes())

    @Bean
    @ConditionalOnMissingBean(ClaudeResultMapper::class)
    fun claudeResultMapper(objectMapper: ObjectMapper): ClaudeResultMapper = ClaudeResultMapper(objectMapper)

    @Bean
    @ConditionalOnMissingBean(ClaudeService::class)
    fun claudeService(
        claudeClient: ClaudeClient,
        promptTemplateResolver: PromptTemplateResolver,
        claudeDocumentResolver: ClaudeDocumentResolver,
        claudeResultMapper: ClaudeResultMapper,
        valueResolverService: ValueResolverService,
    ): ClaudeService =
        ClaudeService(
            claudeClient,
            promptTemplateResolver,
            claudeDocumentResolver,
            claudeResultMapper,
            valueResolverService,
        )

    @Bean
    @ConditionalOnMissingBean(ClaudePluginFactory::class)
    fun claudePluginFactory(
        pluginService: PluginService,
        claudeService: ClaudeService,
    ): ClaudePluginFactory = ClaudePluginFactory(pluginService, claudeService)
}
