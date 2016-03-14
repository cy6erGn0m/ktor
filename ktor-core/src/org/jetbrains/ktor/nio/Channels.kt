package org.jetbrains.ktor.nio

import java.io.*
import java.nio.*
import java.util.concurrent.*

interface AsyncHandler {
    fun success(count: Int)
    fun successEnd()
    fun failed(cause: Throwable)
}

interface Channel: Closeable {
}
interface AsyncReadChannel: Channel {
    fun read(dst: ByteBuffer, handler: AsyncHandler)
}
interface AsyncWriteChannel: Channel {
    fun write(src: ByteBuffer, handler: AsyncHandler)
}

interface SeekableAsyncChannel : AsyncReadChannel {
    val position: Long
    fun seek(position: Long, handler: AsyncHandler)
}

interface ProgressListener<T> {
    fun progress(source: T)
}

fun <T> CompletableFuture<T>.asHandler(block: (Int?) -> T) = object : AsyncHandler {
    override fun success(count: Int) {
        this@asHandler.complete(block(count))
    }

    override fun successEnd() {
        this@asHandler.complete(block(null))
    }

    override fun failed(cause: Throwable) {
        this@asHandler.completeExceptionally(cause)
    }
}

fun aH_example(ch: AsyncReadChannel) {
    val bb = ByteBuffer.allocate(10)
    val f = CompletableFuture<Unit>()
    ch.read(bb, f.asHandler { Unit })
    f.get()
}

/**
 * Very similar to CompletableFuture.asHandler but can be used multiple times
 */
class BlockingAdapter {
    private val semaphore = Semaphore(0)
    private var error: Throwable? = null
    private var count: Int = -1

    val handler = object : AsyncHandler {
        override fun success(count: Int) {
            error = null
            this@BlockingAdapter.count = count
            semaphore.release()
        }

        override fun successEnd() {
            count = -1
            error = null
            semaphore.release()
        }

        override fun failed(cause: Throwable) {
            error = cause
            semaphore.release()
        }
    }

    fun await(): Int {
        count
        semaphore.acquire()
        error?.let { throw it }
        return count
    }
}

class AsyncReadChannelAdapterStream(val ch: AsyncReadChannel) : InputStream() {
    private val singleByte = ByteBuffer.allocate(1)
    private val adapter = BlockingAdapter()

    override fun read(): Int {
        singleByte.clear()
        ch.read(singleByte, adapter.handler)
        val rc = adapter.await()
        if (rc == -1) {
            return -1
        }
        return singleByte.get().toInt()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val bb = ByteBuffer.wrap(b, off, len)
        ch.read(bb, adapter.handler)
        return adapter.await()
    }

    override fun close() {
        ch.close()
    }
}

class AsyncWriteChannelAdapterStream(val ch: AsyncWriteChannel) : OutputStream() {
    private val singleByte = ByteBuffer.allocate(1)
    private val adapter = BlockingAdapter()

    @Synchronized
    override fun write(b: Int) {
        do {
            singleByte.clear()
            singleByte.put(b.toByte())
            ch.write(singleByte, adapter.handler)
        } while (adapter.await() <= 0)
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        val bb = ByteBuffer.wrap(b, off, len)

        while (bb.hasRemaining()) {
            ch.write(bb, adapter.handler)
            adapter.await()
        }
    }

    @Synchronized
    override fun close() {
        ch.close()
    }

    @Synchronized
    override fun flush() {
        // note: it is important to keep it here synchronized even empty to ensure all write operations complete
    }
}

