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
import org.junit.Test

class MassTextTemplateTest {

    @Test
    fun fillsTheFirstName() {
        assertEquals("Hi Sarah, practice moved to 4:30", MassTextTemplate.fill("Hi {first}, practice moved to 4:30", "Sarah Jones"))
        assertEquals("Sarah and Sarah", MassTextTemplate.fill("{first} and {first}", "Sarah"))
        assertEquals("Hi Mom!", MassTextTemplate.fill("Hi {first}!", "  Mom  "))
    }

    @Test
    fun leavesTextWithoutThePlaceholderAlone() {
        assertEquals("Practice moved to 4:30", MassTextTemplate.fill("Practice moved to 4:30", "Sarah"))
        assertEquals("Practice moved to 4:30", MassTextTemplate.fill("Practice moved to 4:30", null))
    }

    @Test
    fun dropsThePlaceholderTidilyWithoutAName() {
        assertEquals("Hi, practice moved", MassTextTemplate.fill("Hi {first}, practice moved", null))
        assertEquals("practice moved", MassTextTemplate.fill("{first}, practice moved", null))
        assertEquals("Hey!", MassTextTemplate.fill("Hey {first}!", ""))
    }

    @Test
    fun treatsANumberAsNoName() {
        assertEquals("Hi, see you there", MassTextTemplate.fill("Hi {first}, see you there", "+1 (555) 123-4567"))
    }

}
