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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.octoshrimpy.quik.manager.NotificationManager
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.util.MassTextTemplate
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Backchannel: sends a mass text, as a separate one-on-one message to each recipient. Runs in the
 * background so leaving the screen doesn't cut it short.
 *
 * The job lives in a file rather than the work's input data, which is capped at 10KB, and the file
 * records how far the run got. If the system stops the worker partway, the retry carries on from
 * there instead of texting everyone again.
 */
class MassTextWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    companion object {
        private const val INPUT_JOB_FILE = "jobFile"

        // Carriers treat a burst of texts from one number as spam
        private val PACE = TimeUnit.SECONDS.toMillis(2)

        /**
         * [recipients] are addresses paired with the contact's display name, if there is one
         */
        fun enqueue(context: Context, recipients: List<Pair<String, String?>>, body: String, subId: Int) {
            val job = JSONObject()
                .put("subId", subId)
                .put("body", body)
                .put("next", 0)
                .put("recipients", JSONArray(recipients.map { (address, name) ->
                    JSONObject().put("address", address).put("name", name.orEmpty())
                }))

            val dir = File(context.filesDir, "mass-text").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.json").apply { writeText(job.toString()) }

            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<MassTextWorker>()
                    .setInputData(workDataOf(INPUT_JOB_FILE to file.absolutePath))
                    .build()
            )
        }
    }

    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var messageRepo: MessageRepository
    @Inject lateinit var notificationManager: NotificationManager

    override fun doWork(): Result {
        val file = inputData.getString(INPUT_JOB_FILE)?.let(::File)?.takeIf(File::exists)
            ?: return Result.failure()

        val job = JSONObject(file.readText())
        val recipients = job.getJSONArray("recipients")
        val template = job.getString("body")
        val subId = job.getInt("subId")

        for (index in job.getInt("next") until recipients.length()) {
            if (isStopped) return Result.retry()

            val recipient = recipients.getJSONObject(index)
            val address = recipient.getString("address")
            val body = MassTextTemplate.fill(template, recipient.getString("name").ifEmpty { null })

            // One bad address shouldn't stop the rest. A send that fails later, at the carrier,
            // gets the usual failed-message notification
            try {
                send(subId, address, body)
            } catch (e: Exception) {
                Timber.e(e, "mass text to one recipient failed")
            }

            // Saved after sending: if we're killed in between, one person may get it twice on the
            // retry, which beats silently skipping them
            file.writeText(job.put("next", index + 1).toString())

            if (index < recipients.length() - 1) {
                Thread.sleep(PACE)
            }
        }

        file.delete()
        notificationManager.notifyMassTextSent(recipients.length())
        return Result.success()
    }

    private fun send(subId: Int, address: String, body: String) {
        val conversation = conversationRepo.getOrCreateConversation(listOf(address), false) ?: return

        val threadIds = messageRepo
            .sendNewMessages(subId, conversation.recipients.map { it.address }, body, listOf(), false)
            .map { message -> message.threadId }

        conversationRepo.updateConversations(threadIds)
        conversationRepo.markUnarchived(threadIds)
    }

}
