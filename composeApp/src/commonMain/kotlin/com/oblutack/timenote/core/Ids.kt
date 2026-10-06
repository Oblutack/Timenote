package com.oblutack.timenote.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A new random identifier for a note, folder, tag or timeline event.
 *
 * UUIDs cannot collide between devices, which matters once the same data is synced across several of
 * them. Identifiers created by older versions ("<millis>_<n>", plain millis) stay valid and are never rewritten.
 */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()
