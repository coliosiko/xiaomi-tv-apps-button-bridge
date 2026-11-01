package com.kostyay.xiaomiappsbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentParserTest {
    @Test fun xiaomiAndroid11FormatKeepsOrderAndDeduplicates() {
        val dump = """
            ACTIVITY MANAGER RECENT TASKS
              * Recent #0: Task{a}
                intent={cmp=tv.video.app/.Main}
                mActivityComponent=tv.video.app/.Main
              * Recent #1: Task{b}
                intent={cmp=tv.vpn.app/.Main}
              * Recent #2: Task{c}
                intent={cmp=tv.video.app/.Other}
        """.trimIndent()
        assertEquals(listOf("tv.video.app", "tv.vpn.app"), RecentParser.packages(dump))
    }

    @Test fun realActivityPreferredAndMalformedTasksIgnored() {
        val dump = """
            * Recent #0: Task{a}
              intent={cmp=android/.ResolverActivity}
              realActivity=tv.actual.app/.Main
            * Recent #1: Task{b}
              intent={act=android.intent.action.MAIN}
        """.trimIndent()
        assertEquals(listOf("tv.actual.app"), RecentParser.packages(dump))
        assertEquals(emptyList<String>(), RecentParser.packages("Permission Denial"))
    }
}
