package com.oblutack.timenote.sync

/**
 * The merge rules (docs: "Conflict handling"). Pure functions over the sync format, so they behave the same for
 * import, for cloud sync, and on every platform that implements the format.
 *
 * Merging is symmetric ("merge(a, b) == merge(b, a)"), idempotent ("merge(a, a) == a") and associative, so devices
 * can exchange data in any order, any number of times, and still end up with the same result.
 */

/** The write with the larger time wins; equal times are decided by device id so every device picks the same one. */
private fun <T> newest(a: SyncField<T>, b: SyncField<T>): SyncField<T> =
    if (a.t != b.t) { if (a.t > b.t) a else b } else if (a.d >= b.d) a else b

private fun newest(a: SyncSetEntry, b: SyncSetEntry): SyncSetEntry =
    if (a.t != b.t) { if (a.t > b.t) a else b } else if (a.d != b.d) { if (a.d > b.d) a else b } else if (a.present || !b.present) a else b

/** Per-element merge: the union of keys, each resolved to its newest entry. Order of [a] first, then new keys of [b]. */
private fun mergeSet(a: Map<String, SyncSetEntry>, b: Map<String, SyncSetEntry>): Map<String, SyncSetEntry> {
    val result = LinkedHashMap<String, SyncSetEntry>()
    for ((key, entry) in a) result[key] = b[key]?.let { newest(entry, it) } ?: entry
    for ((key, entry) in b) if (key !in result) result[key] = entry
    return result
}

/** The recorded timeline never changes once a session ended: events are united by id, the longer record is kept. */
private fun mergeSession(a: SyncSession, b: SyncSession): SyncSession {
    // A fixed ordering decides which side is the base, so the result never depends on argument order
    fun pad(n: Int) = n.toString().padStart(10, '0')
    fun key(s: SyncSession) = "${pad(s.events.size)}|${pad(s.activeSeconds)}|${pad(s.pauseSeconds)}|${s.events.joinToString(",") { it.id }}"
    val base = if (key(b) > key(a)) b else a
    val other = if (base === a) b else a
    val known = base.events.map { it.id }.toSet()
    val extra = other.events.filter { it.id !in known }
    return base.copy(events = if (extra.isEmpty()) base.events else (base.events + extra).sortedBy { it.offsetSeconds })
}

fun mergeNotes(a: SyncNote, b: SyncNote): SyncNote {
    require(a.id == b.id) { "Cannot merge different notes" }
    return SyncNote(
        v = maxOf(a.v, b.v),
        id = a.id,
        createdAt = minOf(a.createdAt, b.createdAt),
        fields = SyncNoteFields(
            title = newest(a.fields.title, b.fields.title),
            description = newest(a.fields.description, b.fields.description),
            folderId = newest(a.fields.folderId, b.fields.folderId),
            isPinned = newest(a.fields.isPinned, b.fields.isPinned),
            deletedAt = newest(a.fields.deletedAt, b.fields.deletedAt),
            parentId = newest(a.fields.parentId, b.fields.parentId),
            parentWaypointId = newest(a.fields.parentWaypointId, b.fields.parentWaypointId)
        ),
        tags = mergeSet(a.tags, b.tags),
        voiceNotes = mergeSet(a.voiceNotes, b.voiceNotes),
        session = mergeSession(a.session, b.session)
    )
}

fun mergeFolders(a: SyncFolder, b: SyncFolder): SyncFolder {
    require(a.id == b.id) { "Cannot merge different folders" }
    return SyncFolder(
        v = maxOf(a.v, b.v),
        id = a.id,
        createdAt = minOf(a.createdAt, b.createdAt),
        fields = SyncFolderFields(
            name = newest(a.fields.name, b.fields.name),
            description = newest(a.fields.description, b.fields.description),
            color = newest(a.fields.color, b.fields.color),
            isPinned = newest(a.fields.isPinned, b.fields.isPinned),
            deletedAt = newest(a.fields.deletedAt, b.fields.deletedAt)
        )
    )
}

fun mergeTags(a: SyncTag, b: SyncTag): SyncTag {
    require(a.id == b.id) { "Cannot merge different tags" }
    return SyncTag(
        v = maxOf(a.v, b.v),
        id = a.id,
        fields = SyncTagFields(
            name = newest(a.fields.name, b.fields.name),
            description = newest(a.fields.description, b.fields.description),
            color = newest(a.fields.color, b.fields.color),
            deletedAt = newest(a.fields.deletedAt, b.fields.deletedAt)
        )
    )
}
