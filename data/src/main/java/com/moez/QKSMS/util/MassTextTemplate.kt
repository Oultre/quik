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

/**
 * Backchannel: personalizes a mass text, which is sent to each recipient as their own message
 */
object MassTextTemplate {

    const val FIRST_NAME = "{first}"

    private val firstNameAfterSpace = Regex("\\s+\\{first\\}")
    private val firstNameLeading = Regex("\\{first\\}[,:!]?\\s*")

    /**
     * Fills in the first word of [name], a contact's display name. Without a usable name (none, or
     * just a number), the placeholder is dropped along with the space or comma that went with it,
     * so "Hi {first}, practice moved" becomes "Hi, practice moved"
     */
    fun fill(template: String, name: String?): String {
        val first = name?.trim()?.substringBefore(' ')?.takeIf { word -> word.any(Char::isLetter) }

        return when (first) {
            null -> template.replace(firstNameAfterSpace, "").replace(firstNameLeading, "")
            else -> template.replace(FIRST_NAME, first)
        }
    }

}
