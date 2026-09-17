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
import com.ritense.valtimoplugins.claude.service.ClaudePromptTemplateResolver
import com.ritense.valtimoplugins.claude.service.ClaudeResultMapper
import com.ritense.valtimoplugins.claude.service.ClaudeService
import com.ritense.valueresolver.ValueResolverService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.util.unit.DataSize

/**
 * Every bean here is named after the plugin, and deliberately so. A bean name is the method
 * name, and an application that already has a bean under that name silently *wins*: Spring
 * skips the auto-configuration's `@Bean` method instead of registering it, because Valtimo
 * applications run with `spring.main.allow-bean-definition-overriding: true` — with
 * overriding off the same clash is an error at startup, with it on the bean simply is not
 * there, and the first bean that asks for it fails with "required a bean of type ... that
 * could not be found". A name no other module would pick keeps this plugin out of that.
 */
@AutoConfiguration
class ClaudeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ClaudeClientProvider::class)
    fun claudeClientProvider(): ClaudeClientProvider = ClaudeClientProvider()

    @Bean
    @ConditionalOnMissingBean(ClaudeClient::class)
    fun claudeClient(claudeClientProvider: ClaudeClientProvider): ClaudeClient = ClaudeClient(claudeClientProvider)

    @Bean
    @ConditionalOnMissingBean(ClaudePromptTemplateResolver::class)
    fun claudePromptTemplateResolver(
        valueResolverService: ValueResolverService,
        objectMapper: ObjectMapper,
    ): ClaudePromptTemplateResolver = ClaudePromptTemplateResolver(valueResolverService, objectMapper)

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
        claudePromptTemplateResolver: ClaudePromptTemplateResolver,
        claudeDocumentResolver: ClaudeDocumentResolver,
        claudeResultMapper: ClaudeResultMapper,
        valueResolverService: ValueResolverService,
    ): ClaudeService =
        ClaudeService(
            claudeClient,
            claudePromptTemplateResolver,
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
