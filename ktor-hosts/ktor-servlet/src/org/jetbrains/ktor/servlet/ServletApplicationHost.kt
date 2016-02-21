package org.jetbrains.ktor.servlet

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.logging.*
import javax.servlet.annotation.*
import javax.servlet.http.*

@MultipartConfig
open class ServletApplicationHost() : HttpServlet() {
    private val loader: ApplicationLoader by lazy {
        val servletContext = servletContext
        val parameterNames = servletContext.initParameterNames.toList().filter { it.startsWith("org.jetbrains.ktor") }
        val parameters = parameterNames.associateBy({ it.removePrefix("org.jetbrains.") }, { servletContext.getInitParameter(it) })

        val config = ConfigFactory.parseMap(parameters)
        val configPath = "ktor.config"

        val combinedConfig = if (config.hasPath(configPath)) {
            val configStream = servletContext.classLoader.getResourceAsStream(config.getString(configPath))
            val loadedKtorConfig = ConfigFactory.parseReader(configStream.bufferedReader())
            config.withFallback(loadedKtorConfig)
        } else
            config

        val applicationLog = SLF4JApplicationLog("ktor.application")
        val applicationConfig = HoconApplicationConfig(combinedConfig, servletContext.classLoader, applicationLog)
        ApplicationLoader(applicationConfig)
    }

    val application: Application get() = loader.application


    public override fun destroy() {
        loader.dispose()
    }

    protected override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        response.characterEncoding = "UTF-8"
        request.characterEncoding = "UTF-8"

        try {
            val applicationRequest = ServletApplicationCall(application, request, response)
            val requestResult = application.handle(applicationRequest)
            when (requestResult) {
                ApplicationCallResult.Handled -> {
//                    applicationRequest.close()
                }
                ApplicationCallResult.Unhandled -> {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND)
                    applicationRequest.close()
                }
                ApplicationCallResult.Asynchronous -> applicationRequest.continueAsync(request.startAsync())
            }
        } catch (ex: Throwable) {
            application.config.log.error("ServletApplicationHost cannot service the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.message)
        }
    }

}
