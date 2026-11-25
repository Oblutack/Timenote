package com.oblutack.timenote.sync

import com.oblutack.timenote.testutil.FakeRemoteStore
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testNote
import com.oblutack.timenote.testutil.testTag
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A newer app version may write fields this version does not know. Uploading from this version must not delete them.
 * The "newer app" is simulated by writing extra fields straight into the cloud file.
 */
class SyncCarryOverTest {

    private fun obj(text: String) = SyncJson.parseToJsonElement(text).jsonObject

    // ---------------------------------------------------------------- the rules, without any cloud

    @Test fun anUnknownTopLevelFieldIsCarriedOver() {
        val out = carryUnknownFields("""{"v":1,"id":"n1"}""", """{"v":1,"id":"n1","rating":{"value":4,"t":5,"d":"b"}}""")
        assertEquals(4, obj(out)["rating"]!!.jsonObject["value"]!!.jsonPrimitive.content.toInt())
        assertEquals("n1", obj(out)["id"]!!.jsonPrimitive.content)
    }

    @Test fun anUnknownFieldInsideAKnownFieldIsCarriedOver() {
        val out = carryUnknownFields(
            """{"fields":{"title":{"value":"A","t":1,"d":"a"}}}""",
            """{"fields":{"title":{"value":"B","t":9,"d":"b","editedWith":"voice"},"mood":{"value":3,"t":9,"d":"b"}}}"""
        )
        val fields = obj(out)["fields"]!!.jsonObject
        assertEquals("A", fields["title"]!!.jsonObject["value"]!!.jsonPrimitive.content, "the known value is ours")
        assertEquals("voice", fields["title"]!!.jsonObject["editedWith"]!!.jsonPrimitive.content)
        assertNotNull(fields["mood"])
    }

    @Test fun knownValuesAreNeverTakenFromTheCloudCopy() {
        val ours = """{"v":1,"id":"n1","createdAt":10,"fields":{"title":{"value":"Mine","t":2,"d":"a"}}}"""
        val cloud = """{"v":1,"id":"n1","createdAt":99,"fields":{"title":{"value":"Theirs","t":8,"d":"b"}}}"""
        assertEquals(ours, carryUnknownFields(ours, cloud), "nothing unknown: the text is left exactly as it was")
    }

    @Test fun timelineEventsKeepTheirUnknownFieldsMatchedById() {
        val out = carryUnknownFields(
            """{"session":{"events":[{"id":"e1","title":"x"},{"id":"e2","title":"y"}]}}""",
            """{"session":{"events":[{"id":"e2","title":"old","kind":"blocker"},{"id":"e1","title":"x","kind":"idea"},{"id":"e3","kind":"gone"}]}}"""
        )
        val events = obj(out)["session"]!!.jsonObject["events"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("e1", "e2"), events.map { it["id"]!!.jsonPrimitive.content }, "our events, in our order, none added")
        assertEquals(listOf("idea", "blocker"), events.map { it["kind"]!!.jsonPrimitive.content })
        assertEquals("y", events[1]["title"]!!.jsonPrimitive.content)
    }

    @Test fun anUnmergedMembershipIsNotAnUnknownField() {
        val out = carryUnknownFields(
            """{"tags":{"t1":{"present":true,"t":1,"d":"a"}}}""",
            """{"tags":{"t1":{"present":true,"t":1,"d":"a","why":"x"},"t2":{"present":true,"t":5,"d":"b"}}}"""
        )
        val tags = obj(out)["tags"]!!.jsonObject
        assertEquals(setOf("t1"), tags.keys, "t2 is for the normal merge to handle, not to be smuggled in")
        assertEquals("x", tags["t1"]!!.jsonObject["why"]!!.jsonPrimitive.content, "but unknown fields inside a membership are kept")
    }

    @Test fun anUnreadableOrMissingCloudCopyChangesNothing() {
        val ours = """{"v":1,"id":"n1"}"""
        assertEquals(ours, carryUnknownFields(ours, null))
        assertEquals(ours, carryUnknownFields(ours, "not json at all {"))
        assertEquals(ours, carryUnknownFields(ours, "[1,2,3]"))
    }

    @Test fun carryingIsRepeatable() {
        val ours = """{"id":"n1"}"""
        val cloud = """{"id":"n1","extra":{"a":[1,2,{"b":null}]}}"""
        val once = carryUnknownFields(ours, cloud)
        assertEquals(once, carryUnknownFields(once, cloud))
        assertEquals(obj(cloud)["extra"], obj(once)["extra"], "arrays, nulls and nesting survive untouched")
    }

    // ---------------------------------------------------------------- through the real engine

    /** What a newer app would do: put extra fields into the cloud copy of a note. */
    private suspend fun FakeRemoteStore.addFieldsToNote(id: String, vararg extra: Pair<String, JsonElement>) {
        val name = "notes/$id.json"
        val current = String(snapshot().getValue(name))
        val file = list().first { it.name == name }
        val doc = obj(current)
        val withExtra = JsonObject(doc + extra)
        upload(name, SyncJson.encodeToString(JsonObject.serializer(), withExtra).encodeToByteArray(), file.id)
    }

