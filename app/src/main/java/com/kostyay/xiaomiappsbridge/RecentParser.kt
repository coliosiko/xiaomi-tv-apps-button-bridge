package com.kostyay.xiaomiappsbridge

/** Android 11 dumpsys activity recents is already ordered most-recent first. */
object RecentParser {
    fun packages(dump: String): List<String> {
        val blocks = dump.split(Regex("(?m)^\\s*\\*?\\s*Recent #\\d+:")).drop(1)
        val component = Regex("(?:realActivity|origActivity)=([A-Za-z0-9._]+)/[A-Za-z0-9._$]+")
        val base = Regex("(?:cmp=)([A-Za-z0-9._]+)/[A-Za-z0-9._$]+")
        return blocks.mapNotNull { block ->
            component.find(block)?.groupValues?.get(1) ?: base.find(block)?.groupValues?.get(1)
        }.distinct()
    }
}
