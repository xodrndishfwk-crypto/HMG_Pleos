package com.example.starlog2.navigation

internal sealed interface JsonValue {
    data class ObjectValue(val values: Map<String, JsonValue>) : JsonValue
    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val value: Double) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

internal class SimpleJson private constructor(private val source: String) {
    private var index = 0

    fun parse(): JsonValue {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == source.length) { "Unexpected trailing JSON content" }
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        require(index < source.length) { "Unexpected end of JSON" }
        return when (source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.StringValue(parseString())
            't' -> parseLiteral("true", JsonValue.BooleanValue(true))
            'f' -> parseLiteral("false", JsonValue.BooleanValue(false))
            'n' -> parseLiteral("null", JsonValue.NullValue)
            else -> parseNumber()
        }
    }

    private fun parseObject(): JsonValue.ObjectValue {
        index++
        skipWhitespace()
        val values = LinkedHashMap<String, JsonValue>()
        if (consume('}')) return JsonValue.ObjectValue(values)
        while (true) {
            skipWhitespace()
            require(index < source.length && source[index] == '"') { "Expected object key" }
            val key = parseString()
            skipWhitespace()
            require(consume(':')) { "Expected colon" }
            require(values.put(key, parseValue()) == null) { "Duplicate object key" }
            skipWhitespace()
            if (consume('}')) break
            require(consume(',')) { "Expected comma" }
        }
        return JsonValue.ObjectValue(values)
    }

    private fun parseArray(): JsonValue.ArrayValue {
        index++
        skipWhitespace()
        val values = ArrayList<JsonValue>()
        if (consume(']')) return JsonValue.ArrayValue(values)
        while (true) {
            values += parseValue()
            skipWhitespace()
            if (consume(']')) break
            require(consume(',')) { "Expected comma" }
        }
        return JsonValue.ArrayValue(values)
    }

    private fun parseString(): String {
        require(consume('"')) { "Expected string" }
        val result = StringBuilder()
        while (index < source.length) {
            val character = source[index++]
            when (character) {
                '"' -> return result.toString()
                '\\' -> {
                    require(index < source.length) { "Truncated escape" }
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> result.append(parseUnicodeEscape())
                        else -> throw IllegalArgumentException("Invalid escape")
                    }
                }
                else -> {
                    require(character.code >= 0x20) { "Control character in string" }
                    result.append(character)
                }
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun parseUnicodeEscape(): Char {
        require(index + 4 <= source.length) { "Truncated unicode escape" }
        val value = source.substring(index, index + 4).toInt(16)
        index += 4
        return value.toChar()
    }

    private fun parseNumber(): JsonValue.NumberValue {
        val start = index
        if (consume('-')) require(index < source.length) { "Invalid number" }
        if (consume('0')) {
            require(index >= source.length || !source[index].isDigit()) { "Leading zero" }
        } else {
            require(index < source.length && source[index] in '1'..'9') { "Invalid number" }
            while (index < source.length && source[index].isDigit()) index++
        }
        if (consume('.')) {
            require(index < source.length && source[index].isDigit()) { "Invalid fraction" }
            while (index < source.length && source[index].isDigit()) index++
        }
        if (index < source.length && source[index] in "eE") {
            index++
            if (index < source.length && source[index] in "+-") index++
            require(index < source.length && source[index].isDigit()) { "Invalid exponent" }
            while (index < source.length && source[index].isDigit()) index++
        }
        val value = source.substring(start, index).toDouble()
        require(value.isFinite()) { "Non-finite number" }
        return JsonValue.NumberValue(value)
    }

    private fun <T : JsonValue> parseLiteral(literal: String, value: T): T {
        require(source.regionMatches(index, literal, 0, literal.length)) { "Invalid literal" }
        index += literal.length
        return value
    }

    private fun consume(expected: Char): Boolean {
        if (index >= source.length || source[index] != expected) return false
        index++
        return true
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index] in " \t\r\n") index++
    }

    companion object {
        fun parse(source: String): JsonValue = SimpleJson(source).parse()
    }
}

internal fun JsonValue.objectValue(): Map<String, JsonValue> = (this as JsonValue.ObjectValue).values

internal fun JsonValue.arrayValue(): List<JsonValue> = (this as JsonValue.ArrayValue).values

internal fun JsonValue.stringValue(): String = (this as JsonValue.StringValue).value

internal fun JsonValue.numberValue(): Double = (this as JsonValue.NumberValue).value
