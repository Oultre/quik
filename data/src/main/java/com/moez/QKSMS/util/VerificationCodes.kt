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

import kotlin.math.abs

/**
 * Backchannel: finds the one-time verification code in a text, so its notification can offer a
 * Copy button and old codes can be cleaned up.
 *
 * Deliberately conservative, since the cleanup deletes what this matches: a text only counts when
 * it calls itself a code, and promotions ("use code 2024 for 20% off") never count.
 */
object VerificationCodes {

    private val keywords = Regex(
        "\\b(codes?|passcode|otp|one[- ]time|verif\\w*|pin|2fa|two[- ]factor|authenticat\\w*|log[- ]?in|sign[- ]?in)\\b",
        RegexOption.IGNORE_CASE
    )

    private val promotional = Regex("\\b(promo|coupon|discount)\\b|%\\s*off\\b", RegexOption.IGNORE_CASE)

    // A run of 4-8 digits, or a 6 or 8 digit code split in half by a space or dash. The guards keep
    // out phone numbers, longer digit runs, amounts, decimals, times, and anything inside a link
    private val candidates = Regex(
        "(?<![\\d$€£.,:/=&@#]|\\d[ -])(\\d{3}[ -]\\d{3}|\\d{4}[ -]\\d{4}|\\d{4,8})(?![\\d%:/]|[.,-]\\d)"
    )

    /**
     * Returns the code's digits, or null if the text isn't a verification code. When there are
     * several candidates, the one nearest a keyword wins
     */
    fun find(text: String): String? {
        val keywordPositions = keywords.findAll(text).map { match -> match.range.first }.toList()
        if (keywordPositions.isEmpty() || promotional.containsMatchIn(text)) {
            return null
        }

        return candidates.findAll(text)
            .minByOrNull { candidate ->
                keywordPositions.minOf { position -> abs(candidate.range.first - position) }
            }
            ?.value
            ?.filter(Char::isDigit)
    }

}
