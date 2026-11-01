package com.deivelawink.customtvplayer

import com.deivelawink.customtvplayer.data.local.ExternalPlaylistImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImporterTest {
    @Test
    fun parsesM3uEntriesAndMetadata() {
        val database = ExternalPlaylistImporter.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://example.com/logo.png" group-title="Noticias",Canal Uno
            https://example.com/live.m3u8
            """.trimIndent()
        )

        assertEquals(1, database.channels.size)
        assertEquals("Canal Uno", database.channels.single().name)
        assertEquals("Noticias", database.channels.single().category)
        assertTrue(database.channels.single().streamUrl.endsWith(".m3u8"))
    }
}
