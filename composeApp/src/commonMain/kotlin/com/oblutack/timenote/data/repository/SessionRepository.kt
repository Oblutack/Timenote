package com.oblutack.timenote.data.repository

import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.data.database.toEntity
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.mockFolders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SessionRepository {

    private var dao: TimenoteDao? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val _timenotes = MutableStateFlow<List<Timenote>>(emptyList())
    val timenotes: StateFlow<List<Timenote>> = _timenotes.asStateFlow()

    // NEW: StateFlow for our Custom Tags!
    private val _tags = MutableStateFlow<List<TimenoteFolder>>(emptyList())
    val tags: StateFlow<List<TimenoteFolder>> = _tags.asStateFlow()

    private val _folders = MutableStateFlow<List<com.oblutack.timenote.feature_history.domain.ProjectFolder>>(emptyList())
    val folders: StateFlow<List<com.oblutack.timenote.feature_history.domain.ProjectFolder>> = _folders.asStateFlow()

    private val _deletedTimenotes = MutableStateFlow<List<Timenote>>(emptyList())
    val deletedTimenotes: StateFlow<List<Timenote>> = _deletedTimenotes.asStateFlow()

    private val _deletedFolders = MutableStateFlow<List<com.oblutack.timenote.feature_history.domain.ProjectFolder>>(emptyList())
    val deletedFolders: StateFlow<List<com.oblutack.timenote.feature_history.domain.ProjectFolder>> = _deletedFolders.asStateFlow()

    fun initialize(timenoteDao: TimenoteDao) {
        dao = timenoteDao

        // Listen to Timenotes
        coroutineScope.launch {
            timenoteDao.getAllActiveTimenotes().collect { entityList ->
                _timenotes.value = entityList.map { it.toDomain() }
            }
        }

        // Listen to Folders
        coroutineScope.launch {
            timenoteDao.getAllActiveFolders().collect { entityList ->
                _folders.value = entityList.map { it.toDomain() }
            }
        }

        // NEW: Listen to Tags
        coroutineScope.launch {
            timenoteDao.getAllTags().collect { entityList ->
                val loadedTags = entityList.map { it.toDomain() }

                // Smart UX: If the database has no tags, inject the default ones!
                if (loadedTags.isEmpty()) {
                    mockFolders.forEach { saveTag(it) }
                } else {
                    _tags.value = loadedTags
                }
            }
        }

        coroutineScope.launch {
            timenoteDao.getDeletedTimenotes().collect { entityList ->
                _deletedTimenotes.value = entityList.map { it.toDomain() }
            }
        }
        coroutineScope.launch {
            timenoteDao.getDeletedFolders().collect { entityList ->
                _deletedFolders.value = entityList.map { it.toDomain() }
            }
        }
    }

    fun saveTimenote(timenote: Timenote) {
        coroutineScope.launch {
            dao?.insertTimenote(timenote.toEntity())
        }
    }

    fun deleteTimenote(id: String) {
        coroutineScope.launch { dao?.softDeleteTimenote(id, com.oblutack.timenote.getCurrentTimeMillis()) }
    }

    // 1. Find all children and sub-children recursively
    fun getDescendantIds(parentId: String): List<String> {
        val children = _timenotes.value.filter { it.parentTimenoteId == parentId }
        val descendantIds = mutableListOf<String>()
        children.forEach { child ->
            descendantIds.add(child.id)
            descendantIds.addAll(getDescendantIds(child.id)) // Recursive call for grandchildren!
        }
        return descendantIds
    }

    // 2. Cascade Delete: Deletes the parent and ALL descendants
    fun cascadeSoftDeleteTimenote(id: String) {
        coroutineScope.launch {
            val descendants = getDescendantIds(id)
            val now = com.oblutack.timenote.getCurrentTimeMillis()
            dao?.softDeleteTimenote(id, now)
            descendants.forEach { childId -> dao?.softDeleteTimenote(childId, now) }
        }
    }

    // 3. Orphan Children: Deletes the parent, and turns children into Roots
    fun deleteAndOrphanChildren(id: String) {
        coroutineScope.launch {
            val directChildren = _timenotes.value.filter { it.parentTimenoteId == id }
            directChildren.forEach { child ->
                val orphanedChild = child.copy(parentTimenoteId = null, parentWaypointId = null)
                dao?.insertTimenote(orphanedChild.toEntity())
            }
            dao?.softDeleteTimenote(id, com.oblutack.timenote.getCurrentTimeMillis())
        }
    }
    fun getTimenoteById(id: String): Timenote? {
        return _timenotes.value.find { it.id == id }
    }

    // (If you don't have getFolderById yet, add this quick helper right next to getTimenoteById):
    fun getFolderById(id: String): com.oblutack.timenote.feature_history.domain.ProjectFolder? {
        return _folders.value.find { it.id == id }
    }

    // NEW: Save a Custom Tag to the Database
    fun saveTag(tag: TimenoteFolder) {
        coroutineScope.launch {
            dao?.insertTag(tag.toEntity())
        }
    }

    // NEW: Delete a Custom Tag
    fun deleteTag(id: String) {
        coroutineScope.launch {
            dao?.deleteTag(id)
        }
    }

    fun saveFolder(folder: com.oblutack.timenote.feature_history.domain.ProjectFolder) {
        coroutineScope.launch {
            dao?.insertFolder(folder.toEntity())
        }
    }

    fun deleteFolder(id: String) {
        coroutineScope.launch { dao?.softDeleteFolder(id, com.oblutack.timenote.getCurrentTimeMillis()) }
    }

    // NEW: Update a Timenote's Folder
    fun assignFolderToTimenote(timenoteId: String, folderId: String?) {
        coroutineScope.launch {
            val note = getTimenoteById(timenoteId)
            if (note != null) {
                // Copy the note with the new folder ID and overwrite it in the DB!
                val updatedNote = note.copy(folderId = folderId)
                dao?.insertTimenote(updatedNote.toEntity())
            }
        }
    }
    // NEW: Update a Timenote's Description inline
    fun updateTimenoteDescription(timenoteId: String, newDescription: String) {
        coroutineScope.launch {
            val note = getTimenoteById(timenoteId)
            if (note != null) {
                // Copy the note with the new text and overwrite it in the DB!
                val updatedNote = note.copy(description = newDescription)
                dao?.insertTimenote(updatedNote.toEntity())
            }
        }
    }

    // NEW: Update Title inline
    fun updateTimenoteTitle(timenoteId: String, newTitle: String) {
        coroutineScope.launch {
            val note = getTimenoteById(timenoteId)
            if (note != null) {
                val updatedNote = note.copy(title = newTitle)
                dao?.insertTimenote(updatedNote.toEntity())
            }
        }
    }

    // NEW: Update Tags inline
    fun updateTimenoteTags(timenoteId: String, newTags: List<TimenoteFolder>) {
        coroutineScope.launch {
            val note = getTimenoteById(timenoteId)
            if (note != null) {
                val updatedNote = note.copy(tags = newTags)
                dao?.insertTimenote(updatedNote.toEntity())
            }
        }
    }

    // --- TRASH BIN ACTIONS ---
    fun restoreTimenote(id: String) { coroutineScope.launch { dao?.restoreTimenote(id) } }
    fun hardDeleteTimenote(id: String) { coroutineScope.launch { dao?.hardDeleteTimenote(id) } }

    fun restoreFolder(id: String) { coroutineScope.launch { dao?.restoreFolder(id) } }
    fun hardDeleteFolder(id: String) { coroutineScope.launch { dao?.hardDeleteFolder(id) } }

    fun emptyTrash() {
        coroutineScope.launch {
            _deletedTimenotes.value.forEach { dao?.hardDeleteTimenote(it.id) }
            _deletedFolders.value.forEach { dao?.hardDeleteFolder(it.id) }
        }
    }

    fun toggleFolderPin(id: String) {
        coroutineScope.launch {
            val folder = getFolderById(id) // You might need to add getFolderById similar to getTimenoteById
            if (folder != null) dao?.updateFolderPin(id, !folder.isPinned)
        }
    }

    fun toggleTimenotePin(id: String) {
        coroutineScope.launch {
            val note = getTimenoteById(id)
            if (note != null) dao?.updateTimenotePin(id, !note.isPinned)
        }
    }

    // --- TEMPORARY DEVELOPER TOOL: INJECT DUMMY DATA ---
    // --- MASSIVE SHOWCASE DATA PAYLOAD ---
    fun injectDummyData() {
        coroutineScope.launch {
            val now = com.oblutack.timenote.getCurrentTimeMillis()
            val day = 86400000L

            // =========================================================
            // 1. Folders (Vibrant Colors, some Pinned)
            // =========================================================
            val f1 = com.oblutack.timenote.feature_history.domain.ProjectFolder("f1", "Computer Science", "System architecture & algorithms", androidx.compose.ui.graphics.Color(0xFF00E5FF), now - 60 * day, isPinned = true)
            val f2 = com.oblutack.timenote.feature_history.domain.ProjectFolder("f2", "Languages", "JLPT N3 Prep", androidx.compose.ui.graphics.Color(0xFF9C27B0), now - 60 * day)
            val f3 = com.oblutack.timenote.feature_history.domain.ProjectFolder("f3", "Fitness", "Hypertrophy block", androidx.compose.ui.graphics.Color(0xFFFF9800), now - 60 * day, isPinned = true)
            val f4 = com.oblutack.timenote.feature_history.domain.ProjectFolder("f4", "Writing", "Sci-Fi Novel Drafts", androidx.compose.ui.graphics.Color(0xFF4CAF50), now - 60 * day)
            listOf(f1, f2, f3, f4).forEach { dao?.insertFolder(it.toEntity()) }

            // =========================================================
            // 2. Tags
            // =========================================================
            val t1 = TimenoteFolder("t1", "Deep Work", null, 42, androidx.compose.ui.graphics.Color(0xFF00E5FF))
            val t2 = TimenoteFolder("t2", "Bug Fix", null, 18, androidx.compose.ui.graphics.Color(0xFFE53935))
            val t3 = TimenoteFolder("t3", "Drafting", null, 25, androidx.compose.ui.graphics.Color(0xFF4CAF50))
            val t4 = TimenoteFolder("t4", "Research", null, 30, androidx.compose.ui.graphics.Color(0xFFFFEB3B))
            listOf(t1, t2, t3, t4).forEach { dao?.insertTag(it.toEntity()) }

            // =========================================================
            // 3. HEATMAP BACKFILLER (60 Days of random activity)
            // =========================================================
            val random = kotlin.random.Random(42) // Seeded so the heatmap looks the same every time
            val folders = listOf(f1, f2, f3, f4)
            val tags = listOf(t1, t2, t3, t4)

            for (i in 1..60) {
                val sessionsToday = random.nextInt(0, 4) // 0 to 3 sessions per day
                for (s in 0 until sessionsToday) {
                    val activeSecs = random.nextInt(1800, 14400) // 30 mins to 4 hours
                    val timestamp = now - (i * day) + random.nextLong(0, day / 2)

                    val fillerNote = Timenote(
                        id = "fill_${i}_$s", folderId = folders.random(random).id,
                        title = "Focused Session", description = "Routine deep work logging.",
                        duration = "0${activeSecs/3600}:${(activeSecs%3600)/60}:00",
                        activeSeconds = activeSecs, pauseSeconds = random.nextInt(0, 1800),
                        createdAt = timestamp, tags = listOf(tags.random(random)), voiceNotes = emptyList(),
                        timelineEvents = emptyList()
                    )
                    dao?.insertTimenote(fillerNote.toEntity())
                }
            }

            // =========================================================
            // GRAPH CLUSTER 1: The Dev Project (Deep 3-Level Branching)
            // =========================================================
            val rootId = "node_root"
            val r_wp1 = "r_wp1"
            val r_wp2 = "r_wp2"

            val rootNote = Timenote(
                id = rootId, folderId = f1.id, title = "Architecting Database Core",
                description = "# Phase 1: SQLite\nSetting up the DAO interfaces. Mentioning past session: **@Database UML Planning**.\n\n_Note: Keep the relations clean._",
                duration = "03:00:00", activeSeconds = 10800, pauseSeconds = 0, createdAt = now - 5 * day,
                tags = listOf(t1), voiceNotes = listOf("dummy1.m4a", "dummy_extra.m4a"), isPinned = true,
                timelineEvents = listOf(
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("wp0", "Session Started", "00:00:00", com.oblutack.timenote.feature_timer.domain.EventType.START),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent(r_wp1, "Hit a snag on Foreign Keys", "01:15:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFFE53935), audioPath = "dummy2.m4a"),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent(r_wp2, "Researching Coroutine Flows", "02:30:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFFFFEB3B)),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("wp_end", "Session Ended", "03:00:00", com.oblutack.timenote.feature_timer.domain.EventType.END)
                )
            )

            val child1Id = "node_c1"
            val c1_wp1 = "c1_wp1"
            val child1 = Timenote(
                id = child1Id, folderId = f1.id, title = "Fixing Foreign Key Crash",
                description = "Resolved the cascading delete issue. Turns out Room needs explicit annotations.\n\nSee **@Architecting Database Core** for original schema.",
                duration = "01:20:00", activeSeconds = 3600, pauseSeconds = 1200, createdAt = now - 4 * day,
                tags = listOf(t2), voiceNotes = listOf("dummy3.m4a", "dummy4.m4a"),
                parentTimenoteId = rootId, parentWaypointId = r_wp1,
                timelineEvents = listOf(
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("c1_0", "Branched from: Hit a snag on Foreign Keys", "00:00:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFF9C27B0)),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent(c1_wp1, "Found the StackOverflow answer", "00:45:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFF00E5FF)),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("c1_p", "Paused", "00:50:00", com.oblutack.timenote.feature_timer.domain.EventType.PAUSE),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("c1_r", "Resumed (Break was 00:20:00)", "01:10:00", com.oblutack.timenote.feature_timer.domain.EventType.RESUME),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("c1_end", "Session Ended", "01:20:00", com.oblutack.timenote.feature_timer.domain.EventType.END)
                )
            )

            val child2 = Timenote(
                id = "node_c2", folderId = f1.id, title = "StateFlow Implementation",
                description = "Refactoring LiveData to StateFlow for better KMP support. Linking to **@Fixing Foreign Key Crash**.",
                duration = "02:00:00", activeSeconds = 7200, pauseSeconds = 0, createdAt = now - 3 * day,
                tags = listOf(t1), voiceNotes = emptyList(),
                parentTimenoteId = rootId, parentWaypointId = r_wp2,
                timelineEvents = listOf(
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("c2_0", "Branched from: Researching Coroutine Flows", "00:00:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFF9C27B0)),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("c2_end", "Session Ended", "02:00:00", com.oblutack.timenote.feature_timer.domain.EventType.END)
                )
            )

            val grandChild = Timenote(
                id = "node_gc1", folderId = f1.id, title = "Writing Migration Scripts",
                description = "Finalizing the v5 to v6 schema update.",
                duration = "00:30:00", activeSeconds = 1800, pauseSeconds = 0, createdAt = now - 2 * day,
                tags = listOf(t1), voiceNotes = emptyList(),
                parentTimenoteId = child1Id, parentWaypointId = c1_wp1,
                timelineEvents = listOf(
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("gc_0", "Branched from: Found the StackOverflow answer", "00:00:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFF9C27B0)),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("gc_end", "Session Ended", "00:30:00", com.oblutack.timenote.feature_timer.domain.EventType.END)
                )
            )

            // =========================================================
            // CLUSTER 2: The Novel (Complex Branching & Markdown)
            // =========================================================
            val w_rootId = "w_root"
            val w_wp1 = "w_wp1"

            val writeRoot = Timenote(
                id = w_rootId, folderId = f4.id, title = "Chapter 4: The Fall",
                description = "# Scene Block\nThe protagonist enters the facility.\n\n**Key plot points to hit:**\n- Reveal the traitor.\n- Explain the artifact.\n~~Kill off the mentor.~~ (Actually, save this for chapter 5).\n\nLinked session: **@Character Backstory: Elias**.",
                duration = "04:30:00", activeSeconds = 14400, pauseSeconds = 1800, createdAt = now - 1 * day,
                tags = listOf(t3), voiceNotes = listOf("dummy5.m4a"),
                timelineEvents = listOf(
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("w0", "Session Started", "00:00:00", com.oblutack.timenote.feature_timer.domain.EventType.START),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent(w_wp1, "Writer's Block on dialogue...", "01:00:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFFFF9800), audioPath = "dummy6.m4a"),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("w2", "Paused", "01:30:00", com.oblutack.timenote.feature_timer.domain.EventType.PAUSE),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("w3", "Resumed (Break was 00:30:00)", "02:00:00", com.oblutack.timenote.feature_timer.domain.EventType.RESUME),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("w4", "Breakthrough!", "03:15:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFF4CAF50)),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("wend", "Session Ended", "04:30:00", com.oblutack.timenote.feature_timer.domain.EventType.END)
                )
            )

            val writeChild = Timenote(
                id = "w_c1", folderId = f4.id, title = "Character Backstory: Elias",
                description = "Fleshing out his motives to fix the dialogue in **@Chapter 4: The Fall**.",
                duration = "01:00:00", activeSeconds = 3600, pauseSeconds = 0, createdAt = now,
                tags = listOf(t4), voiceNotes = emptyList(),
                parentTimenoteId = w_rootId, parentWaypointId = w_wp1,
                timelineEvents = listOf(
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("wc_0", "Branched from: Writer's Block on dialogue...", "00:00:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFF9C27B0)),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("wc_end", "Session Ended", "01:00:00", com.oblutack.timenote.feature_timer.domain.EventType.END)
                )
            )

            // =========================================================
            // STANDALONE: The Gym (Testing high pause ratios)
            // =========================================================
            val gymNote = Timenote(
                id = "g_root", folderId = f3.id, title = "Push Day (Chest/Triceps)",
                description = "Heavy compound movements today.\n\n_Felt a slight tweak in left shoulder on incline press, monitor it._",
                duration = "01:15:00", activeSeconds = 1500, pauseSeconds = 3000, createdAt = now - 12 * day, // Massive pause ratio
                tags = emptyList(), voiceNotes = listOf("dummy7.m4a", "dummy8.m4a", "dummy9.m4a", "dummy10.m4a"), isPinned = true,
                timelineEvents = listOf(
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("g0", "Session Started", "00:00:00", com.oblutack.timenote.feature_timer.domain.EventType.START),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("g1", "Hit 225lbs on Bench PR!", "00:20:00", com.oblutack.timenote.feature_timer.domain.EventType.NOTE, color = androidx.compose.ui.graphics.Color(0xFF00E5FF), audioPath = "dummy_gym.m4a"),
                    com.oblutack.timenote.feature_timer.domain.TimelineEvent("gend", "Session Ended", "01:15:00", com.oblutack.timenote.feature_timer.domain.EventType.END)
                )
            )

            // =========================================================
            // GRAPH CLUSTER 3: The Massive Interconnected Web
            // =========================================================
            fun spawnNode(id: String, title: String, desc: String, parentId: String?, daysAgo: Int, tag: TimenoteFolder): Timenote {
                return Timenote(
                    id = id, folderId = f1.id, title = title, description = desc,
                    duration = "01:30:00", activeSeconds = 5400, pauseSeconds = 0, createdAt = now - daysAgo * day,
                    tags = listOf(tag), voiceNotes = emptyList(),
                    parentTimenoteId = parentId, parentWaypointId = if (parentId != null) "wp_from_$parentId" else null,
                    timelineEvents = listOf(com.oblutack.timenote.feature_timer.domain.TimelineEvent("wp_start_$id", "Started", "00:00:00", com.oblutack.timenote.feature_timer.domain.EventType.START))
                )
            }

            // The Root
            val webRoot = spawnNode("web_root", "V1.0 App Launch", "# Final Stretch\nPlanning the release. Need to coordinate with **@Marketing Strategy** and **@Database Scaling**.", null, 20, t1).copy(isPinned = true)

            // Tier 1 (Children of Root)
            val w1_code = spawnNode("w1_code", "Database Scaling", "Checking SQLite limits. Cross-referencing **@UI Canvas Engine** for performance drops.", webRoot.id, 19, t2)
            val w1_ui = spawnNode("w1_ui", "UI Canvas Engine", "Building the Obsidian graph. Mentions **@V1.0 App Launch** specs.", webRoot.id, 18, t1)
            val w1_mktg = spawnNode("w1_mktg", "Marketing Strategy", "Prepping the Rotato videos. Waiting on **@UI Canvas Engine** to finish.", webRoot.id, 17, t4)

            // Tier 2 (Grandchildren)
            val w2_c1 = spawnNode("w2_c1", "Room Migrations", "Writing SQL scripts. Linked to **@Database Scaling**.", w1_code.id, 15, t2)
            val w2_u1 = spawnNode("w2_u1", "Bezier Curve Math", "Fixing the curved lines. Need help from **@Room Migrations** data.", w1_ui.id, 14, t1)
            val w2_u2 = spawnNode("w2_u2", "Pinch to Zoom", "Gestures are laggy. Check **@UI Canvas Engine** logs.", w1_ui.id, 13, t2)
            val w2_m1 = spawnNode("w2_m1", "App Store ASO", "Writing the description. Pulled keywords from **@Marketing Strategy**.", w1_mktg.id, 12, t3)

            // Tier 3 (Great-Grandchildren)
            val w3_c1 = spawnNode("w3_c1", "Ghost Column Crash", "Fixed the ID bug in **@Room Migrations**.", w2_c1.id, 10, t2)
            val w3_u1 = spawnNode("w3_u1", "Hitbox Math", "Clicking nodes now works. Relies on **@Bezier Curve Math**.", w2_u1.id, 9, t1)

            // Inject all Timenotes
            val customNodes = listOf(
                rootNote, child1, child2, grandChild,
                writeRoot, writeChild, gymNote,
                webRoot, w1_code, w1_ui, w1_mktg, w2_c1, w2_u1, w2_u2, w2_m1, w3_c1, w3_u1
            )

            // Save the custom clusters
            customNodes.forEach { dao?.insertTimenote(it.toEntity()) }
        }
    }

}