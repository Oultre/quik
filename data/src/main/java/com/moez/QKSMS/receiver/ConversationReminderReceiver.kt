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
package dev.octoshrimpy.quik.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.interactor.FireReminder
import timber.log.Timber
import javax.inject.Inject

/**
 * Backchannel: the alarm for a conversation's "Remind me" reminder
 */
class ConversationReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_THREAD_ID = "threadId"
    }

    @Inject lateinit var fireReminder: FireReminder

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0).takeIf { it > 0 }
            ?: return Timber.e("didn't get a valid thread id")

        val pendingResult = goAsync()
        fireReminder.execute(threadId) { pendingResult.finish() }
    }

}
