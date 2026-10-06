package com.oblutack.timenote.core

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    println("E/$tag: $message${throwable?.let { "\n" + it.stackTraceToString() } ?: ""}")
}
