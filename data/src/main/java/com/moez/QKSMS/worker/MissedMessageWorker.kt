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
package dev.octoshrimpy.quik.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.SyncRepository
import dev.octoshrimpy.quik.worker.ReceiveSmsWorker.Companion.INPUT_DATA_KEY_MESSAGE_ID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Backchannel: catches received messages that never made it into the app. Receiving saves to the
 * system's SMS store before ours, so if it's interrupted (the app killed by an ANR, say), the text
 * is safe but we never show it or notify. This finds those and finishes receiving them.
 */
class MissedMessageWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    companion object {
        private val WORKER_TAG: String = MissedMessageWorker::class.java.simpleName

        // Each check looks back this far. Anything older was caught by an earlier check
        private val LOOKBACK = TimeUnit.DAYS.toMillis(3)

        // Messages younger than this may still be mid-receive, and recovering one of those would
        // race the receiver into saving it twice
        private val SETTLE_TIME = TimeUnit.MINUTES.toMillis(5)

        fun register(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORKER_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(MissedMessageWorker::class.java, 1, TimeUnit.HOURS)
                    .addTag(WORKER_TAG)
                    .build()
            )
        }

        /**
         * Checks right away, rather than waiting for the next hourly run
         */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORKER_TAG-now",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MissedMessageWorker>().addTag(WORKER_TAG).build()
            )
        }
    }

    @Inject lateinit var syncRepo: SyncRepository
    @Inject lateinit var conversationRepo: ConversationRepository

    override fun doWork(): Result {
        val now = System.currentTimeMillis()
        val recovered = syncRepo.recoverMissedMessages(now - LOOKBACK, now - SETTLE_TIME)
        if (recovered.isEmpty()) return Result.success()

        // Unread ones get the rest of receiving: blocking, content filters and the notification.
        // ReceiveSmsWorker works from the message id alone, so it handles recovered MMS as well
        val (unread, read) = recovered.partition { message -> !message.read }
        unread.forEach { message ->
            WorkManager.getInstance(applicationContext).enqueue(
                OneTimeWorkRequestBuilder<ReceiveSmsWorker>()
                    .setInputData(workDataOf(INPUT_DATA_KEY_MESSAGE_ID to message.id))
                    .build()
            )
        }

        // Already-read ones only need their conversations brought up to date
        conversationRepo.updateConversations(read.map { message -> message.threadId }.distinct())

        return Result.success()
    }

}
