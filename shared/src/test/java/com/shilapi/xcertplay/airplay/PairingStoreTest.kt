package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class PairingStoreTest {
    @Test fun restoringHistoryDoesNotWriteButNewPairingsDoAndKeysStayIsolated() {
        var writes = 0
        val store = PairingStore { _, _ -> writes++ }
        val key = byteArrayOf(1, 2, 3)
        store.restore("saved", key)
        key[0] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), store.get("saved"))
        store.get("saved")!![1] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), store.get("saved"))
        assertEquals(0, writes)
        store.save("new", byteArrayOf(4, 5, 6))
        assertEquals(1, writes)
        assertArrayEquals(byteArrayOf(4, 5, 6), store.get("new"))
    }
}
