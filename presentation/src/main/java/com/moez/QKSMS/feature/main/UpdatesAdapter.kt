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
package dev.octoshrimpy.quik.feature.main

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.common.base.QkRealmAdapter
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.DateFormatter
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.databinding.UpdatesListItemBinding
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

/**
 * Backchannel: renders the "Updates" box as one merged feed. Every message from every bundled
 * conversation, newest first, labelled with the sender it came from -- so promo/delivery texts read
 * as a single stream instead of a wall of separate threads.
 *
 * Tapping a message opens the sender's real conversation; long-pressing offers to move that sender
 * back to the inbox (the feed has no multi-select, so unbundling lives here rather than on a
 * selection toolbar).
 */
class UpdatesAdapter @Inject constructor(
    private val colors: Colors,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator
) : QkRealmAdapter<Message, QkBindingViewHolder<UpdatesListItemBinding>>() {

    /**
     * Emits the thread id of a long-pressed message, so the view can offer to unbundle its sender
     */
    val unbundleRequests: Subject<Long> = PublishSubject.create()

    /**
     * Messages in the feed hop between senders constantly, so the sender of each row is resolved
     * from a map built once per data set rather than queried on every bind. Querying during a bind
     * is what we're avoiding here: the repository refreshes the Realm on read, which can fire
     * change listeners in the middle of a RecyclerView layout pass.
     */
    private var conversations = mapOf<Long, Conversation>()

    init {
        setHasStableIds(true)
    }

    override fun updateData(data: OrderedRealmCollection<Message>?) {
        // render() runs often; only re-resolve the senders when the feed itself changes
        if (getData() !== data) {
            conversations = Realm.getDefaultInstance()
                .where(Conversation::class.java)
                .equalTo("bundled", true)
                .findAll()
                .associateBy { it.id }
        }

        super.updateData(data)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): QkBindingViewHolder<UpdatesListItemBinding> {
        val binding = UpdatesListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        // viewType 1 == unread, styled bold to match the conversation list
        if (viewType == 1) {
            val textColorPrimary = parent.context.resolveThemeColor(android.R.attr.textColorPrimary)

            binding.title.setTypeface(binding.title.typeface, Typeface.BOLD)
            binding.body.setTypeface(binding.body.typeface, Typeface.BOLD)
            binding.body.setTextColor(textColorPrimary)
            binding.date.setTypeface(binding.date.typeface, Typeface.BOLD)
            binding.date.setTextColor(textColorPrimary)
            binding.unread.isVisible = true
        }

        return QkBindingViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val message = getItem(adapterPosition) ?: return@setOnClickListener
                navigator.showConversation(message.threadId)
            }
            binding.root.setOnLongClickListener {
                val message = getItem(adapterPosition) ?: return@setOnLongClickListener true
                unbundleRequests.onNext(message.threadId)
                true
            }
        }
    }

    override fun onBindViewHolder(
        holder: QkBindingViewHolder<UpdatesListItemBinding>,
        position: Int
    ) {
        val message = getItem(position) ?: return
        val binding = holder.binding
        val conversation = conversations[message.threadId]

        binding.avatars.recipients = conversation?.recipients ?: emptyList()
        binding.title.text = conversation?.getTitle()?.takeIf { it.isNotBlank() } ?: message.address
        binding.date.text = message.date.takeIf { it > 0 }?.let(dateFormatter::getConversationTimestamp)
        binding.body.text = message.getSummary()
        binding.unread.setTint(colors.theme(conversation?.recipients?.firstOrNull()).theme)
    }

    /**
     * Display name for a thread in the feed, for the "move back to inbox" prompt
     */
    fun getSenderName(threadId: Long): String =
        conversations[threadId]?.getTitle()?.takeIf { it.isNotBlank() } ?: threadId.toString()

    override fun getItemId(position: Int): Long = getItem(position)?.id ?: -1

    override fun getItemViewType(position: Int): Int =
        if (getItem(position)?.read == false) 1 else 0

}
