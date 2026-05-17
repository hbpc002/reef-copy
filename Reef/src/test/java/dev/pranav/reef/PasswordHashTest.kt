package dev.pranav.reef

import dev.pranav.reef.screens.hashPin
import org.junit.Assert.*
import org.junit.Test

class PasswordHashTest {

    @Test
    fun hashPin_isDeterministic() {
        val hash1 = hashPin("1234")
        val hash2 = hashPin("1234")
        assertEquals(hash1, hash2)
    }

    @Test
    fun hashPin_differentPins_differentHashes() {
        val hash1 = hashPin("1234")
        val hash2 = hashPin("5678")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun hashPin_outputLength_is64HexChars() {
        val hash = hashPin("123456")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun hashPin_worksWith4DigitPin() {
        val hash = hashPin("0000")
        assertEquals(64, hash.length)
    }

    @Test
    fun hashPin_worksWith6DigitPin() {
        val hash = hashPin("999999")
        assertEquals(64, hash.length)
    }

    @Test
    fun hashPin_worksWithAllZeros() {
        val hash = hashPin("000000")
        assertNotNull(hash)
    }

    @Test
    fun hashPin_samePinIsConsistent() {
        val pin = "8372"
        val h1 = hashPin(pin)
        val h2 = hashPin(pin)
        val h3 = hashPin(pin)
        assertEquals(h1, h2)
        assertEquals(h2, h3)
    }

    @Test
    fun hashPin_pinOrderMatters() {
        val hash1 = hashPin("1234")
        val hash2 = hashPin("4321")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun hashPin_emptyPin() {
        val hash = hashPin("")
        assertEquals(64, hash.length)
    }

    @Test
    fun hashPin_longPin() {
        val hash = hashPin("1234567890")
        assertEquals(64, hash.length)
    }
}
