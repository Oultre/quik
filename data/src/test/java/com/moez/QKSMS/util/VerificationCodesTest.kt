/*
 * Copyright (C) 2026
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QUIK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QUIK.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationCodesTest {

    @Test
    fun findsCommonFormats() {
        assertEquals("482913", VerificationCodes.find("Your Venmo code is 482913. Don't share it with anyone."))
        assertEquals("482913", VerificationCodes.find("482913 is your verification code for Instacart"))
        assertEquals("482913", VerificationCodes.find("G-482913 is your Google verification code."))
        assertEquals("4829", VerificationCodes.find("Your DoorDash login code: 4829"))
        assertEquals("48291377", VerificationCodes.find("Use 48291377 as your one-time passcode"))
        assertEquals("482913", VerificationCodes.find("Amazon OTP: 482 913"))
        assertEquals("482913", VerificationCodes.find("Your sign-in code is 482-913"))
        assertEquals("482913", VerificationCodes.find("[Chase] Never share this. Your 2FA code: 482913"))
    }

    @Test
    fun picksTheCandidateNearestTheKeyword() {
        assertEquals(
            "731044",
            VerificationCodes.find("Wells Fargo: 731044 is your verification code. Questions? Call 1-800-869-3557")
        )
        assertEquals("731044", VerificationCodes.find("Order 2231 ready. Your pickup code is 731044"))
    }

    @Test
    fun ignoresTextsThatAreNotCodes() {
        assertNull(VerificationCodes.find("Your order 48291377 has shipped"))
        assertNull(VerificationCodes.find("Practice moved to 4:30 tomorrow"))
        assertNull(VerificationCodes.find("Can you call me at 555-123-4567"))
        assertNull(VerificationCodes.find("Your PIN was changed. If this wasn't you, call us."))
    }

    @Test
    fun ignoresPromotions() {
        assertNull(VerificationCodes.find("Use code 2024 for 20% off your next order!"))
        assertNull(VerificationCodes.find("Promo code 5521 expires tonight"))
    }

    @Test
    fun ignoresNumbersThatOnlyLookLikeCodes() {
        assertNull(VerificationCodes.find("Your code: call (555) 123-4567"))
        assertNull(VerificationCodes.find("Verify your payment of $1234 at https://x.co/r/839201"))
        assertNull(VerificationCodes.find("Login from 2001:db8::1 at 10:42"))
        assertNull(VerificationCodes.find("Verification failed for card 4111111111111111"))
        assertNull(VerificationCodes.find("Your verification balance is 1234.56"))
    }

}
