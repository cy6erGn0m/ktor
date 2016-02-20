package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*
import java.io.*
import java.nio.channels.*
import java.nio.file.*

abstract class BaseApplicationResponse(open val call: ApplicationCall) : ApplicationResponse {
    protected abstract val stream: Interceptable1<OutputStream.() -> Unit, Unit>
    protected abstract val status: Interceptable1<HttpStatusCode, Unit>

    protected open val send = Interceptable1<Any, ApplicationCallResult> { value ->
        sendHeaders(value)
        when (value) {
            is String -> {
                status() ?: status(HttpStatusCode.OK)
                val encoding = headers[HttpHeaders.ContentType]?.let {
                    ContentType.parse(it).parameter("charset")
                } ?: "UTF-8"
                streamText(value, encoding)
                ApplicationCallResult.Handled
            }
            is TextContent -> {
                contentType(value.contentType)
                send(value.text)
            }
            is TextErrorContent -> {
                status(value.code)
                send(TextContent(ContentType.Text.Html, "<H1>${value.code}</H1>${value.message}"))
            }
            is HttpStatusCode -> {
                status(value)
                ApplicationCallResult.Handled
            }
            is StreamContent -> {
                stream {
                    value.stream(this)
                }
                ApplicationCallResult.Handled
            }
            is URIFileContent -> {
                if (value.uri.scheme == "file") {
                    send(LocalFileContent(File(value.uri)))
                } else {
                    sendStream(value.stream())
                    ApplicationCallResult.Handled
                }
            }
            is ChannelContentProvider -> {
                sendAsyncChannel(value.channel())
                ApplicationCallResult.Handled // or async?
            }
            is LocalFileContent -> {
                send(object : ChannelContentProvider, HasVersion2, HasContentLength {
                    override fun channel() = value.file.asyncReadOnlyFileChannel()
                    override val version = LastModifiedVersion(Files.getLastModifiedTime(value.file.toPath()))
                    override val contentLength = value.file.length()
                    override val seekable = true
                })
            }
            is StreamContentProvider -> {
                sendStream(value.stream())
                ApplicationCallResult.Handled
            }
            else -> throw UnsupportedOperationException("No known way to stream value $value")
        }
    }

    protected fun sendHeaders(value: Any) {
        if (value is HasETag) {
            etag(value.etag())
        }
        if (value is HasLastModified) {
            lastModified(value.lastModified)
        }
        if (value is HasContentType && !call.request.headers.contains(HttpHeaders.Range)) {
            contentType(value.contentType)
        }
        if (value is HasContentLength && !call.request.headers.contains(HttpHeaders.Range)) {
            contentLength(value.contentLength)
        }
    }

    protected open fun sendAsyncChannel(channel: AsynchronousByteChannel) {
        stream {
            Channels.newInputStream(channel).use { it.copyTo(this) }
        }
    }

    protected open fun sendFile(file: File, position: Long, length: Long) {
        stream {
            FileChannel.open(file.toPath(), StandardOpenOption.READ).use { fc ->
                fc.transferTo(position, length, Channels.newChannel(this))
            }
        }
    }

    protected open fun sendStream(stream: InputStream) {
        stream {
            stream.use { it.copyTo(this) }
        }
    }

    override fun send(message: Any): ApplicationCallResult = send.execute(message)
    override fun interceptSend(handler: (Any, (Any) -> ApplicationCallResult) -> ApplicationCallResult) = send.intercept(handler)

    override val cookies = ResponseCookies(this)

    override fun status(value: HttpStatusCode) = status.execute(value)
    override fun interceptStatus(handler: (HttpStatusCode, (HttpStatusCode) -> Unit) -> Unit) = status.intercept(handler)

    override fun stream(body: OutputStream.() -> Unit): Unit = stream.execute(body)
    override fun interceptStream(handler: (OutputStream.() -> Unit, (OutputStream.() -> Unit) -> Unit) -> Unit) = stream.intercept(handler)
}