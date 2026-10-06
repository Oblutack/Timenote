package com.oblutack.timenote.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceMemoPathsTest {
    private val cache = "/data/user/0/app/cache"
    private val files = "/data/user/0/app/files/voice_memos"

    @Test fun fileInCacheIsRelocated() =
        assertEquals("$files/VoiceMemo_1.m4a", relocatedPath("$cache/VoiceMemo_1.m4a", cache, files))

    @Test fun trailingSlashesAreIgnored() =
        assertEquals("$files/a.m4a", relocatedPath("$cache/a.m4a", "$cache/", "$files/"))

    @Test fun fileAlreadyInNewLocationIsLeftAlone() =
        assertNull(relocatedPath("$files/a.m4a", cache, files))

    @Test fun nestedFileIsNotRelocated() =
        assertNull(relocatedPath("$cache/sub/a.m4a", cache, files))

    @Test fun similarlyNamedDirectoryIsNotRelocated() =
        assertNull(relocatedPath("/data/user/0/app/cache2/a.m4a", cache, files))

    @Test fun dummyOrEmptyPathsAreIgnored() {
        assertNull(relocatedPath("dummy1.m4a", cache, files))
        assertNull(relocatedPath("", cache, files))
    }
}