    private fun cloudNote(cloud: FakeRemoteStore, id: String) = obj(String(cloud.snapshot().getValue("notes/$id.json")))

    @Test fun anOlderDeviceEditingANoteKeepsTheFieldsOfANewerOne() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val device = Replica("a", backgroundScope, cloud, clock)
        device.repo.saveTimenote(testNote("n1", title = "Before"))
        device.sync()
        val rating = buildJsonObject { put("value", 4); put("t", 5_000_000); put("d", "newer-phone") }
        cloud.addFieldsToNote("n1", "rating" to rating)
        device.sync() // reads the file

        clock.tick(); device.repo.updateTimenoteTitle("n1", "After")
        device.sync()

        val note = cloudNote(cloud, "n1")
        assertEquals("After", note["fields"]!!.jsonObject["title"]!!.jsonObject["value"]!!.jsonPrimitive.content, "the edit was uploaded")
        assertEquals(rating, note["rating"], "and the newer app's field is still there")
    }

    @Test fun theFieldsSurviveEvenWhenTheEditHappensBeforeTheDeviceHasReadThem() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val device = Replica("a", backgroundScope, cloud, clock)
        device.repo.saveTimenote(testNote("n1", title = "Before"))
        device.sync()
        val label = JsonPrimitive("blocker")
        cloud.addFieldsToNote("n1", "label" to label)

        // the device edits and syncs without having seen the newer file first: the upload reads it just before replacing it
        clock.tick(); device.repo.updateTimenoteTitle("n1", "After")
        device.sync()

        assertEquals(label, cloudNote(cloud, "n1")["label"])
    }

    @Test fun twoOlderDevicesPassingANoteBetweenThemDoNotLoseTheFields() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = Replica("a", backgroundScope, cloud, clock); val b = Replica("b", backgroundScope, cloud, clock)
        a.repo.saveTimenote(testNote("n1", title = "v1"))
        a.sync(); b.sync()
        val extra = buildJsonObject { put("stars", 5) }
        cloud.addFieldsToNote("n1", "highlights" to extra)

        clock.tick(); a.repo.updateTimenoteTitle("n1", "from a"); a.sync()
        clock.tick(); b.sync(); b.repo.updateTimenoteTitle("n1", "from b"); b.sync()
        a.sync()

        assertEquals(extra, cloudNote(cloud, "n1")["highlights"])
        assertEquals(a.view(), b.view())
    }

    @Test fun foldersAndTagsKeepTheirUnknownFieldsToo() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val device = Replica("a", backgroundScope, cloud, clock)
        device.repo.saveFolder(testFolder("f1", "Writing")); device.repo.saveTag(testTag("t1", "Focus"))
        device.sync()
        for (name in listOf("folders/f1.json", "tags/t1.json")) {
            val file = cloud.list().first { it.name == name }
            val doc = obj(String(cloud.snapshot().getValue(name)))
            cloud.upload(name, SyncJson.encodeToString(JsonObject.serializer(), JsonObject(doc + ("budgetHours" to JsonPrimitive(120)))).encodeToByteArray(), file.id)
        }
        device.sync()

        clock.tick(); device.repo.saveFolder(testFolder("f1", "Writing, renamed")); device.repo.saveTag(testTag("t1", "Focus, renamed"))
        device.sync()

        assertEquals(JsonPrimitive(120), obj(String(cloud.snapshot().getValue("folders/f1.json")))["budgetHours"])
        assertEquals(JsonPrimitive(120), obj(String(cloud.snapshot().getValue("tags/t1.json")))["budgetHours"])
    }

    @Test fun aNoteWithoutUnknownFieldsIsUploadedExactlyAsBefore() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val device = Replica("a", backgroundScope, cloud, clock)
        device.repo.saveTimenote(testNote("n1", title = "One"))
        device.sync()
        val before = String(cloud.snapshot().getValue("notes/n1.json"))

        clock.tick(); device.repo.updateTimenoteTitle("n1", "Two")
        device.sync()

        val after = String(cloud.snapshot().getValue("notes/n1.json"))
        assertTrue(after.contains("\"Two\"") && !after.contains("\"One\""))
        assertEquals(setOf("v", "id", "createdAt", "fields", "tags", "voiceNotes", "session"), obj(after).keys, "no stray fields appear")
        assertEquals(obj(before).keys, obj(after).keys)
    }

    @Test fun carryingFieldsDoesNotMakeTheNoteLookChangedForever() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val device = Replica("a", backgroundScope, cloud, clock)
        device.repo.saveTimenote(testNote("n1", title = "One"))
        device.sync()
        cloud.addFieldsToNote("n1", "rating" to JsonPrimitive(3))
        clock.tick(); device.repo.updateTimenoteTitle("n1", "Two"); device.sync()

        // after the upload, syncing again must be quiet: no endless re-uploads because the cloud text has extras
        assertEquals(SyncStats(), device.syncDone())
        assertEquals(SyncStats(), device.syncDone())
        assertEquals(JsonPrimitive(3), cloudNote(cloud, "n1")["rating"], "and the extra field is still there")
    }
}
