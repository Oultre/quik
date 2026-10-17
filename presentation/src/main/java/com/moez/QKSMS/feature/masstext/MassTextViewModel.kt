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
package dev.octoshrimpy.quik.feature.masstext

import android.content.Context
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.repository.ContactRepository
import dev.octoshrimpy.quik.worker.MassTextWorker
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class MassTextViewModel @Inject constructor(
    private val context: Context,
    private val contactRepo: ContactRepository
) : QkViewModel<MassTextView, MassTextState>(MassTextState()) {

    override fun bindView(view: MassTextView) {
        super.bindView(view)

        // The picker leaves out whoever's already on the list
        view.addRecipientsIntent
            .withLatestFrom(state) { _, state -> state }
            .autoDisposable(view.scope())
            .subscribe { state ->
                view.showContactPicker(HashMap(state.recipients.associate { it.address to it.lookupKey }))
            }

        view.recipientsPickedIntent
            .observeOn(Schedulers.io())
            .map { picked ->
                picked.map { (address, lookupKey) ->
                    val name = lookupKey?.let(contactRepo::getUnmanagedContact)?.name?.takeIf { it.isNotBlank() }
                    MassTextRecipient(address, lookupKey, name)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(view.scope())
            .subscribe { added ->
                newState { copy(recipients = (recipients + added).distinctBy { it.address }) }
            }

        view.removeRecipientIntent
            .autoDisposable(view.scope())
            .subscribe { removed -> newState { copy(recipients = recipients - removed) } }

        view.bodyChangedIntent
            .autoDisposable(view.scope())
            .subscribe { body -> newState { copy(body = body.toString()) } }

        view.insertFirstNameIntent
            .autoDisposable(view.scope())
            .subscribe { view.insertFirstName() }

        // Ask first: a mass text can't be taken back
        view.sendIntent
            .withLatestFrom(state) { _, state -> state }
            .filter { state -> state.recipients.isNotEmpty() && state.body.isNotBlank() }
            .autoDisposable(view.scope())
            .subscribe { state -> view.showConfirmSend(state.recipients.size) }

        // -1 sends from the default SIM, the same as composing without picking one
        view.confirmSendIntent
            .withLatestFrom(state) { _, state -> state }
            .autoDisposable(view.scope())
            .subscribe { state ->
                MassTextWorker.enqueue(context, state.recipients.map { it.address to it.name }, state.body.trim(), -1)
                view.finishSending(state.recipients.size)
            }
    }

}
