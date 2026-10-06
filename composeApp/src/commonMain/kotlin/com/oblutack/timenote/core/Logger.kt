package com.oblutack.timenote.core

/** Platform logging (Logcat on Android) so failures leave a tagged, filterable trace. */
expect fun logError(tag: String, message: String, throwable: Throwable? = null)
