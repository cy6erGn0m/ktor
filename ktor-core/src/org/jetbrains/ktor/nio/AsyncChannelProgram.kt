package org.jetbrains.ktor.nio

class AsyncChannelProgram<out C: AsyncReadChannel>(val source: C, val handler: AsyncHandler, private val operations: List<(C, AsyncHandler) -> Unit>) {
    private var index = 0

    fun start() {
        scheduleNext()
    }

    private val handlerNext = object : AsyncHandler {
        override fun success(count: Int) {
            throw UnsupportedOperationException()
        }

        override fun successEnd() {
            index++
            scheduleNext()
        }

        override fun failed(cause: Throwable) {
            index = -1
            handler.failed(cause)
        }
    }

    private fun scheduleNext() {
        require(index >= 0) { throw IllegalStateException() }

        if (index >= operations.size) {
            handler.successEnd()
        } else {
            operations[index](source, handlerNext)
        }
    }
}