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
import com.ritense.valtimoplugins.claude.domain.ClaudeResultMapping
import com.ritense.valtimoplugins.claude.service.ClaudeResultMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ClaudeResultMapperTest : BaseTest() {
    private val mapper = ClaudeResultMapper(ObjectMapper())

    @Test
    fun `should map pointers onto their targets, keeping scalar types`() {
        val result =
            mapper.map(
                listOf(
                    ClaudeResultMapping("/urgency", "pv:urgency"),
                    ClaudeResultMapping("/score", "doc:/zaak/score"),
                    ClaudeResultMapping("/blocking", "pv:blocking"),
                ),
                """{"urgency":"high","score":7,"blocking":true}""",
            )

        assertEquals("high", result.values["pv:urgency"])
        assertEquals(7, result.values["doc:/zaak/score"])
        assertEquals(true, result.values["pv:blocking"])
        assertNull(result.error)
    }

    @Test
    fun `should accept a source without a leading slash`() {
        val result = mapper.map(listOf(ClaudeResultMapping("urgency", "pv:urgency")), """{"urgency":"low"}""")

        assertEquals("low", result.values["pv:urgency"])
    }

    @Test
    fun `should read through a json fence`() {
        val result =
            mapper.map(
                listOf(ClaudeResultMapping("/urgency", "pv:urgency")),
                "```json\n{\"urgency\":\"high\"}\n```",
            )

        assertEquals("high", result.values["pv:urgency"])
    }

    @Test
    fun `should write nested objects as json text`() {
        val result =
            mapper.map(
                listOf(ClaudeResultMapping("/address", "doc:/zaak/adres")),
                """{"address":{"street":"Kade","number":7}}""",
            )

        assertEquals("""{"street":"Kade","number":7}""", result.values["doc:/zaak/adres"])
    }

    @Test
    fun `should report a missing field instead of throwing`() {
        val result =
            mapper.map(
                listOf(
                    ClaudeResultMapping("/urgency", "pv:urgency"),
                    ClaudeResultMapping("/missing", "pv:missing"),
                ),
                """{"urgency":"high"}""",
            )

        assertEquals("high", result.values["pv:urgency"])
        assertEquals(1, result.values.size)
        assertTrue(result.error!!.contains("/missing"))
    }

    @Test
    fun `should report an answer that is not json`() {
        val result = mapper.map(listOf(ClaudeResultMapping("/urgency", "pv:urgency")), "It seems rather urgent.")

        assertTrue(result.isEmpty)
        assertNotNull(result.error)
    }

    @Test
    fun `should reject a target that is not a value resolver expression`() {
        val result = mapper.map(listOf(ClaudeResultMapping("/urgency", "urgency")), """{"urgency":"high"}""")

        assertTrue(result.isEmpty)
        assertTrue(result.error!!.contains("urgency"))
    }

    @Test
    fun `should do nothing without mappings`() {
        assertTrue(mapper.map(null, """{"urgency":"high"}""").isEmpty)
        assertTrue(mapper.map(emptyList(), """{"urgency":"high"}""").isEmpty)
        assertNull(mapper.map(emptyList(), "not json").error)
    }

    @Test
    fun `should drop a half filled row`() {
        val result = mapper.map(listOf(ClaudeResultMapping("/urgency", " ")), """{"urgency":"high"}""")

        assertTrue(result.isEmpty)
        assertNull(result.error)
    }
}
