package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.util.*

object ConditionalHeadersInterceptor : ApplicationFeature<Unit> {
    override val name = "ConditionalHeaders"
    override val key = AttributeKey<Unit>(name)

    override fun install(application: Application, configure: Unit.() -> Unit) {
        configure(Unit)

        application.intercept { requestNext ->
            if (listOf(HttpHeaders.IfModifiedSince,
                    HttpHeaders.IfUnmodifiedSince,
                    HttpHeaders.IfMatch,
                    HttpHeaders.IfNoneMatch).any { it in request.headers }) {

                response.interceptSend { obj, next ->
                    val version = (obj as? HasVersion2)?.version

                    when (version) {
                        is EntityTagVersion -> withETag(version.etag, false) { next(obj) }
                        is LastModifiedVersion -> withLastModified(version.lastModified, false) { next(obj) }
                        else -> next(obj)
                    }
                }
            }

            requestNext()
        }
    }
}
