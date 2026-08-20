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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkThemedActivity
import dev.octoshrimpy.quik.databinding.GifPickerActivityBinding
import javax.inject.Inject

/**
 * Backchannel: full-screen GIF search, launched for result from the compose attach shade. Returns
 * the Uri of a downloaded GIF, which compose then attaches through the ordinary file path.
 */
class GifPickerActivity : QkThemedActivity(), GifPickerView {

    @Inject lateinit var gifAdapter: GifAdapter
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var binding: GifPickerActivityBinding

    override val queryChangedIntent by lazy { binding.search.textChanges() }
    override val gifSelectedIntent by lazy { gifAdapter.gifSelected }
    override val retryIntent by lazy { binding.retry.clicks() }

    private val viewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory)[GifPickerViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = GifPickerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // QkActivity.setContentView already installs the toolbar as the action bar. The search
        // field fills it, so there is no title to set -- the hint says what this screen is.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerView.adapter = gifAdapter

        viewModel.bindView(this)
    }

    override fun render(state: GifPickerState) {
        binding.loading.isVisible = state.loading || state.downloading
        binding.recyclerView.isVisible = state.results.isNotEmpty() && !state.downloading

        gifAdapter.data = state.results

        val message = when {
            state.unconfigured -> getString(R.string.gif_picker_no_key)
            state.error -> getString(R.string.gif_picker_error)
            !state.loading && !state.downloading && state.results.isEmpty() ->
                getString(R.string.gif_picker_empty)
            else -> null
        }

        binding.empty.text = message
        binding.empty.isVisible = message != null
        binding.retry.isVisible = state.error && !state.unconfigured

        // Searching is pointless without a key
        binding.search.isEnabled = !state.unconfigured
    }

    override fun setResultAndFinish(uri: Uri) {
        setResult(Activity.RESULT_OK, Intent().setData(uri))
        finish()
    }

}
