package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

/**
 * Builds a route to match specified [path]
 */
fun RoutingEntry.route(path: String, build: RoutingEntry.() -> Unit) = createRoute(path).build()

/**
 * Builds a route to match specified [method] and [path]
 */
fun RoutingEntry.route(method: HttpMethod, path: String, build: RoutingEntry.() -> Unit) {
    val selector = HttpMethodRoutingSelector(method)
    select(selector).route(path, build)
}

/**
 * Builds a route to match specified [method]
 */
fun RoutingEntry.method(method: HttpMethod, body: RoutingEntry.() -> Unit) {
    val selector = HttpMethodRoutingSelector(method)
    select(selector).body()
}

/**
 * Builds a route to match parameter with specified [name] and [value]
 */
fun RoutingEntry.param(name: String, value: String, build: RoutingEntry.() -> Unit) {
    val selector = ConstantParameterRoutingSelector(name, value)
    select(selector).build()
}

/**
 * Builds a route to match parameter with specified [name]
 */
fun RoutingEntry.param(name: String, build: RoutingEntry.() -> Unit) {
    val selector = ParameterRoutingSelector(name)
    select(selector).build()
}

/**
 * Builds a route to match header with specified [name] and [value]
 */
fun RoutingEntry.header(name: String, value: String, build: RoutingEntry.() -> Unit) {
    val selector = HttpHeaderRoutingSelector(name, value)
    select(selector).build()
}

/**
 * Builds a route to match requests with specified [contentType]
 */
fun RoutingEntry.contentType(contentType: ContentType, build: RoutingEntry.() -> Unit) {
    header("Accept", "${contentType.contentType}/${contentType.contentSubtype}", build)
}

/**
 * Builds a route to match `GET` requests with specified [path]
 */
fun RoutingEntry.get(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Get, path) { handle(body) }
}

/**
 * Builds a route to match `POST` requests with specified [path]
 */
fun RoutingEntry.post(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Post, path) { handle(body) }
}

/**
 * Builds a route to match `HEAD` requests with specified [path]
 */
fun RoutingEntry.head(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Head, path) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests with specified [path]
 */
fun RoutingEntry.put(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Put, path) { handle(body) }
}

/**
 * Builds a route to match `DELETE` requests with specified [path]
 */
fun RoutingEntry.delete(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Delete, path) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests with specified [path]
 */
fun RoutingEntry.options(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Options, path) { handle(body) }
}

/**
 * Create a routing entry for specified path
 */
public fun RoutingEntry.createRoute(path: String): RoutingEntry {
    val parts = RoutingPath.parse(path).parts
    var current: RoutingEntry = this;
    for (part in parts) {
        val selector = when (part.kind) {
            RoutingPathSegmentKind.TailCard -> UriPartTailcardRoutingSelector(part.value)
            RoutingPathSegmentKind.Parameter -> when {
                part.optional -> UriPartOptionalParameterRoutingSelector(part.value)
                else -> UriPartParameterRoutingSelector(part.value)
            }
            RoutingPathSegmentKind.Constant ->
                when {
                    part.optional -> UriPartWildcardRoutingSelector
                    else -> UriPartConstantRoutingSelector(part.value)
                }
        }
        // there may already be entry with same selector, so join them
        current = current.select(selector)
    }
    return current
}
