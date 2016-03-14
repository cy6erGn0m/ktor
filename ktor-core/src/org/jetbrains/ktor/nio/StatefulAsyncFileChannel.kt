package org.jetbrains.ktor.nio

import java.io.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import java.util.concurrent.*

class StatefulAsyncFileChannel (val fc: AsynchronousFileChannel, val start: Long = 0, val endInclusive: Long = fc.size() - 1, val preventClose: Boolean = false) : AsyncReadChannel {

    constructor(fc: AsynchronousFileChannel, range: LongRange = 0L .. fc.size() - 1, preventClose: Boolean = false) : this(fc, range.start, range.endInclusive, preventClose)

    private var currentHandler: AsyncHandler? = null

    init {
        require(start >= 0L) { "start position shouldn't be negative but it is $start"}
        require(endInclusive >= start) { "endInclusive shouldn't be less than start but start = $start, endInclusive = $endInclusive" }
        require(endInclusive <= fc.size() - 1) { "endInclusive points to the position out of the file: file size = ${fc.size()}, endInclusive = $endInclusive" }
    }

    private var position = start

    val range: LongRange
        get () = start .. endInclusive

    override fun close() {
        if (!preventClose) fc.close()
    }

    private val readHandler = object : CompletionHandler<Int, ByteBuffer> {
        override fun failed(exc: Throwable, attachment: ByteBuffer) {
            currentHandler?.failed(exc)
        }

        override fun completed(rc: Int, attachment: ByteBuffer) {
            val dst = attachment

            if (rc == -1) {
                currentHandler?.successEnd()
            } else {
                position += rc
                val overRead = Math.max(0L, position - endInclusive - 1)
                if (overRead > 0) {
                    require(overRead < Int.MAX_VALUE)
                    dst.position(dst.position() - overRead.toInt())
                }
                currentHandler?.success(rc - overRead.toInt())
            }
        }
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (position > endInclusive) {
            handler.successEnd()
            return
        }

        try {
            fc.read(dst, position, dst, readHandler)
        } catch (e: Throwable) {
            handler.failed(e)
        }
    }
}

fun Path.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = Files.size(this) - 1) = StatefulAsyncFileChannel(AsynchronousFileChannel.open(this, StandardOpenOption.READ), start, endInclusive)
fun File.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = length() - 1) = toPath().asyncReadOnlyFileChannel(start, endInclusive)
