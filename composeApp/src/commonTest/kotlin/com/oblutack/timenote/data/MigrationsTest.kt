package com.oblutack.timenote.data

import com.oblutack.timenote.data.database.ALL_MIGRATIONS
import com.oblutack.timenote.data.database.DATABASE_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards against the worst local-first mistake: shipping a new schema version without a migration,
 * which would leave existing installs unable to open (or lose) their data.
 *
 * Version 1 only existed in development builds and is reset by fallbackToDestructiveMigrationFrom
 * (see DatabaseBuilder.kt), so the supported chain starts at version 2.
 */
class MigrationsTest {
    private val firstSupportedVersion = 2

    @Test fun everySupportedVersionStepHasExactlyOneMigration() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        val expected = (firstSupportedVersion until DATABASE_VERSION).map { it to it + 1 }
        assertEquals(expected, steps.sortedBy { it.first })
    }

    @Test fun versionOneIsNotMigratedSoItCanBeResetInstead() {
        assertTrue(ALL_MIGRATIONS.none { it.startVersion == 1 })
    }
}
