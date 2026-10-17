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
package dev.octoshrimpy.quik.interactor

import dev.octoshrimpy.quik.manager.NotificationManager
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import io.reactivex.Flowable
import javax.inject.Inject

/**
 * Backchannel: a "Remind me" reminder has come due. The conversation moves to the top of the inbox
 * and turns unread, and a notification goes out
 */
class FireReminder @Inject constructor(
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val notificationManager: NotificationManager,
    private val updateBadge: UpdateBadge
) : Interactor<Long>() {

    override fun buildObservable(params: Long): Flowable<*> {
        return Flowable.just(params)
            // A reminder cleared after its alarm was set has nothing to fire
            .filter { threadId -> conversationRepo.fireReminder(threadId) }
            .doOnNext { threadId -> messageRepo.markUnread(listOf(threadId)) }
            .doOnNext { threadId -> notificationManager.notifyReminder(threadId) }
            .flatMap { updateBadge.buildObservable(Unit) }
    }

}
