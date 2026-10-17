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

import dev.octoshrimpy.quik.manager.AlarmManager
import dev.octoshrimpy.quik.repository.ConversationRepository
import io.reactivex.Flowable
import javax.inject.Inject

/**
 * Backchannel: sets a "Remind me" reminder on conversations, or clears it when [Params.at] is 0
 */
class SetReminder @Inject constructor(
    private val alarmManager: AlarmManager,
    private val conversationRepo: ConversationRepository
) : Interactor<SetReminder.Params>() {

    data class Params(val threadIds: Collection<Long>, val at: Long)

    override fun buildObservable(params: Params): Flowable<*> {
        return Flowable.just(params)
            .doOnNext { (threadIds, at) -> conversationRepo.setReminder(threadIds, at) }
            .doOnNext { (threadIds, at) ->
                threadIds.forEach { threadId ->
                    val intent = alarmManager.getReminderIntent(threadId)
                    when (at) {
                        0L -> alarmManager.cancelAlarm(intent)
                        else -> alarmManager.setAlarm(at, intent)
                    }
                }
            }
    }

}
