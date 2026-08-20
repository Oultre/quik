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
package dev.octoshrimpy.quik.repository

import android.net.Uri
import dev.octoshrimpy.quik.model.GifResult

/**
 * Backchannel: GIF search. This is the only part of the app that talks to the open internet --
 * everything else goes over the carrier's MMS APN -- so it is kept behind one interface.
 */
interface GifRepository {

    /**
     * True when a provider API key was supplied at build time. Without one, the picker can only
     * tell the user to add one rather than silently returning nothing.
     */
    fun isConfigured(): Boolean

    /**
     * Trending GIFs, for the picker's initial state before anything is typed.
     * [maxSendBytes] bounds which rendition is chosen for sending.
     */
    fun trending(offset: Int, maxSendBytes: Long): List<GifResult>

    fun search(query: String, offset: Int, maxSendBytes: Long): List<GifResult>

    /**
     * Downloads a chosen GIF into the cache and returns a content Uri for it, ready to attach
     */
    fun download(gif: GifResult): Uri

}
