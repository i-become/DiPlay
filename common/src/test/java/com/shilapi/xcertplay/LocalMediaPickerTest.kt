package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LocalMediaPickerTest {
    @Test fun imageAndVideoExtensionsAreFilteredByRequestedMediaType() {
        assertTrue(acceptsMediaFile("cover.jpg", video = false))
        assertTrue(acceptsMediaFile("cover.webp", video = false))
        assertFalse(acceptsMediaFile("cover.mp4", video = false))
        assertTrue(acceptsMediaFile("clip.mp4", video = true))
        assertTrue(acceptsMediaFile("clip.mkv", video = true))
        assertFalse(acceptsMediaFile("clip.png", video = true))
        assertFalse(acceptsMediaFile("notes.txt", video = false))
    }
}
