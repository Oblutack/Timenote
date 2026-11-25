package com.oblutack.timenote.core

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Asks for a permission at the moment it is needed (the first voice memo, the first session), not when the app opens.
 *
 * In both functions [then] is called once the question is settled, whether the answer was yes or no, or no question
 * was needed (already allowed, or the platform has no such permission). The caller carries on either way and deals
 * with a "no" itself (for example by explaining that voice memos need the microphone).
 */
interface PermissionAsker {
    fun microphone(then: () -> Unit)
    fun notifications(then: () -> Unit)
}

/** For platforms and previews without runtime permissions: nothing to ask. */
object NoPermissionAsker : PermissionAsker {
    override fun microphone(then: () -> Unit) = then()
    override fun notifications(then: () -> Unit) = then()
}

val LocalPermissionAsker = staticCompositionLocalOf<PermissionAsker> { NoPermissionAsker }
