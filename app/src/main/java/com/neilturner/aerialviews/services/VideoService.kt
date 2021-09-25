package com.neilturner.aerialviews.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.AppleVideoQuality
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.prefs.NetworkVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.Apple2019Video
import com.neilturner.aerialviews.providers.AppleVideoProvider
import com.neilturner.aerialviews.providers.LocalVideoProvider
import com.neilturner.aerialviews.providers.NetworkVideoProvider
import com.neilturner.aerialviews.providers.VideoProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.JsonHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoService(private val context: Context) {
    private val providers = mutableListOf<VideoProvider>()

    init {
        if (AppleVideoPrefs.enabled)
            providers.add(AppleVideoProvider(context, AppleVideoPrefs))

        if (LocalVideoPrefs.enabled)
            providers.add(LocalVideoProvider(context))

        if (NetworkVideoPrefs.enabled)
            providers.add(NetworkVideoProvider(context, NetworkVideoPrefs))
    }

    suspend fun fetchVideos(): VideoPlaylist = withContext(Dispatchers.IO) {
        var videos = mutableListOf<AerialVideo>()

        // Find all videos from all providers/sources
        providers.forEach {
            val newVideos = try {
                it.fetchVideos()
            } catch(ex: Exception) {
                Log.e(TAG, ex.message!!)
                emptyList()
            }
            videos.addAll(newVideos)
        }

        if (GeneralPrefs.removeDuplicates) {
            var numVideos = videos.size
            // Remove duplicates based on full path
            videos = videos.distinctBy { it.uri.toString().lowercase() } as MutableList<AerialVideo>
            Log.i(TAG, "Videos removed based on full path: ${numVideos - videos.size}")

            numVideos = videos.size
            // Remove duplicates based on filename only
            videos = videos.distinctBy { it.uri.lastPathSegment?.lowercase() } as MutableList<AerialVideo>
            Log.i(TAG, "Videos removed based on filename: ${numVideos - videos.size}")
        }

        // Try and add locations by looking up video filenames in various manifests
        val manifestVideos = mutableListOf<AerialVideo>()
        if (GeneralPrefs.useAppleManifests)
            manifestVideos.addAll(appleManifestVideos())

        if (GeneralPrefs.useCustomManifests)
            manifestVideos.addAll(customManifestVideos())

        val result = findVideoLocation(videos, manifestVideos)
        videos = result.first.toMutableList()

        if (result.first.isNotEmpty())
            Log.i(TAG, "Found ${result.first.count()} manifest videos")

        if (result.second.isNotEmpty())
            Log.i(TAG, "Found ${result.second.count()} non-manifest videos")

        if (!GeneralPrefs.ignoreNonManifestVideos) {
            videos.addAll(result.second)
        }

        // If there are videos with no location yet, use filename as location
        if (!GeneralPrefs.ignoreNonManifestVideos && GeneralPrefs.filenameAsLocation) {
            videos.forEach { video ->
                if (video.location.isBlank()) {
                    val location = FileHelper.filenameToTitleCase(video.uri)
                    video.location = location
                }
            }
        }

        if (videos.isEmpty()) {
            Log.i(TAG, "No videos, adding empty one")
            videos.add(AerialVideo(Uri.parse(""), ""))
        }

        if (GeneralPrefs.shuffleVideos)
            videos.shuffle()

        Log.i(TAG, "Total vids: ${videos.size}")
        VideoPlaylist(videos)
    }

    private fun appleManifestVideos(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()

        var wrapper = JsonHelper.parseJson(context, R.raw.tvos13, JsonHelper.Wrapper::class.java)
        wrapper.assets?.forEach {
            val allQualities = allVideoQualities(it)
            videos.addAll(allQualities)
        }

        wrapper = JsonHelper.parseJson(context, R.raw.tvos15, JsonHelper.Wrapper::class.java)
        wrapper.assets?.forEach {
            val allQualities = allVideoQualities(it)
            videos.addAll(allQualities)
        }

        return videos
    }

    private fun customManifestVideos(): List<AerialVideo> {

        return emptyList()
    }

    private fun allVideoQualities(video: Apple2019Video): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()
        videos.add(AerialVideo(video.uri(AppleVideoQuality.VIDEO_1080_H264), video.location))
        videos.add(AerialVideo(video.uri(AppleVideoQuality.VIDEO_1080_SDR), video.location))
        videos.add(AerialVideo(video.uri(AppleVideoQuality.VIDEO_1080_HDR), video.location))
        videos.add(AerialVideo(video.uri(AppleVideoQuality.VIDEO_4K_SDR), video.location))
        videos.add(AerialVideo(video.uri(AppleVideoQuality.VIDEO_4K_HDR), video.location))
        return videos
    }

    private fun findVideoLocation(foundVideos: List<AerialVideo>, manifestVideos: List<AerialVideo>) : Pair<List<AerialVideo>,List<AerialVideo>> {
        val matched = mutableListOf<AerialVideo>()
        val unmatched = mutableListOf<AerialVideo>()

        for (video in foundVideos) {
            if (!FileHelper.isLocalVideo(video.uri)) {
                Log.i(TAG, "HTTP/web video, ignoring")
                matched.add(video)
                continue
            }

            val filename = video.uri.lastPathSegment!!.lowercase()
            val videoFound = manifestVideos.find {
                val manifestFilename = it.uri.lastPathSegment!!.lowercase()
                manifestFilename.contains(filename)
            }

            if (videoFound != null) {
                matched.add(AerialVideo(video.uri, videoFound.location))
            } else {
                unmatched.add(video)
            }
        }
        return Pair(matched, unmatched)
    }

    companion object {
        private const val TAG = "VideoService"
    }
}