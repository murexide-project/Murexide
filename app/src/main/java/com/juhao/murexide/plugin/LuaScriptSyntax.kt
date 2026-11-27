package com.juhao.murexide.plugin

object LuaScriptSyntax {
    private data class Token(val text: String, val kind: Int)
    data class Placeholder(val start: Int, val end: Int, val expression: String)

    fun compile(source: String): String {
        val tokens = tokenize(source)
        val significant = tokens.indices.filter { tokens[it].kind != 0 }
        val next = significant.zipWithNext().toMap()
        val stack = ArrayDeque<Boolean>()
        return buildString {
            tokens.forEachIndexed { index, token ->
                when {
                    token.text == "(" && token.kind == 1 -> {
                        val key = next[index]
                        val equal = key?.let { next[it] }
                        val named = key != null && tokens[key].kind == 2 && equal != null && tokens[equal].text == "="
                        stack.addLast(named)
                        append(if (named) "({" else "(")
                    }
                    token.text == ")" && token.kind == 1 -> append(if (stack.removeLastOrNull() == true) "})" else ")")
                    token.kind == 3 -> {
                        val matches = placeholders(decodeString(token.text))
                        if (matches.isEmpty()) append(token.text) else {
                            append("__template(").append(token.text)
                            for (match in matches) {
                                append(", function() return (").append(compile(match.expression)).append(") end")
                            }
                            append(")")
                        }
                    }
                    else -> append(token.text)
                }
            }
        }
    }

    private fun decodeString(source: String): String {
        if (source.startsWith("[")) {
            val open = source.indexOf('[', 1)
            val closeSize = open + 1
            return source.substring(open + 1, source.length - closeSize).removePrefix("\r\n").removePrefix("\n").removePrefix("\r")
        }
        return buildString {
            var index = 1
            val end = source.length - 1
            while (index < end) {
                val current = source[index++]
                if (current != '\\') { append(current); continue }
                require(index < end) { "Lua 字符串转义未结束" }
                val escaped = source[index++]
                when (escaped) {
                    'a' -> append('\u0007')
                    'b' -> append('\b')
                    'f' -> append('\u000c')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'v' -> append('\u000b')
                    '\\', '\'', '"' -> append(escaped)
                    '\n' -> append('\n')
                    '\r' -> { if (source.getOrNull(index) == '\n') index++; append('\n') }
                    'z' -> while (index < end && source[index].isWhitespace()) index++
                    'x' -> {
                        require(index + 2 <= end) { "Lua 十六进制转义不完整" }
                        append(source.substring(index, index + 2).toInt(16).toChar())
                        index += 2
                    }
                    'u' -> {
                        require(source.getOrNull(index) == '{') { "Lua Unicode 转义无效" }
                        val close = source.indexOf('}', ++index)
                        require(close in index until end) { "Lua Unicode 转义未结束" }
                        appendCodePoint(source.substring(index, close).toInt(16))
                        index = close + 1
                    }
                    in '0'..'9' -> {
                        val start = index - 1
                        while (index < end && index - start < 3 && source[index] in '0'..'9') index++
                        val code = source.substring(start, index).toInt()
                        require(code <= 255) { "Lua 数字转义超出范围" }
                        append(code.toChar())
                    }
                    else -> error("Lua 字符串包含无效转义")
                }
            }
        }
    }

    fun placeholders(text: String): List<Placeholder> {
        val result = mutableListOf<Placeholder>()
        var index = 0
        while (index < text.length - 1) {
            if (text[index] != '$' || text[index + 1] != '{') { index++; continue }
            val start = index
            index += 2
            val body = index
            var depth = 1
            while (index < text.length && depth > 0) {
                val c = text[index]
                if (c == '\'' || c == '"') {
                    val quote = c
                    index++
                    while (index < text.length) {
                        if (text[index] == '\\') index += 2
                        else if (text[index++] == quote) break
                    }
                    continue
                }
                val longEnd = longStringEnd(text, index)
                if (longEnd != null) { index = longEnd; continue }
                when (c) { '{' -> depth++; '}' -> depth-- }
                index++
            }
            require(depth == 0) { "未闭合的模板表达式" }
            result += Placeholder(start, index, text.substring(body, index - 1))
        }
        return result
    }

    private fun longStringEnd(text: String, start: Int): Int? {
        if (text.getOrNull(start) != '[') return null
        var index = start + 1
        while (text.getOrNull(index) == '=') index++
        if (text.getOrNull(index) != '[') return null
        val close = "]" + "=".repeat(index - start - 1) + "]"
        val end = text.indexOf(close, index + 1)
        require(end >= 0) { "未闭合的 Lua 长字符串" }
        return end + close.length
    }

    private fun tokenize(source: String): List<Token> {
        val result = mutableListOf<Token>()
        var index = 0
        while (index < source.length) {
            val start = index
            val c = source[index]
            val kind = when {
                c.isWhitespace() -> {
                    while (index < source.length && source[index].isWhitespace()) index++
                    0
                }
                source.startsWith("--", index) -> {
                    index = longStringEnd(source, index + 2) ?: source.indexOf('\n', index).let { if (it < 0) source.length else it }
                    0
                }
                c == '\'' || c == '"' -> {
                    index++
                    while (index < source.length) {
                        if (source[index] == '\\') index += 2
                        else if (source[index++] == c) break
                    }
                    index = index.coerceAtMost(source.length)
                    3
                }
                c == '[' && longStringEnd(source, index) != null -> {
                    index = longStringEnd(source, index)!!
                    3
                }
                c.isLetter() || c == '_' -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
                    2
                }
                else -> {
                    index++
                    if (index < source.length && source.substring(start, index + 1) in setOf("==", "~=", "<=", ">=", "..", "::", "//", "<<", ">>")) index++
                    1
                }
            }
            result += Token(source.substring(start, index), kind)
        }
        return result
    }
}
