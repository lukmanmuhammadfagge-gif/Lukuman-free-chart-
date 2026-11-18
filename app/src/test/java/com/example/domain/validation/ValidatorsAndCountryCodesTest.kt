package com.example.domain.validation

import com.example.core.CountryCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsAndCountryCodesTest {

    @Test
    fun `nigeria +234 is default country code and formats local 080 numbers to E164`() {
        assertEquals("+234", CountryCodes.defaultNigeria.dialCode)
        assertEquals("+2348031234567", CountryCodes.formatE164("+234", "08031234567"))
        assertEquals("+2348031234567", CountryCodes.formatE164("+234", "803-123-4567"))
        assertEquals("+447911123456", CountryCodes.formatE164("+44", "07911 123456"))
    }

    @Test
    fun `username validation enforces lowercase alphanumeric and underscores`() {
        assertTrue(Validators.isValidUsername("lukuman_fagge"))
        assertTrue(Validators.isValidUsername("@Lukuman_99"))
        assertFalse(Validators.isValidUsername("ab"))
        assertFalse(Validators.isValidUsername("user name!"))
    }

    @Test
    fun `deterministic conversation id is order independent`() {
        assertEquals("alice_bob", Validators.deterministicConversationId("alice", "bob"))
        assertEquals("alice_bob", Validators.deterministicConversationId("bob", "alice"))
    }

    @Test
    fun `phone E164 validation rejects malformed strings and accepts empty or valid E164`() {
        assertTrue(Validators.isValidPhoneE164(""))
        assertTrue(Validators.isValidPhoneE164("+2348012345678"))
        assertFalse(Validators.isValidPhoneE164("08012345678"))
        assertFalse(Validators.isValidPhoneE164("+0123"))
    }
}
