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
package dev.octoshrimpy.quik.model

/**
 * Backchannel: one GIF from the search provider.
 *
 * [sendUrl] is deliberately not the full-size GIF. MMS payloads are capped in the low hundreds of
 * KB, so the repository picks the largest rendition that still fits the budget -- sending the
 * original would just be re-encoded down to mush by the attachment pipeline, or rejected outright.
 */
data class GifResult(
    val id: String,
    val previewUrl: String,
    val sendUrl: String,
    val sendSizeBytes: Long,
    val width: Int,
    val height: Int
)
