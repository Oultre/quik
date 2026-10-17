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
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.content.getSystemService
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.interactor.MarkRead
import timber.log.Timber
import javax.inject.Inject

/**
 * Backchannel: the "Copy code" notification action. Copies a verification code to the clipboard,
 * then marks the conversation read, which also clears the notification
 */
class CopyCodeReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_THREAD_ID = "threadId"
    }

    @Inject lateinit var markRead: MarkRead

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        val code = intent.getStringExtra(EXTRA_CODE)?.takeIf { it.isNotEmpty() }
            ?: return Timber.e("didn't get a code")

        val clip = ClipData.newPlainText(context.getString(R.string.verification_code_clip_label), code)

        // Keeps the code out of the clipboard preview and keyboard suggestions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }

        context.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)

        // Android 13+ confirms clipboard writes itself, so only older versions need a toast
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.verification_code_copied, Toast.LENGTH_SHORT).show()
        }

        intent.getLongExtra(EXTRA_THREAD_ID, 0).takeIf { it > 0 }?.let { threadId ->
            val pendingResult = goAsync()
            markRead.execute(listOf(threadId)) { pendingResult.finish() }
        }
    }

}
