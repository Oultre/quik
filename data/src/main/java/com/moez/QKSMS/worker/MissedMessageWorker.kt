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

import android.content.ContentUris
import android.content.Context
import android.provider.BaseColumns
import android.provider.Telephony
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.android.mms.MmsConfig
import com.android.mms.transaction.DownloadManager
import com.klinker.android.send_message.Utils
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.SyncRepository
import dev.octoshrimpy.quik.util.tryOrNull
import dev.octoshrimpy.quik.worker.ReceiveSmsWorker.Companion.INPUT_DATA_KEY_MESSAGE_ID
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Backchannel: catches received messages that never made it into the app. Receiving saves to the
 * system's SMS store before ours, so if it's interrupted (the app killed by an ANR, say), the text
 * is safe but we never show it or notify. This finds those and finishes receiving them.
 *
 * A group or picture message arrives as a notice that it's waiting at the carrier, then has to be
 * downloaded. When that download fails, the notice stays behind in the system's store, so this
 * also downloads those again.
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

        // A waiting message's first download gets this long to finish before we try one ourselves.
        // Shorter than SETTLE_TIME, since a download already running in this process can't be
        // started twice anyway
        private val DOWNLOAD_SETTLE_TIME = TimeUnit.MINUTES.toMillis(1)

        // How long to wait before each retry of a failed download, so a message that's gone for
        // good (expired at the carrier) isn't retried forever. Its length is the attempt limit
        private val DOWNLOAD_RETRY_BACKOFF = listOf(0L, 5L, 15L, 60L, 180L, 360L)
            .map(TimeUnit.MINUTES::toMillis)

        private const val DOWNLOAD_RETRIES_PREFS = "mms_download_retries"

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

        /**
         * Checks in a couple of minutes, after a download has failed. A brief signal drop is the
         * usual cause, so it's worth trying again before the next hourly run
         */
        fun runSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORKER_TAG-soon",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MissedMessageWorker>()
                    .setInitialDelay(2, TimeUnit.MINUTES)
                    .addTag(WORKER_TAG)
                    .build()
            )
        }
    }

    @Inject lateinit var syncRepo: SyncRepository
    @Inject lateinit var conversationRepo: ConversationRepository

    override fun doWork(): Result {
        val now = System.currentTimeMillis()

        retryUndownloadedMms(now)

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

    /**
     * Downloads again any message whose notice is still waiting. A successful download deletes
     * the notice, so whatever is left never arrived, and retrying can't save a message twice. A
     * success is received the usual way, by ReceiveMmsWorker
     */
    private fun retryUndownloadedMms(now: Long) {
        val waiting = getWaitingMms(now - LOOKBACK, now - DOWNLOAD_SETTLE_TIME)

        // Attempts are counted per notice, as "attempts:lastAttemptMillis". Forget the ones that
        // are no longer waiting, whether they downloaded, were deleted or aged out
        val retries = applicationContext.getSharedPreferences(DOWNLOAD_RETRIES_PREFS, Context.MODE_PRIVATE)
        val waitingIds = waiting.map { it.id.toString() }.toSet()
        val stale = retries.all.keys.filterNot(waitingIds::contains)
        if (stale.isNotEmpty()) {
            val editor = retries.edit()
            stale.forEach { key -> editor.remove(key) }
            editor.apply()
        }

        if (waiting.isEmpty()) return

        // The downloader reads the carrier's HTTP settings from this
        MmsConfig.init(applicationContext)

        waiting.forEach { mms ->
            val (attempts, lastAttempt) = retries.getString(mms.id.toString(), null)
                ?.split(':')
                ?.mapNotNull(String::toLongOrNull)
                ?.takeIf { it.size == 2 }
                ?: listOf(0L, 0L)

            if (attempts >= DOWNLOAD_RETRY_BACKOFF.size) return@forEach
            if (now - lastAttempt < DOWNLOAD_RETRY_BACKOFF[attempts.toInt()]) return@forEach

            retries.edit().putString(mms.id.toString(), "${attempts + 1}:$now").apply()

            Timber.i("retrying download of mms ${mms.id}, attempt ${attempts + 1}")
            DownloadManager.getInstance().downloadMultimediaMessage(applicationContext, mms.location,
                ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mms.id), false, mms.subId)
        }
    }

    private data class WaitingMms(val id: Long, val location: String, val subId: Int)

    /**
     * Received notices for messages still waiting to be downloaded. The MMS store dates in seconds
     */
    private fun getWaitingMms(from: Long, to: Long): List<WaitingMms> {
        val projection = arrayOf(BaseColumns._ID, Telephony.Mms.CONTENT_LOCATION, Telephony.Mms.SUBSCRIPTION_ID)
        val selection = "${Telephony.Mms.MESSAGE_BOX} = ${Telephony.Mms.MESSAGE_BOX_INBOX} AND " +
                "${Telephony.Mms.MESSAGE_TYPE} = 130 AND " +
                "${Telephony.Mms.DATE} >= ? AND ${Telephony.Mms.DATE} < ?"
        val args = arrayOf((from / 1000).toString(), (to / 1000).toString())

        return tryOrNull(false) {
            applicationContext.contentResolver.query(Telephony.Mms.CONTENT_URI, projection, selection, args, null)
        }?.use { cursor ->
            generateSequence { cursor.takeIf { it.moveToNext() } }
                .mapNotNull { row ->
                    val location = row.getString(1)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    // An unset subscription reads as 0, which no real one is
                    val subId = row.getInt(2).takeIf { it > 0 } ?: Utils.getDefaultSubscriptionId()
                    WaitingMms(row.getLong(0), location, subId)
                }
                .toList()
        } ?: listOf()
    }

}
