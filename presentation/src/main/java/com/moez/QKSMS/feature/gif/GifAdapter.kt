/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.feature.gif

import android.view.LayoutInflater
import android.view.ViewGroup
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.databinding.GifListItemBinding
import dev.octoshrimpy.quik.util.GlideApp
import dev.octoshrimpy.quik.model.GifResult
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

/**
 * Backchannel: the GIF search grid. Previews are loaded straight from the provider's CDN by Glide,
 * which decodes them as GifDrawables, so the grid animates.
 */
class GifAdapter @Inject constructor() : QkAdapter<GifResult, QkBindingViewHolder<GifListItemBinding>>() {

    val gifSelected: Subject<GifResult> = PublishSubject.create()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<GifListItemBinding> {
        val binding = GifListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return QkBindingViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val gif = data.getOrNull(adapterPosition) ?: return@setOnClickListener
                gifSelected.onNext(gif)
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<GifListItemBinding>, position: Int) {
        val gif = getItem(position)

        // Square cells: a GIF grid that reflows as each image lands is miserable to tap
        GlideApp.with(holder.binding.thumbnail)
            .load(gif.previewUrl)
            .centerCrop()
            .into(holder.binding.thumbnail)
    }

    override fun areItemsTheSame(old: GifResult, new: GifResult): Boolean = old.id == new.id

    override fun areContentsTheSame(old: GifResult, new: GifResult): Boolean = old == new

}
