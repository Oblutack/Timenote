package com.oblutack.timenote

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
expect fun getCurrentTimeMillis(): Long