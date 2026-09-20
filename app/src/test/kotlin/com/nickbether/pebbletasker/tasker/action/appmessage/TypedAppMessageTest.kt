package com.nickbether.pebbletasker.tasker.action.appmessage

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TypedAppMessageTest {
    @Test fun `numeric-looking string and empty string retain their types and values`() {
        val obj = Json.parseToJsonElement(TypedAppMessage.validate("""{"1":"0123","2":"","3":123}""")).jsonObject
        assertTrue(obj.getValue("1").jsonPrimitive.isString); assertEquals("0123",obj.getValue("1").jsonPrimitive.content)
        assertEquals("",obj.getValue("2").jsonPrimitive.content); assertFalse(obj.getValue("3").jsonPrimitive.isString)
    }
    @Test fun `typed unsigned bounds and empty byte array survive`() {
        val raw = """{"4294967295":{"type":"uint","value":4294967295},"2":{"type":"bytes","value":[]},"3":{"type":"string","value":""}}"""
        assertEquals(Json.parseToJsonElement(raw), Json.parseToJsonElement(TypedAppMessage.validate(raw)))
    }
    @Test fun `bad keys fractional numbers bool null arrays and signed overflow are rejected`() {
        listOf("""{"x":1}""", """{"1":1.5}""", """{"1":true}""", """{"1":null}""", """{"1":[]}""", """{"1":2147483648}""").forEach { raw ->
            assertTrue(raw, runCatching { TypedAppMessage.validate(raw) }.isFailure)
        }
    }
    @Test fun `typed values cannot silently coerce strings negative uint or invalid bytes`() {
        listOf("""{"1":{"type":"int","value":"1"}}""", """{"1":{"type":"uint","value":-1}}""", """{"1":{"type":"bytes","value":[256]}}""", """{"1":{"type":"bytes","value":["2"]}}""").forEach {
            assertTrue(runCatching { TypedAppMessage.validate(it) }.isFailure)
        }
    }
}
