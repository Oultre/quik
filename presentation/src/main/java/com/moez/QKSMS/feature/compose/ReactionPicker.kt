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
package dev.octoshrimpy.quik.feature.compose

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.octoshrimpy.quik.R

/**
 * Backchannel: every emoji, by category, with recents at the top. Holding one offers its skin tones
 * and other variants. The list comes from the emoji2-emojipicker library, but it's drawn in plain
 * TextViews because that library's own view showed its headings with no emojis.
 */
class ReactionPicker(
    context: Context,
    private val onPicked: (String) -> Unit
) {

    private companion object {
        const val COLUMNS = 8
        const val RECENTS_PREFS = "reaction_recents"
        const val RECENTS_KEY = "recents"
        const val RECENTS_LIMIT = 16
    }

    private class Item(val title: String?, val variants: List<String>)

    private val builder = AlertDialog.Builder(context)
    private val context = builder.context
    private val recents = context.getSharedPreferences(RECENTS_PREFS, Context.MODE_PRIVATE)
    private val dialogs = mutableListOf<AlertDialog>()

    fun show() {
        val items = mutableListOf<Item>()

        val recent = recents.getString(RECENTS_KEY, null)?.split('\n').orEmpty().filter(String::isNotEmpty)
        if (recent.isNotEmpty()) {
            items += Item(context.getString(R.string.emoji_category_recent), listOf())
            items += recent.map { emoji -> Item(null, listOf(emoji)) }
        }

        // Each tab jumps to the start of its category
        val tabs = listOf(
            Triple("😀", R.string.emoji_category_emotions, R.raw.emoji_category_emotions),
            Triple("🙋", R.string.emoji_category_people, R.raw.emoji_category_people_gender_inclusive),
            Triple("🐶", R.string.emoji_category_animals_nature, R.raw.emoji_category_animals_nature),
            Triple("🍔", R.string.emoji_category_food_drink, R.raw.emoji_category_food_drink),
            Triple("✈️", R.string.emoji_category_travel_places, R.raw.emoji_category_travel_places),
            Triple("⚽", R.string.emoji_category_activity, R.raw.emoji_category_activity),
            Triple("💡", R.string.emoji_category_objects, R.raw.emoji_category_objects),
            Triple("❤️", R.string.emoji_category_symbols, R.raw.emoji_category_symbols),
            Triple("🏁", R.string.emoji_category_flags, R.raw.emoji_category_flags)
        ).map { (icon, title, raw) ->
            val start = items.size
            items += Item(context.getString(title), listOf())
            items += loadCategory(raw)
            icon to start
        }

        val height = (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
        val grid = createGrid(items, height)
        val layoutManager = grid.layoutManager as GridLayoutManager

        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            tabs.forEach { (icon, start) ->
                val tab = createEmojiView(19f).apply {
                    text = icon
                    setOnClickListener { layoutManager.scrollToPositionWithOffset(start, 0) }
                }
                addView(tab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabRow)
            addView(grid)
        }

        showDialog(builder.setTitle(R.string.compose_react_title), content)
    }

    private fun showVariants(item: Item) {
        val grid = createGrid(item.variants.map { emoji -> Item(null, listOf(emoji)) }, ViewGroup.LayoutParams.WRAP_CONTENT)
        showDialog(AlertDialog.Builder(context), grid)
    }

    private fun showDialog(builder: AlertDialog.Builder, content: View) {
        val dialog = builder
            .setView(content)
            .setNegativeButton(R.string.button_cancel, null)
            .setOnDismissListener { dialog -> dialogs.remove(dialog) }
            .create()
        dialogs += dialog
        dialog.show()
    }

    private fun pick(emoji: String) {
        val updated = (listOf(emoji) + recents.getString(RECENTS_KEY, null)?.split('\n').orEmpty())
            .filter(String::isNotEmpty)
            .distinct()
            .take(RECENTS_LIMIT)
        recents.edit().putString(RECENTS_KEY, updated.joinToString("\n")).apply()

        dialogs.toList().forEach(AlertDialog::dismiss)
        onPicked(emoji)
    }

    /**
     * One line per emoji, comma-separated, with the plain emoji first and then its variants
     */
    private fun loadCategory(raw: Int): List<Item> {
        return context.resources.openRawResource(raw).bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank)
                .map { line -> Item(null, line.trim().split(',').distinct()) }
                .toList()
        }
    }

    private fun createGrid(items: List<Item>, height: Int): RecyclerView {
        return RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            layoutManager = GridLayoutManager(context, COLUMNS).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (items[position].title != null) COLUMNS else 1
                    }
                }
            }
            adapter = EmojiAdapter(items)
        }
    }

    private fun createEmojiView(size: Float): TextView {
        return TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            gravity = Gravity.CENTER
            maxLines = 1
            val padding = dp(8)
            setPadding(0, padding, 0, padding)
            val background = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)
            setBackgroundResource(background.resourceId)
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private inner class EmojiAdapter(private val items: List<Item>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int) = if (items[position].title != null) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = when (viewType) {
                0 -> TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(12), dp(12), dp(12), dp(4))
                }
                else -> createEmojiView(26f)
            }
            view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val view = holder.itemView as TextView

            if (item.title != null) {
                view.text = item.title
                return
            }

            val emoji = item.variants.first()
            view.text = emoji
            view.setOnClickListener { pick(emoji) }
            view.setOnLongClickListener {
                if (item.variants.size > 1) showVariants(item)
                item.variants.size > 1
            }
        }
    }
}
