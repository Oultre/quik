/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.feature.gif

import dev.octoshrimpy.quik.model.GifResult

data class GifPickerState(

    /** No API key was compiled in, so searching is impossible and we say so rather than hang */
    val unconfigured: Boolean = false,

    val loading: Boolean = false,

    /** Set when a search failed outright -- offline, bad key, provider down */
    val error: Boolean = false,

    /** Set while the chosen GIF is being downloaded, before the picker closes */
    val downloading: Boolean = false,

    val results: List<GifResult> = emptyList()
)
