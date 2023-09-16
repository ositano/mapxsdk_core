package com.afex.mapx

interface EventEmitter {
    /**
     * Consumes a successful event.
     *
     * @param event the event, possibly null.
     */
    fun success(event: Any?)

    /**
     * Consumes an error event.
     *
     * @param errorCode an error code String.
     * @param errorMessage a human-readable error message String, possibly null.
     * @param errorDetails error details, possibly null
     */
    fun error(errorCode: String?, errorMessage: String?, errorDetails: Any?)

    /**
     * Consumes end of stream. Ensuing calls to [.success] or [.error], if any, are ignored.
     */
    fun endOfStream()
}