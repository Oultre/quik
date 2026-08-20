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
package dev.octoshrimpy.quik.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dev.octoshrimpy.quik.data.BuildConfig
import dev.octoshrimpy.quik.model.GifResult
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backchannel: Giphy-backed [GifRepository].
 *
 * Uses HttpURLConnection rather than pulling in an HTTP client: the only network calls are two
 * JSON GETs and a file download, and Glide already fetches the thumbnails itself. Giphy was chosen
 * over Tenor because Tenor's v2 API requires a Google Cloud project, which is an odd dependency
 * for a phone that is deliberately de-Googled. Swapping providers means rewriting this class only.
 */
@Singleton
class GifRepositoryImpl @Inject constructor(
    private val context: Context
) : GifRepository {

    companion object {
        private const val ENDPOINT = "https://api.giphy.com/v1/gifs"
        private const val PAGE_SIZE = 24
        private const val TIMEOUT_MS = 15000

        /**
         * Renditions worth sending, roughly largest first. Giphy reports a byte size for each, so
         * the first one that fits the MMS budget wins. The "downsampled" ones drop frames to
         * shrink, and preview_gif is the last resort.
         */
        private val SEND_RENDITIONS = listOf(
            "downsized",
            "fixed_height",
            "fixed_height_downsampled",
            "fixed_width_downsampled",
            "fixed_height_small",
            "fixed_width_small",
            "preview_gif"
        )

        /** Small, animated, cheap to grid up */
        private val PREVIEW_RENDITIONS = listOf(
            "fixed_width_downsampled",
            "fixed_height_downsampled",
            "preview_gif",
            "fixed_width_small"
        )
    }

    private val apiKey: String get() = BuildConfig.GIPHY_API_KEY

    override fun isConfigured(): Boolean = apiKey.isNotBlank()

    override fun trending(offset: Int, maxSendBytes: Long): List<GifResult> =
        fetch(
            ENDPOINT + "/trending?api_key=" + apiKey + "&limit=" + PAGE_SIZE +
                    "&offset=" + offset + "&rating=pg-13",
            maxSendBytes
        )

    override fun search(query: String, offset: Int, maxSendBytes: Long): List<GifResult> {
        if (query.isBlank()) return trending(offset, maxSendBytes)

        val encoded = URLEncoder.encode(query, "UTF-8")

        return fetch(
            ENDPOINT + "/search?api_key=" + apiKey + "&q=" + encoded + "&limit=" + PAGE_SIZE +
                    "&offset=" + offset + "&rating=pg-13",
            maxSendBytes
        )
    }

    private fun fetch(url: String, maxSendBytes: Long): List<GifResult> {
        if (!isConfigured()) return emptyList()

        val body = get(url) ?: return emptyList()

        return try {
            val data = JSONObject(body).optJSONArray("data") ?: return emptyList()

            (0 until data.length()).mapNotNull { i ->
                data.optJSONObject(i)?.let { toGifResult(it, maxSendBytes) }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse GIF search response")
            emptyList()
        }
    }

    private fun toGifResult(gif: JSONObject, maxSendBytes: Long): GifResult? {
        val images = gif.optJSONObject("images") ?: return null
        val id = gif.optString("id").takeIf { it.isNotBlank() } ?: return null

        val preview = PREVIEW_RENDITIONS
            .asSequence()
            .mapNotNull { images.optJSONObject(it) }
            .mapNotNull { rendition -> rendition.optString("url").takeIf { it.isNotBlank() } }
            .firstOrNull() ?: return null

        // Largest rendition that still fits the budget. If none of them report a usable size, fall
        // back to the preview rather than dropping the result -- a small GIF beats no GIF
        val send = SEND_RENDITIONS
            .asSequence()
            .mapNotNull { images.optJSONObject(it) }
            .mapNotNull { rendition ->
                val renditionUrl = rendition.optString("url").takeIf { it.isNotBlank() }
                val size = rendition.optString("size").toLongOrNull()

                if (renditionUrl == null || size == null) null
                else Triple(renditionUrl, size, rendition)
            }
            .firstOrNull { it.second in 1..maxSendBytes }

        return GifResult(
            id = id,
            previewUrl = preview,
            sendUrl = send?.first ?: preview,
            sendSizeBytes = send?.second ?: 0L,
            width = send?.third?.optString("width")?.toIntOrNull() ?: 0,
            height = send?.third?.optString("height")?.toIntOrNull() ?: 0
        )
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null

        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }

            when (connection.responseCode) {
                in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                else -> {
                    Timber.w("GIF request failed: HTTP " + connection.responseCode)
                    null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "GIF request failed")
            null
        } finally {
            connection?.disconnect()
        }
    }

    override fun download(gif: GifResult): Uri {
        val dir = File(context.cacheDir, "gifs").apply { mkdirs() }
        val file = File(dir, gif.id + ".gif")

        if (!file.exists() || file.length() == 0L) {
            var connection: HttpURLConnection? = null

            try {
                connection = (URL(gif.sendUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                }

                connection.inputStream.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            } finally {
                connection?.disconnect()
            }
        }

        // Reuses the app's existing cache-wide FileProvider. A file:// Uri would trip StrictMode's
        // FileUriExposedException as soon as it was handed back through setResult()
        return FileProvider.getUriForFile(context, context.packageName + ".messagesText", file)
    }

}
