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
import com.ritense.plugin.service.PluginService
import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.valtimoplugins.claude.autoconfiguration.ClaudeAutoConfiguration
import com.ritense.valtimoplugins.claude.client.ClaudeClient
import com.ritense.valtimoplugins.claude.plugin.ClaudePluginFactory
import com.ritense.valtimoplugins.claude.service.ClaudeDocumentResolver
import com.ritense.valtimoplugins.claude.service.ClaudePromptTemplateResolver
import com.ritense.valtimoplugins.claude.service.ClaudeResultMapper
import com.ritense.valtimoplugins.claude.service.ClaudeService
import com.ritense.valueresolver.ValueResolverService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionService
import org.springframework.util.unit.DataSize
import java.util.function.Supplier

internal class ClaudeAutoConfigurationTest : BaseTest() {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(ValtimoBeans::class.java)
            .withConfiguration(
                AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration::class.java,
                    ClaudeAutoConfiguration::class.java,
                ),
            )

    @Test
    fun `should contribute every bean the plugin runs on`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(ClaudeClient::class.java)
            assertThat(context).hasSingleBean(ClaudePromptTemplateResolver::class.java)
            assertThat(context).hasSingleBean(ClaudeDocumentResolver::class.java)
            assertThat(context).hasSingleBean(ClaudeResultMapper::class.java)
            assertThat(context).hasSingleBean(ClaudeService::class.java)
            assertThat(context).hasSingleBean(ClaudePluginFactory::class.java)
        }
    }

    /**
     * An application is free to have a bean called `promptTemplateResolver`, or
     * `documentResolver`, of its own — those are names anything that talks to a language
     * model might pick. A bean the application registers itself takes the name, and this
     * plugin's `@Bean` method for it is then silently skipped rather than registered, so
     * the plugin's beans have to be named after the plugin.
     *
     * Silently, because a Valtimo application allows bean definition overriding; without
     * that the same clash at least fails loudly, as a `BeanDefinitionOverrideException`.
     */
    @Test
    fun `should keep its beans when the application has ones of its own under general names`() {
        contextRunner
            .withAllowBeanDefinitionOverriding(true)
            .withBean("promptTemplateResolver", String::class.java, anotherModulesBean)
            .withBean("documentResolver", String::class.java, anotherModulesBean)
            .withBean("resultMapper", String::class.java, anotherModulesBean)
            .run { context ->
                assertThat(context).hasSingleBean(ClaudePromptTemplateResolver::class.java)
                assertThat(context).hasSingleBean(ClaudeDocumentResolver::class.java)
                assertThat(context).hasSingleBean(ClaudeService::class.java)
            }
    }

    @Test
    fun `should let an application supply a prompt resolver of its own`() {
        val ownResolver: ClaudePromptTemplateResolver = mock()

        contextRunner
            .withBean(
                "claudePromptTemplateResolver",
                ClaudePromptTemplateResolver::class.java,
                Supplier { ownResolver },
            ).run { context ->
                assertThat(context).hasSingleBean(ClaudePromptTemplateResolver::class.java)
                assertThat(context.getBean(ClaudePromptTemplateResolver::class.java)).isSameAs(ownResolver)
            }
    }

    /** What a Valtimo application brings; the plugin declares all of these `compileOnly`. */
    @Configuration(proxyBeanMethods = false)
    internal class ValtimoBeans {
        @Bean
        fun valueResolverService(): ValueResolverService = mock()

        @Bean
        fun temporaryResourceStorageService(): TemporaryResourceStorageService = mock()

        @Bean
        fun pluginService(): PluginService = mock()

        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()

        /** Spring Boot installs this in an application; it reads `10MB` as a [DataSize]. */
        @Bean(ConfigurableApplicationContext.CONVERSION_SERVICE_BEAN_NAME)
        fun conversionService(): ConversionService = ApplicationConversionService.getSharedInstance()
    }

    private companion object {
        val anotherModulesBean = Supplier { "another module's bean" }
    }
}
