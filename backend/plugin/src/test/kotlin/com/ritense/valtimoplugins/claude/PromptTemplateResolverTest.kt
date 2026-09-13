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
import com.ritense.valtimoplugins.claude.service.PromptTemplateResolver
import com.ritense.valueresolver.ValueResolverService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.operaton.bpm.engine.delegate.DelegateExecution
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PromptTemplateResolverTest : BaseTest() {
    private val valueResolverService: ValueResolverService = mock()
    private val execution: DelegateExecution = mock()
    private val resolver = PromptTemplateResolver(valueResolverService, ObjectMapper())

    @BeforeEach
    fun setUp() {
        whenever(execution.processInstanceId).thenReturn("process-instance-id")
        whenever(execution.id).thenReturn("execution-id")
        whenever(valueResolverService.supportsValue(any())).thenReturn(true)
    }

    @Test
    fun `should substitute placeholders`() {
        resolves(mapOf("doc:/question" to "the roof leaks", "pv:customer" to 42))

        val prompt = resolver.resolve("Assess {{doc:/question}} for customer {{pv:customer}}.", execution)

        assertEquals("Assess the roof leaks for customer 42.", prompt)
    }

    @Test
    fun `should resolve a repeated placeholder once`() {
        resolves(mapOf("pv:name" to "Ada"))

        assertEquals("Ada and Ada", resolver.resolve("{{pv:name}} and {{pv:name}}", execution))
    }

    @Test
    fun `should render an object placeholder as json`() {
        resolves(mapOf("doc:/address" to mapOf("street" to "Kade")))

        assertEquals("""Address: {"street":"Kade"}""", resolver.resolve("Address: {{doc:/address}}", execution))
    }

    @Test
    fun `should leave a prompt without placeholders alone`() {
        assertEquals("Just a question.", resolver.resolve("Just a question.", execution))
        assertNull(resolver.resolve(null, execution))
        assertEquals("", resolver.resolve("", execution))
    }

    @Test
    fun `should leave braces that are not resolver expressions alone`() {
        assertEquals(
            """Answer as {"urgency": "..."}""",
            resolver.resolve("""Answer as {"urgency": "..."}""", execution),
        )
    }

    @Test
    fun `should fail on an unknown resolver prefix`() {
        whenever(valueResolverService.supportsValue(eq("nope:x"))).thenReturn(false)
        whenever(valueResolverService.getValueResolvers()).thenReturn(listOf("pv", "doc"))

        val exception = assertThrows<IllegalArgumentException> { resolver.resolve("A {{nope:x}} b", execution) }

        assertTrue(exception.message!!.contains("nope:x"))
    }

    @Test
    fun `should fail on a placeholder that resolves to nothing`() {
        resolves(mapOf("pv:known" to "yes", "pv:unknown" to null))

        val exception =
            assertThrows<IllegalArgumentException> {
                resolver.resolve("{{pv:known}} {{pv:unknown}}", execution)
            }

        assertTrue(exception.message!!.contains("pv:unknown"))
    }

    private fun resolves(values: Map<String, Any?>) {
        whenever(valueResolverService.resolveValues(eq("process-instance-id"), eq(execution), any()))
            .thenReturn(values)
    }
}
