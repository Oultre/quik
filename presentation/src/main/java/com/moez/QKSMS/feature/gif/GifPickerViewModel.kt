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

import android.net.Uri
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.model.GifResult
import dev.octoshrimpy.quik.repository.GifRepository
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GifPickerViewModel @Inject constructor(
    private val gifRepo: GifRepository,
    private val prefs: Preferences
) : QkViewModel<GifPickerView, GifPickerState>(
    GifPickerState(unconfigured = !gifRepo.isConfigured(), loading = gifRepo.isConfigured())
) {

    /** Last query run, so the retry button and the initial load share one path */
    private val query: BehaviorSubject<String> = BehaviorSubject.createDefault("")

    /**
     * How many bytes a GIF may be to stand a chance of actually sending. Mirrors the attachment
     * pipeline's budget (prefs.mmsSize is in KB; 0 means unlimited), with headroom for the message
     * body and MMS overhead. Anything larger gets re-encoded down to mush, so it is not worth
     * fetching in the first place.
     */
    private val maxSendBytes: Long
        get() = when (val sizeKb = prefs.mmsSize.get()) {
            0 -> Long.MAX_VALUE
            -1 -> 300L * 1024 // carrier-configured; 300KB is the safe assumption
            else -> (sizeKb * 1024 * 0.8).toLong()
        }

    override fun bindView(view: GifPickerView) {
        super.bindView(view)

        if (!gifRepo.isConfigured()) return

        // Search as they type. Trending shows first, since query starts empty and the repository
        // treats a blank query as a trending request.
        view.queryChangedIntent
            .map { it.toString().trim() }
            .distinctUntilChanged()
            .debounce(350, TimeUnit.MILLISECONDS)
            .autoDisposable(view.scope())
            .subscribe(query::onNext)

        query
            .doOnNext { newState { copy(loading = true, error = false) } }
            .observeOn(Schedulers.io())
            .map { q ->
                try {
                    Loaded(gifRepo.search(q, 0, maxSendBytes), failed = false)
                } catch (e: Exception) {
                    Timber.w(e, "GIF search failed")
                    Loaded(emptyList(), failed = true)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(view.scope())
            .subscribe { loaded ->
                newState {
                    copy(loading = false, error = loaded.failed, results = loaded.results)
                }
            }

        view.retryIntent
            .autoDisposable(view.scope())
            .subscribe { query.onNext(query.value ?: "") }

        // Download the chosen GIF, then hand its Uri back to compose
        view.gifSelectedIntent
            .doOnNext { newState { copy(downloading = true) } }
            .observeOn(Schedulers.io())
            .map { gif: GifResult ->
                try {
                    Downloaded(gifRepo.download(gif))
                } catch (e: Exception) {
                    Timber.w(e, "GIF download failed")
                    Downloaded(null)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(view.scope())
            .subscribe { downloaded ->
                newState { copy(downloading = false, error = downloaded.uri == null) }
                downloaded.uri?.let(view::setResultAndFinish)
            }
    }

    // RxJava2 will not carry nulls, so results and failures travel together in a wrapper
    private data class Loaded(val results: List<GifResult>, val failed: Boolean)

    private data class Downloaded(val uri: Uri?)

}
