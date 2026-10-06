package com.oblutack.timenote.data.repository

/**
 * Remembers that the default tags were already created on this installation.
 *
 * Without it, "no tags exist" is ambiguous: it can mean a first launch, or a user who deleted every tag on
 * purpose (and must not get them back), or, once data is synced, tags that simply have not arrived yet.
 */
interface DefaultTagsState {
    suspend fun isSeeded(): Boolean
    suspend fun markSeeded()
}
