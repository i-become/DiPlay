package com.shilapi.xcertplay.carlink

import android.graphics.Bitmap
import android.graphics.Color
import com.shilapi.xcertplay.ClusterCopyRegion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class CarlinkFrameCopyTest {
    @Test fun copyKeepsNativeResolutionAndSinglePixelDetails() {
        val source = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.BLACK)
        source.setPixel(1000, 300, Color.WHITE)
        val full = CarlinkFrameCopy.crop(source, ClusterCopyRegion(0f, 0f, 1f, 1f), null)
        assertEquals(1920, full.width)
        assertEquals(720, full.height)
        assertEquals(Color.WHITE, full.getPixel(1000, 300))
        assertEquals(Color.BLACK, full.getPixel(999, 300))
        val cropped = CarlinkFrameCopy.crop(source, ClusterCopyRegion(.25f, .25f, .75f, .75f), full)
        assertEquals(960, cropped.width)
        assertEquals(360, cropped.height)
        assertEquals(Color.WHITE, cropped.getPixel(520, 120))
        assertEquals(Color.BLACK, cropped.getPixel(519, 120))
        assertSame(cropped, CarlinkFrameCopy.crop(source, ClusterCopyRegion(.25f, .25f, .75f, .75f), cropped))
    }
}
