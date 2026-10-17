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
import dev.octoshrimpy.quik.model.Message
import io.reactivex.Observable

interface SyncRepository {

    sealed class SyncProgress {
        object Idle : SyncProgress()
        data class Running(val max: Int, val progress: Int, val indeterminate: Boolean) : SyncProgress()
        data class ParsingEmojis(val max: Int, val progress: Int, val indeterminate: Boolean) : SyncProgress()
    }

    val syncProgress: Observable<SyncProgress>

    fun syncMessages()

    fun syncMessage(uri: Uri, messageId: Long = 0): Message?

    /**
     * Backchannel: copies in received messages, dated between [from] and [to], that are in the
     * system's SMS/MMS store but missing from ours. Receiving writes to the system store first, so
     * a receive that gets interrupted (the app killed by an ANR, say) leaves exactly this behind.
     * Returns the messages that were recovered
     */
    fun recoverMissedMessages(from: Long, to: Long): List<Message>

    fun syncContacts()

}
