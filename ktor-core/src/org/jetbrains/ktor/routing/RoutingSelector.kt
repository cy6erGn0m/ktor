package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

data class RouteSelectorEvaluation(val succeeded: Boolean,
                                   val quality: Double,
                                   val values: ValuesMap = ValuesMap.Empty,
                                   val segmentIncrement: Int = 0) {
    companion object {
        val Failed = RouteSelectorEvaluation(false, 0.0)

        val qualityConstant = 1.0
        val qualityParameter = 0.8
        val qualityMissing = 0.5
        val qualityWildcard = 0.2
    }
}

interface RoutingSelector {
    fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation
}

data class ConstantParameterRoutingSelector(val name: String, val value: String) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (context.parameters.contains(name, value))
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant)
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "[$name = $value]"
}

data class ParameterRoutingSelector(val name: String) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val param = context.parameters.getAll(name)
        if (param != null)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, valuesOf(name to param))
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "[$name]"
}

data class OptionalParameterRoutingSelector(val name: String) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val param = context.parameters.getAll(name)
        if (param != null)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, valuesOf(name to param))
        return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityMissing)
    }

    override fun toString(): String = "[$name?]"
}

data class UriPartConstantRoutingSelector(val name: String) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.size && context.path[index] == name)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant, segmentIncrement = 1)
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "$name"
}

data class UriPartParameterRoutingSelector(val name: String) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.size) {
            val part = context.path[index]
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, valuesOf(name to listOf(part)), segmentIncrement = 1)
        }
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "{$name}"
}

data class UriPartOptionalParameterRoutingSelector(val name: String) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.size) {
            val part = context.path[index]
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityParameter, valuesOf(name to listOf(part)), segmentIncrement = 1)
        }
        return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityMissing)
    }

    override fun toString(): String = "{$name?}"
}

object UriPartWildcardRoutingSelector : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index < context.path.size) {
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityWildcard, segmentIncrement = 1)
        }
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "*"
}

data class UriPartTailcardRoutingSelector(val name: String = "") : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (index <= context.path.size) {
            val values = if (name.isEmpty()) valuesOf() else valuesOf(name to context.path.drop(index).map { it })
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityWildcard, values, segmentIncrement = context.path.size - index)
        }
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "{...}"
}

data class OrRoutingSelector(val first: RoutingSelector, val second: RoutingSelector) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val result = first.evaluate(context, index)
        if (result.succeeded)
            return result
        else
            return second.evaluate(context, index)
    }

    override fun toString(): String = "{$first | $second}"
}

data class AndRoutingSelector(val first: RoutingSelector, val second: RoutingSelector) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val result1 = first.evaluate(context, index)
        if (!result1.succeeded)
            return result1
        val result2 = second.evaluate(context, index + result1.segmentIncrement)
        if (!result2.succeeded)
            return result2
        val resultValues = ValuesMap.build {
            appendAll(result1.values)
            appendAll(result2.values)
        }
        return RouteSelectorEvaluation(true, result1.quality * result2.quality, resultValues, result1.segmentIncrement + result2.segmentIncrement)
    }

    override fun toString(): String = "{$first & $second}"
}

data class HttpMethodRoutingSelector(val method: HttpMethod) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        if (context.verb.method == method)
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant)
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "(method:${method.value})"
}

data class HttpHeaderRoutingSelector(val name: String, val value: String) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val headers = context.headers[name]
        val parsedHeaders = parseAndSortHeader(headers)
        val header = parsedHeaders.firstOrNull { it.value == value }
        if (header != null)
            return RouteSelectorEvaluation(true, header.quality)
        return RouteSelectorEvaluation.Failed
    }

    override fun toString(): String = "(header:$name = $value)"
}
