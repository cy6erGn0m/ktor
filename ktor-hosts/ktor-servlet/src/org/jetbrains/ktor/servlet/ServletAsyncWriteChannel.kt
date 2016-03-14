package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.nio.*
import java.nio.*
import javax.servlet.*

class ServletAsyncWriteChannel(val asyncContext: AsyncContext, val servletOutputStream: ServletOutputStream) : AsyncWriteChannel {
    // TODO !!

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        throw UnsupportedOperationException()
    }

    override fun close() {
        throw UnsupportedOperationException()
    }
}