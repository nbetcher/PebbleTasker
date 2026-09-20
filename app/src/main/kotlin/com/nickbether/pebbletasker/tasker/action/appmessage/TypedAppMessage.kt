package com.nickbether.pebbletasker.tasker.action.appmessage

import kotlinx.serialization.json.*

object TypedAppMessage {
    fun validate(raw: String): String {
        val dict = Json.parseToJsonElement(raw) as? JsonObject ?: error("dictionary must be a JSON object")
        for ((key, tuple) in dict) {
            require(key.toUIntOrNull() != null) { "invalid AppMessage key: $key" }
            when (tuple) {
                is JsonPrimitive -> if (!tuple.isString) integer(tuple, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                is JsonObject -> {
                    require(tuple.keys == setOf("type", "value")) { "tuple requires type and value" }
                    val value = tuple.getValue("value")
                    when (tuple["type"]?.jsonPrimitive?.content) {
                        "string" -> require(value is JsonPrimitive && value.isString)
                        "int" -> integer(value, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                        "uint" -> integer(value, 0, 4294967295L)
                        "bytes" -> { require(value is JsonArray); value.forEach { integer(it, 0, 255) } }
                        else -> error("unsupported tuple type")
                    }
                }
                else -> error("unsupported tuple value")
            }
        }
        return dict.toString()
    }
    private fun integer(value: JsonElement, min: Long, max: Long) {
        require(value is JsonPrimitive && !value.isString)
        val number = value.longOrNull ?: error("tuple value must be an integer")
        require(number in min..max)
    }
}
