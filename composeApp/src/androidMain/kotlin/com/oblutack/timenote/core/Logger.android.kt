package com.oblutack.timenote.core

import android.util.Log

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    Log.e(tag, message, throwable)
}
