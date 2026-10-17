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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.databinding.MassTextRecipientItemBinding
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class MassTextRecipientAdapter @Inject constructor()
    : QkAdapter<MassTextRecipient, QkBindingViewHolder<MassTextRecipientItemBinding>>() {

    val removeClicks: Subject<MassTextRecipient> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<MassTextRecipientItemBinding> {
        val binding = MassTextRecipientItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return QkBindingViewHolder(binding).apply {
            binding.remove.setOnClickListener {
                val recipient = data.getOrNull(adapterPosition) ?: return@setOnClickListener
                removeClicks.onNext(recipient)
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<MassTextRecipientItemBinding>, position: Int) {
        val recipient = getItem(position)

        // A bare number shows once, as the name
        holder.binding.name.text = recipient.name ?: recipient.address
        holder.binding.address.text = recipient.address
        holder.binding.address.isVisible = recipient.name != null
    }

    override fun areItemsTheSame(old: MassTextRecipient, new: MassTextRecipient): Boolean =
        old.address == new.address

}
