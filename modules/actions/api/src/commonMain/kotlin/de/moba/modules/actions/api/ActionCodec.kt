package de.moba.modules.actions.api

/** Kurzer Text fuer Listen, z. B. "Rückmeldung: 1.3, belegt". */
fun Condition.describe(): String {
    val body = if (type == ConditionType.OPERATOR) {
        if (param("op") == "oder") "oder" else "und"
    } else {
        describeParams(type.label, type.params, ::param)
    }
    return if (negate) "nicht $body" else body
}

fun Command.describe(): String = describeParams(type.label, type.params, ::param)

private fun describeParams(label: String, specs: List<ParamSpec>, value: (String) -> String): String {
    val parts = specs.mapNotNull { spec ->
        val v = value(spec.key)
        when {
            v.isBlank() -> if (spec.optional) null else "${spec.label} ?"
            spec.kind == ParamKind.CHOICE -> spec.options.firstOrNull { it.first == v }?.second ?: v
            spec.kind == ParamKind.LOCO -> "Lok $v"
            spec.kind == ParamKind.FEEDBACK -> v
            spec.kind == ParamKind.ROUTE || spec.kind == ParamKind.AUTOMATION -> v
            spec.kind == ParamKind.TEXT -> "„$v“"
            else -> "${spec.label} $v"
        }
    }
    return if (parts.isEmpty()) label else "$label: ${parts.joinToString(", ")}"
}

/**
 * Textformat, eine Zeile je Eintrag, Felder durch Tabulator getrennt:
 *
 * ```
 * # aktionen v1
 * AK  <id> <name> <aktiv 0/1>
 * B   <aktion-id> <tiefe> <art> <nicht 0/1> <parameter>
 * C   <aktion-id> <art> <parameter>
 * T   <lokadresse> <vorrat %>
 * ```
 * Bedingungen stehen in Baumreihenfolge; die Tiefe sagt, unter welchem Operator sie haengen (0 = Wurzel).
 * Parameter sind "schluessel=wert" mit "|" getrennt; "%", "=", "|", Tab und Zeilenumbruch werden maskiert.
 */
object ActionCodec {
    private const val HEADER = "# aktionen v1"

    fun encode(book: ActionBook): String = buildString {
        appendLine(HEADER)
        for (r in book.rules) {
            line("AK", r.id, escape(r.name), if (r.enabled) "1" else "0")
            fun tree(c: Condition, depth: Int) {
                line("B", r.id, depth, c.type.name, if (c.negate) "1" else "0", params(c.params))
                c.children.forEach { tree(it, depth + 1) }
            }
            tree(r.condition, 0)
            for (c in r.commands) line("C", r.id, c.type.name, params(c.params))
        }
        for ((address, percent) in book.fuel.toSortedMap()) line("T", address, (kotlin.math.round(percent * 10) / 10).toString())
    }

    fun decode(text: String): ActionBook {
        val rules = linkedMapOf<String, ActionRule>()
        // Je Aktion der Pfad der offenen Operatoren als veraenderliche Knoten
        class Node(val c: Condition, val children: MutableList<Node> = mutableListOf())
        val roots = mutableMapOf<String, Node>()
        val stacks = mutableMapOf<String, MutableList<Node>>()
        val fuel = mutableMapOf<Int, Double>()
        for (raw in text.lineSequence()) {
            if (raw.isBlank() || raw.startsWith("#")) continue
            val f = raw.split('\t')
            fun at(i: Int) = f.getOrElse(i) { "" }
            runCatching {
                when (f[0]) {
                    "AK" -> rules[at(1)] = ActionRule(at(1), unescape(at(2)), at(3) != "0")
                    "B" -> {
                        val id = at(1)
                        if (id !in rules) return@runCatching
                        val depth = at(2).toInt()
                        val node = Node(Condition(ConditionType.valueOf(at(3)), parseParams(at(5)), at(4) == "1"))
                        val stack = stacks.getOrPut(id) { mutableListOf() }
                        if (depth == 0) {
                            if (id in roots) return@runCatching
                            roots[id] = node
                            stack.clear()
                            stack += node
                        } else {
                            if (depth > stack.size) return@runCatching
                            while (stack.size > depth) stack.removeAt(stack.lastIndex)
                            val parent = stack.last()
                            if (parent.c.type != ConditionType.OPERATOR) return@runCatching
                            parent.children += node
                            stack += node
                        }
                    }
                    "C" -> {
                        val rule = rules[at(1)] ?: return@runCatching
                        rules[at(1)] = rule.copy(commands = rule.commands + Command(CommandType.valueOf(at(2)), parseParams(at(3))))
                    }
                    "T" -> fuel[at(1).toInt()] = at(2).toDouble().coerceIn(0.0, 100.0)
                }
            }
        }
        fun build(n: Node): Condition = n.c.copy(children = n.children.map(::build))
        return ActionBook(
            rules.values.map { r -> roots[r.id]?.let { r.copy(condition = build(it)) } ?: r },
            fuel,
        )
    }

    private fun StringBuilder.line(vararg fields: Any?) = appendLine(fields.joinToString("\t"))

    private fun params(p: Map<String, String>) =
        p.entries.filter { it.value.isNotEmpty() }.sortedBy { it.key }.joinToString("|") { "${escape(it.key)}=${escape(it.value)}" }

    private fun parseParams(text: String): Map<String, String> = text.split('|').filter { '=' in it }.associate {
        unescape(it.substringBefore('=')) to unescape(it.substringAfter('='))
    }

    private val ESCAPED = mapOf('%' to "%25", '=' to "%3D", '|' to "%7C", '\t' to "%09", '\n' to "%0A", '\r' to "%0D")

    private fun escape(s: String) = buildString { s.forEach { append(ESCAPED[it] ?: it) } }

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val code = if (s[i] == '%' && i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (code != null) {
                append(code.toChar())
                i += 3
            } else {
                append(s[i])
                i++
            }
        }
    }
}
