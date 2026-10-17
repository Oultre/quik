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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import dev.octoshrimpy.quik.databinding.MassTextActivityBinding
import dev.octoshrimpy.quik.feature.contacts.ContactsActivity
import dev.octoshrimpy.quik.util.MassTextTemplate
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

/**
 * Backchannel: one message, sent to a list of people as separate one-on-one texts, with an
 * optional first name filled in for each
 */
class MassTextActivity : QkThemedActivity(), MassTextView {

    companion object {
        private const val PICK_CONTACTS_REQUEST_CODE = 1
    }

    @Inject lateinit var recipientAdapter: MassTextRecipientAdapter
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: MassTextActivityBinding

    override val addRecipientsIntent by lazy { binding.addRecipients.clicks() }
    override val recipientsPickedIntent: Subject<HashMap<String, String?>> = PublishSubject.create()
    override val removeRecipientIntent by lazy { recipientAdapter.removeClicks }
    override val bodyChangedIntent by lazy { binding.body.textChanges() }
    override val insertFirstNameIntent by lazy { binding.insertFirstName.clicks() }
    override val sendIntent: Subject<Unit> = PublishSubject.create()
    override val confirmSendIntent: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory)[MassTextViewModel::class.java]
    }

    private var canSend = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = MassTextActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(R.string.title_mass_text)
        showBackButton(true)

        binding.recipients.adapter = recipientAdapter

        viewModel.bindView(this)
    }

    override fun render(state: MassTextState) {
        val count = state.recipients.size
        binding.recipientsTitle.text = when (count) {
            0 -> getString(R.string.mass_text_recipients_title)
            else -> resources.getQuantityString(R.plurals.mass_text_recipients, count, count)
        }
        binding.recipientsEmpty.isVisible = count == 0
        recipientAdapter.data = state.recipients

        // Shows what the first person will get, once there's a name to fill in
        val example = state.recipients.firstOrNull()?.takeIf { MassTextTemplate.FIRST_NAME in state.body }
        binding.preview.isVisible = example != null
        if (example != null) {
            binding.preview.text = getString(R.string.mass_text_preview,
                example.name ?: example.address, MassTextTemplate.fill(state.body, example.name))
        }

        val sendable = count > 0 && state.body.isNotBlank()
        if (sendable != canSend) {
            canSend = sendable
            invalidateOptionsMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.mass_text, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.send)?.let { send ->
            send.isEnabled = canSend
            send.icon?.alpha = if (canSend) 255 else 97
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.send -> {
            sendIntent.onNext(Unit)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun showContactPicker(selected: HashMap<String, String?>) {
        val intent = Intent(this, ContactsActivity::class.java)
            .putExtra(ContactsActivity.CHIPS_KEY, selected)
            .putExtra(ContactsActivity.RETURN_ON_BACK_KEY, true)
        startActivityForResult(intent, PICK_CONTACTS_REQUEST_CODE)
    }

    override fun insertFirstName() {
        val start = binding.body.selectionStart.coerceAtLeast(0)
        binding.body.text?.insert(start, MassTextTemplate.FIRST_NAME)
    }

    override fun showConfirmSend(recipientCount: Int) {
        AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.mass_text_confirm_title, recipientCount, recipientCount))
            .setMessage(R.string.mass_text_confirm_message)
            .setPositiveButton(R.string.mass_text_send) { _, _ -> confirmSendIntent.onNext(Unit) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun finishSending(recipientCount: Int) {
        Toast.makeText(this, resources.getQuantityString(R.plurals.mass_text_sending, recipientCount, recipientCount),
            Toast.LENGTH_LONG).show()
        finish()
    }

    @Suppress("UNCHECKED_CAST")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACTS_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            (data?.getSerializableExtra(ContactsActivity.CHIPS_KEY) as? HashMap<String, String?>)
                ?.let(recipientsPickedIntent::onNext)
        }
    }

}
