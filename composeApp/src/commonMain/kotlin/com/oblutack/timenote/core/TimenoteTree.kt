package com.oblutack.timenote.core

import com.oblutack.timenote.feature_history.domain.Timenote

/** All children, grandchildren, etc. of [parentId]. Guards against cyclic parent links. */
fun descendantIds(notes: List<Timenote>, parentId: String): List<String> {
    val childrenByParent = notes.groupBy { it.parentTimenoteId }
    val result = mutableListOf<String>()
    val visited = mutableSetOf(parentId)
    val queue = ArrayDeque<String>().apply { add(parentId) }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        childrenByParent[current].orEmpty().forEach { child ->
            if (visited.add(child.id)) {
                result.add(child.id)
                queue.add(child.id)
            }
        }
    }
    return result
}
