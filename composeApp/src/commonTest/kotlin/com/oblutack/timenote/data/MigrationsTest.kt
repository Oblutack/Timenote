package com.oblutack.timenote.data

import com.oblutack.timenote.data.database.ALL_MIGRATIONS
import com.oblutack.timenote.data.database.DATABASE_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards against the worst local-first mistake: shipping a new schema version without a migration,
 * which would leave existing installs unable to open (or lose) their data.
 */
class MigrationsTest {
    @Test fun everyVersionStepHasExactlyOneMigration() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        val expected = (1 until DATABASE_VERSION).map { it to it + 1 }
        assertEquals(expected, steps.sortedBy { it.first })
    }
}
