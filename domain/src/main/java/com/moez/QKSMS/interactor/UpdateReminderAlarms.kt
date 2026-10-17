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
 * Backchannel: alarms don't survive a reboot, so this sets them again, and fires any reminder that
 * came due while the phone was off
 */
class UpdateReminderAlarms @Inject constructor(
    private val alarmManager: AlarmManager,
    private val conversationRepo: ConversationRepository,
    private val fireReminder: FireReminder
) : Interactor<Unit>() {

    override fun buildObservable(params: Unit): Flowable<*> {
        return Flowable.just(params)
            .map { conversationRepo.getPendingReminders().toList() }
            .flatMap { reminders -> Flowable.fromIterable(reminders) }
            .doOnNext { (threadId, at) -> alarmManager.setAlarm(at, alarmManager.getReminderIntent(threadId)) }
            .filter { (_, at) -> at < System.currentTimeMillis() }
            .flatMap { (threadId, _) -> fireReminder.buildObservable(threadId) }
    }

}
