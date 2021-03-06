package org.jetbrains.ktor.host

interface ApplicationHostConfig {
    /**
     * The network interface this host binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces.
     */
    val host: String

    /**
     * The port this application should be bound to.
     */
    val port: Int

    /**
     * Specifies if host should enable its async support, if any
     */
    val async: Boolean

}

inline fun applicationHostConfig(builder: ApplicationHostConfigBuilder.() -> Unit) = ApplicationHostConfigBuilder().apply(builder)

class ApplicationHostConfigBuilder : ApplicationHostConfig {
    override var host: String = "0.0.0.0"
    override var port: Int = 80
    override var async: Boolean = false
}