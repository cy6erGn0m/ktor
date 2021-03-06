package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import kotlin.reflect.*

class CookieValueSessionTracker<S : Any>(val cookieSettings: SessionCookiesSettings,
                                         val cookieName: String,
                                         val serializer: SessionSerializer<S>) : SessionTracker<S> {
    override fun assign(call: ApplicationCall, session: S) {
        call.response.cookies.append(cookieSettings.toCookie(cookieName, serializer.serialize(session)))
    }

    override fun lookup(call: ApplicationCall, injectSession: (S) -> Unit, next: ApplicationCall.() -> ApplicationCallResult): ApplicationCallResult {
        val value = cookieSettings.fromCookie(call.request.cookies[cookieName])
        if (value != null) {
            injectSession(serializer.deserialize(value))
        }
        return next(call)
    }

    override fun unassign(call: ApplicationCall) {
        call.response.cookies.appendExpired(cookieName)
    }
}

class CookieValueSessionTrackerBuilder<S : Any>(val type: KClass<S>) {
    var settings = SessionCookiesSettings()
    var cookieName: String = "SESSION"
    var serializer: SessionSerializer<S> = autoSerializerOf(type)

    fun build(): SessionTracker<S> = CookieValueSessionTracker(settings, cookieName, serializer)
}

fun <S : Any> SessionConfigBuilder<S>.withCookieByValue(block: CookieValueSessionTrackerBuilder<S>.() -> Unit = {}) {
    sessionTracker = CookieValueSessionTrackerBuilder(sessionType).apply(block).build()
}
