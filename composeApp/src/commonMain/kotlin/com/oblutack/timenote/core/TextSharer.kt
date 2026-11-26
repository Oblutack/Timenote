package com.oblutack.timenote.core

import androidx.compose.runtime.staticCompositionLocalOf

/** Hands a piece of text to the phone's share sheet (message, mail, notes app...). The platform provides it. */
interface TextSharer {
    fun share(title: String, text: String)
}

/** Used where the platform has not provided a sharer: sharing does nothing instead of crashing. */
object NoTextSharer : TextSharer {
    override fun share(title: String, text: String) = Unit
}

val LocalTextSharer = staticCompositionLocalOf<TextSharer> { NoTextSharer }
