package com.oblutack.timenote.data.repository

/** What the app offers to bring back after something was moved to the trash: a message and the sessions to restore. */
data class UndoDelete(val message: String, val ids: List<String>)
