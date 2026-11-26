package com.oblutack.timenote.feature_history.domain

import com.oblutack.timenote.core.descendantIds

/** Whether a tag is on none, some or all of the selected sessions. */
enum class TagCoverage { None, Some, All }

fun tagCoverage(selected: List<Timenote>, tagId: String): TagCoverage {
    if (selected.isEmpty()) return TagCoverage.None
    val withTag = selected.count { session -> session.tags.any { it.id == tagId } }
    return when (withTag) {
        0 -> TagCoverage.None
        selected.size -> TagCoverage.All
        else -> TagCoverage.Some
    }
}

/**
 * The tags of [session] after tapping [tag] in the bulk tag sheet: when every selected session has the tag it is taken off
 * them all, otherwise it is put on all of them (the order of the other tags is kept).
 */
fun tagsAfterBulkToggle(session: Timenote, tag: TimenoteFolder, coverage: TagCoverage): List<TimenoteFolder> =
    if (coverage == TagCoverage.All) {
        session.tags.filter { it.id != tag.id }
    } else if (session.tags.any { it.id == tag.id }) {
        session.tags
    } else {
        session.tags + tag
    }

/** The bulk pin button pins everything, unless everything is pinned already: then it unpins them all. */
fun shouldPinAll(selected: List<Timenote>): Boolean = selected.any { !it.isPinned }

/** The branches (at any depth) of the sessions in [ids] that are not themselves in [ids]. */
fun branchesOutside(all: List<Timenote>, ids: Set<String>): Set<String> =
    ids.flatMap { descendantIds(all, it) }.filter { it !in ids }.toSet()

/** What a bulk delete does: the sessions to delete, and the branches that would be left behind (asked about first). */
data class BulkDelete(val ids: Set<String>, val branchCount: Int)

/** "3 sessions moved to trash", or the title for one. */
fun trashMessage(deleted: List<Timenote>, count: Int): String =
    if (count == 1 && deleted.size == 1) "\"${deleted[0].title.ifBlank { "Untitled" }}\" moved to trash" else "$count sessions moved to trash"
