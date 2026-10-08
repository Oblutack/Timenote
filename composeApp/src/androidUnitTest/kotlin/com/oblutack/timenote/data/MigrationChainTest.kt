package com.oblutack.timenote.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.oblutack.timenote.data.database.ALL_MIGRATIONS
import com.oblutack.timenote.data.database.DATABASE_VERSION
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Upgrades a database from EVERY version the app ever shipped, running the real migrations, and checks two things:
 *  1. the result has exactly the tables and columns the current version declares (this is what Room verifies when the
 *     app opens the database; a mismatch there means a crash on every launch of an upgraded install), and
 *  2. nothing the user had is lost or altered.
 * The old shapes come from the schema files Room exported for each version (composeApp/schemas).
 */
class MigrationChainTest {

    private val schemaDir = File("schemas/com.oblutack.timenote.data.database.AppDatabase")

    private fun schema(version: Int): JsonObject =
        Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject["database"]!!.jsonObject

    private fun entities(version: Int): List<JsonObject> = schema(version)["entities"]!!.jsonArray.map { it.jsonObject }

    private fun sql(entityOrIndex: JsonObject, table: String) =
        entityOrIndex["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)

    /** A new in-memory database shaped exactly like [version]. */
    private fun databaseAt(version: Int): Connection {
        val db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { st ->
            entities(version).forEach { e ->
                val table = e["tableName"]!!.jsonPrimitive.content
                st.execute(sql(e, table))
                e["indices"]?.jsonArray?.forEach { st.execute(sql(it.jsonObject, table)) }
            }
        }
        return db
    }

    private fun migrate(db: Connection, from: Int) {
        val wrapped = JdbcConnection(db)
        ALL_MIGRATIONS.filter { it.startVersion >= from }.sortedBy { it.startVersion }.forEach { it.migrate(wrapped) }
    }

    private class Column(val name: String, val type: String, val notNull: Boolean, val pk: Int)

    private fun actualColumns(db: Connection, table: String): List<Column> =
        db.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                buildList { while (rs.next()) add(Column(rs.getString("name"), rs.getString("type").uppercase(), rs.getInt("notnull") == 1, rs.getInt("pk"))) }
            }
        }

    private fun expectedColumns(entity: JsonObject): List<Column> {
        val pkColumns = entity["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
        return entity["fields"]!!.jsonArray.map { it.jsonObject }.map { f ->
            val name = f["columnName"]!!.jsonPrimitive.content
            // Room leaves "notNull" out of the file for nullable columns
            val notNull = f["notNull"]?.jsonPrimitive?.boolean ?: false
            Column(name, f["affinity"]!!.jsonPrimitive.content.uppercase(), notNull, pkColumns.indexOf(name) + 1)
        }
    }

    private fun tableNames(db: Connection): Set<String> =
        db.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'").use { rs ->
                buildSet { while (rs.next()) add(rs.getString(1)) }
            }
        }

    private fun assertMatchesCurrentSchema(db: Connection) {
        val expected = entities(DATABASE_VERSION)
        assertEquals(expected.map { it["tableName"]!!.jsonPrimitive.content }.toSet(), tableNames(db), "the set of tables")
        expected.forEach { e ->
            val table = e["tableName"]!!.jsonPrimitive.content
            val want = expectedColumns(e).associateBy { it.name }
            val have = actualColumns(db, table).associateBy { it.name }
            assertEquals(want.keys, have.keys, "columns of $table")
            want.forEach { (name, w) ->
                val h = have.getValue(name)
                assertEquals(w.type, h.type, "type of $table.$name")
                assertEquals(w.notNull, h.notNull, "NOT NULL of $table.$name")
                assertEquals(w.pk, h.pk, "primary key position of $table.$name")
            }
        }
    }

    /** One row for every table, with simple values that satisfy its constraints, so the migration has real rows to carry over. */
    private fun fillEachTable(db: Connection, version: Int): Map<String, Map<String, Any?>> {
        val rows = mutableMapOf<String, Map<String, Any?>>()
        entities(version).forEach { e ->
            val table = e["tableName"]!!.jsonPrimitive.content
            val values = expectedColumns(e).associate { c ->
                c.name to when {
                    c.type == "TEXT" -> "value-of-${c.name}"
                    c.type == "INTEGER" -> 1_700_000_000_000L
                    c.type == "REAL" -> 1.5
                    else -> null
                }
            }
            db.prepareStatement("INSERT INTO `$table` (${values.keys.joinToString(",") { "`$it`" }}) VALUES (${values.keys.joinToString(",") { "?" }})").use { ps ->
                values.values.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                ps.executeUpdate()
            }
            rows[table] = values
        }
        return rows
    }

    private fun row(db: Connection, table: String): Map<String, Any?> =
        db.createStatement().use { st ->
            st.executeQuery("SELECT * FROM `$table`").use { rs ->
                assertTrue(rs.next(), "$table has a row")
                (1..rs.metaData.columnCount).associate { rs.metaData.getColumnName(it) to rs.getObject(it) }
            }
        }

    private fun count(db: Connection, table: String): Int =
        db.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM `$table`").use { it.next(); it.getInt(1) } }

    // ------------------------------------------------------------------ the tests

    @Test fun everyShippedVersionUpgradesToTheCurrentShape() {
        for (from in 2 until DATABASE_VERSION) {
            val db = databaseAt(from)
            migrate(db, from)
            try { assertMatchesCurrentSchema(db) } catch (e: AssertionError) { throw AssertionError("upgrading from version $from: ${e.message}", e) }
            db.close()
        }
    }

    @Test fun nothingTheUserHadIsLostOrChangedWhenUpgradingFromAnyVersion() {
        for (from in 2 until DATABASE_VERSION) {
            val db = databaseAt(from)
            val before = fillEachTable(db, from)

            migrate(db, from)

            before.forEach { (table, oldValues) ->
                if (table !in tableNames(db)) return@forEach
                assertEquals(1, count(db, table), "row count of $table after upgrading from $from")
                val after = row(db, table)
                oldValues.forEach { (column, value) ->
                    if (column in after) {
                        val now = after.getValue(column)
                        assertEquals(value.toString(), now.toString(), "$table.$column after upgrading from version $from")
                    }
                }
            }
            db.close()
        }
    }

    @Test fun upgradingFromVersion7GivesEveryItemATimeAndKeepsTheTrash() {
        val db = databaseAt(7)
        db.createStatement().use { st ->
            // two notes (one active, one in the trash), a folder in the trash, and a tag
            st.execute(
                "INSERT INTO timenotes (id,folderId,title,description,audioPath,voiceNotesJson,duration,activeSeconds,pauseSeconds,createdAt," +
                    "tagsJson,timelineEventsJson,parentTimenoteId,parentWaypointId,isPinned,isDeleted,deletedAt) VALUES " +
                    "('a',NULL,'Active','text',NULL,'[]','00:01:00',60,0,1000,'[]','[]',NULL,NULL,1,0,NULL)," +
                    "('b',NULL,'Trashed','',NULL,'[\"/old/path/memo.m4a\"]','00:02:00',120,0,2000,'[]','[]',NULL,NULL,0,1,5000)"
            )
            st.execute("INSERT INTO project_folders (id,name,description,colorLong,createdAt,isPinned,isDeleted,deletedAt) VALUES ('f','Old',NULL,-1,3000,0,1,7000)")
            st.execute("INSERT INTO tags (id,name,description,sessionCount,colorLong) VALUES ('t','Work',NULL,4,-1)")
        }

        migrate(db, 7)

        fun long(sql: String) = db.createStatement().use { it.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) } }
        assertEquals(1000L, long("SELECT updatedAt FROM timenotes WHERE id='a'"), "an active note: when it was created")
        assertEquals(5000L, long("SELECT updatedAt FROM timenotes WHERE id='b'"), "a trashed note: when it was trashed")
        assertEquals(7000L, long("SELECT updatedAt FROM project_folders WHERE id='f'"))
        assertEquals(0L, long("SELECT updatedAt FROM tags WHERE id='t'"), "tags have no creation time: older than any real edit")
        assertEquals(0L, long("SELECT isDeleted FROM tags WHERE id='t'"))
        assertEquals(1L, long("SELECT isDeleted FROM timenotes WHERE id='b'"), "the trash stays the trash")
        assertEquals(1L, long("SELECT isPinned FROM timenotes WHERE id='a'"))
        assertEquals(4L, long("SELECT sessionCount FROM tags WHERE id='t'"))
        db.createStatement().use { st ->
            st.executeQuery("SELECT voiceNotesJson FROM timenotes WHERE id='b'").use { rs ->
                rs.next(); assertEquals("[\"/old/path/memo.m4a\"]", rs.getString(1), "voice memo references are untouched (they keep working)")
            }
        }
        listOf("field_versions", "sync_state", "pending_remote_deletes", "note_conflicts").forEach { assertEquals(0, count(db, it), "$it starts empty") }
        db.close()
    }

    @Test fun theMigrationsFormAnUnbrokenChainEndingAtTheCurrentVersion() {
        val steps = ALL_MIGRATIONS.sortedBy { it.startVersion }
        steps.zipWithNext().forEach { (a, b) -> assertEquals(a.endVersion, b.startVersion) }
        assertEquals(DATABASE_VERSION, steps.last().endVersion)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// A minimal bridge so the app's real migrations (written against Room's SQLiteConnection) run on a plain JDBC database.
// Migrations only execute statements, so reading results is only needed for completeness.

private class JdbcConnection(private val db: Connection) : SQLiteConnection {
    override fun prepare(sql: String): SQLiteStatement = JdbcStatement(db.prepareStatement(sql))
    override fun close() {}
}

private class JdbcStatement(private val ps: PreparedStatement) : SQLiteStatement {
    private var executed = false
    private var rs: ResultSet? = null

    override fun bindBlob(index: Int, value: ByteArray) = ps.setBytes(index, value)
    override fun bindDouble(index: Int, value: Double) = ps.setDouble(index, value)
    override fun bindLong(index: Int, value: Long) = ps.setLong(index, value)
    override fun bindText(index: Int, value: String) = ps.setString(index, value)
    override fun bindNull(index: Int) = ps.setObject(index, null)

    override fun getBlob(index: Int): ByteArray = rs!!.getBytes(index + 1)
    override fun getDouble(index: Int): Double = rs!!.getDouble(index + 1)
    override fun getLong(index: Int): Long = rs!!.getLong(index + 1)
    override fun getText(index: Int): String = rs!!.getString(index + 1)
    override fun isNull(index: Int): Boolean { rs!!.getObject(index + 1); return rs!!.wasNull() }
    override fun getColumnCount(): Int = rs?.metaData?.columnCount ?: 0
    override fun getColumnName(index: Int): String = rs!!.metaData.getColumnName(index + 1)
    override fun getColumnType(index: Int): Int = rs!!.metaData.getColumnType(index + 1)

    override fun step(): Boolean {
        if (!executed) {
            executed = true
            rs = if (ps.execute()) ps.resultSet else null
        }
        return rs?.next() ?: false
    }

    override fun reset() { rs?.close(); rs = null; executed = false }
    override fun clearBindings() { ps.clearParameters() }
    override fun close() { rs?.close(); ps.close() }
}
