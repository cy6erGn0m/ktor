package org.jetbrains.ktor.components

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.routing.*
import kotlin.system.*

public open class ComponentApplication(config: ApplicationConfig) : Application(config) {
    val container = StorageComponentContainer("Application")
    val routing = install(Routing)
    val log = config.log.fork("Components")

    init {
        val componentClassLoader = Thread.currentThread().contextClassLoader

        container.registerInstance(this)
        container.registerInstance(config)
        // TODO: instead of registering log itself, register component resolver, that can fork log for each component
        container.registerInstance(config.log)
        container.registerInstance(componentClassLoader)
        container.registerInstance(routing)

        val introspectionTime = measureTimeMillis {
            componentClassLoader
                    .scanForClasses("")
                    .filter { it.getAnnotation(Component::class.java) != null }
                    .forEach { container.registerSingleton(it) }
        }
        log.info("Introspection took $introspectionTime ms")

        val compositionTime = measureTimeMillis {
            container.compose()
        }
        log.info("Composition took $compositionTime ms")
    }

    override fun dispose() {
        super.dispose()
        container.close()
    }

    fun routing(body: RoutingEntry.() -> Unit) = routing.apply(body)
}

@Retention(AnnotationRetention.RUNTIME)
annotation public class Component
